package com.example.valetkey.service;

import com.example.valetkey.model.Folder;
import com.example.valetkey.model.Resource;
import com.example.valetkey.model.User;
import com.example.valetkey.repository.FolderRepository;
import com.example.valetkey.repository.ResourceRepository;
import com.example.valetkey.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.CompletableFuture;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AWSS3Service awsS3Service;

    @Autowired
    private SQSService sqsService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private CloudWatchMetricsService cloudWatchMetricsService;

    @Autowired
    private StorageQuotaService storageQuotaService;

    public Map<String, Object> generateUploadUrl(String fileName, Long fileSize, Long folderId, User user) {
        if (!user.isCreate() || !user.isWrite()) {
            throw new RuntimeException("User does not have permission to upload files");
        }

        if (fileSize == null || fileSize <= 0) {
            throw new RuntimeException("Invalid file size");
        }

        if (!storageQuotaService.hasStorageSpace(user, fileSize)) {
            Long remaining = storageQuotaService.getRemainingStorage(user);
            throw new RuntimeException("Storage quota exceeded. Available: " + 
                storageQuotaService.formatBytes(remaining) + ", Required: " + 
                storageQuotaService.formatBytes(fileSize));
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "unnamed_" + System.currentTimeMillis();
        }

        String uniqueFileName = generateUniqueFileName(fileName);
        String objectKey = "user-" + user.getId() + "/" + uniqueFileName;

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerAndNotDeleted(folderId, user)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        }

        Resource resource = new Resource();
        resource.setFileName(fileName);
        resource.setFilePath(objectKey);
        resource.setUploader(user);
        resource.setFolder(folder);
        resource.setFileSize(fileSize);
        resource = resourceRepository.save(resource);

        int expiryMinutes = 15;
        String uploadUrl = awsS3Service.generatePresignedUploadUrl(objectKey, expiryMinutes, user);

        Map<String, Object> result = new HashMap<>();
        result.put("uploadUrl", uploadUrl);
        result.put("fileId", resource.getId());
        result.put("objectKey", objectKey);
        result.put("expiresInMinutes", expiryMinutes);

        return result;
    }

    @Async("uploadUrlExecutor")
    public CompletableFuture<Map<String, Object>> generateUploadUrlAsync(
            String fileName, Long fileSize, Long folderId, User user) {
        try {
            Map<String, Object> result = generateUploadUrl(fileName, fileSize, folderId, user);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Async upload URL generation failed for user {}: {}", 
                user.getUsername(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    @Transactional
    public Resource confirmUpload(Long fileId, String contentType, User user) {
        Resource resource = resourceRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("File not found"));

        if (!resource.getUploader().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        if (!awsS3Service.objectExists(resource.getFilePath())) {
            resourceRepository.delete(resource);
            throw new RuntimeException("File upload failed - file not found in storage");
        }

        Long actualStorageUsed = resourceRepository.getTotalStorageUsedByUser(user);
        if (actualStorageUsed + resource.getFileSize() > user.getStorageQuota()) {
            log.warn("Storage quota exceeded for user {}: used={}, quota={}, file={}",
                user.getUsername(), actualStorageUsed, user.getStorageQuota(), resource.getFileSize());
            
            try {
                awsS3Service.deleteObject(resource.getFilePath());
            } catch (Exception e) {
                log.error("Failed to delete file from S3 during quota rollback: {}", e.getMessage());
            }
            
            resourceRepository.delete(resource);
            
            throw new RuntimeException("Storage quota exceeded. Used: " + formatBytes(actualStorageUsed) + 
                ", Quota: " + formatBytes(user.getStorageQuota()) + 
                ". Please delete some files before uploading.");
        }

        if (contentType != null) {
            resource.setContentType(contentType);
        }

        resource = resourceRepository.save(resource);

        user.setStorageUsed(actualStorageUsed + resource.getFileSize());
        userRepository.save(user);
        
        storageQuotaService.invalidateStorageCache(user.getId());

        resource.setBackupStatus("PENDING");
        resourceRepository.save(resource);
        try {
            sqsService.sendBackupMessage(resource.getId(), resource.getFilePath(), resource.getFileSize());
            cloudWatchMetricsService.recordBackupResult("PENDING", resource.getFilePath());
        } catch (Exception ex) {
            log.error("Failed to send backup message to SQS for {}: {}", resource.getFilePath(), ex.getMessage());
            resource.setBackupStatus("FAILED");
            resource.setBackupError("Failed to enqueue backup: " + ex.getMessage());
            resourceRepository.save(resource);
            cloudWatchMetricsService.recordBackupResult("FAILED", resource.getFilePath());
        }

        log.info("File upload confirmed: {} by user: {}", resource.getFileName(), user.getUsername());

        return resource;
    }

    @Transactional(readOnly = true)
    public Resource getFile(Long fileId, User user) {
        Resource resource = resourceRepository.findById(fileId)
            .orElseThrow(() -> new RuntimeException("File not found"));

        if (!resource.getUploader().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied to this file");
        }

        return resource;
    }

    @Transactional(readOnly = true)
    public String getDownloadUrl(Long fileId, User user) {
        long startTime = System.currentTimeMillis();
        Resource resource = getFile(fileId, user);

        if (!user.isRead()) {
            throw new RuntimeException("User does not have permission to download files");
        }

        int expiryMinutes = 10;
        String downloadUrl = awsS3Service.generatePresignedDownloadUrl(resource.getFilePath(), expiryMinutes, user);
        
        long duration = System.currentTimeMillis() - startTime;
        double latencySeconds = duration / 1000.0;
        try {
            List<Dimension> dimensions = new ArrayList<>();
            dimensions.add(Dimension.builder().name("Operation").value("Download").build());
            dimensions.add(Dimension.builder().name("ObjectKey").value(resource.getFilePath()).build());
            cloudWatchMetricsService.recordCustomMetric("FileOperationLatency", latencySeconds, dimensions);
        } catch (Exception e) {
            log.debug("Failed to record download metric: {}", e.getMessage());
        }
        
        return downloadUrl;
    }

    @Transactional
    public void deleteFile(Long fileId, User user) {
        Resource resource = getFile(fileId, user);

        awsS3Service.deleteObject(resource.getFilePath());

        user.setStorageUsed(user.getStorageUsed() - resource.getFileSize());
        userRepository.save(user);

        resourceRepository.delete(resource);
        
        storageQuotaService.invalidateStorageCache(user.getId());

        log.info("File deleted: {} by user: {}", resource.getFileName(), user.getUsername());
    }

    @Transactional(readOnly = true, timeout = 20)
    public List<Resource> getAllFiles(User user, Long folderId) {
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerAndNotDeleted(folderId, user)
                .orElse(null);
        }
        return resourceRepository.findByUploaderAndFolderNullableOrderByUploadedAtDesc(user, folder);
    }

    @Transactional(readOnly = true)
    public Page<Resource> listFiles(User user, Long folderId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerAndNotDeleted(folderId, user)
                .orElse(null);
        }
        
        return resourceRepository.findByUploaderAndFolderNullableOrderByUploadedAtDesc(user, folder, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Resource> searchFiles(User user, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return resourceRepository.searchByUploaderAndFileName(user, query, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Resource> searchFilesWithFilters(User user, Long folderId, String query, String fileType, Long minSize, Long maxSize, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndOwnerAndNotDeleted(folderId, user)
                .orElse(null);
        }
        
        return resourceRepository.searchWithFilters(user, folder, query, fileType, minSize, maxSize, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFileMetadata(Long fileId, User user) {
        Resource resource = getFile(fileId, user);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("id", resource.getId());
        metadata.put("fileName", resource.getFileName());
        metadata.put("originalName", resource.getOriginalName());
        metadata.put("fileSize", resource.getFileSize());
        metadata.put("fileSizeFormatted", formatBytes(resource.getFileSize()));
        metadata.put("contentType", resource.getContentType());
        metadata.put("uploadedAt", resource.getUploadedAt());
        metadata.put("lastModified", resource.getLastModified());
        metadata.put("isPublic", resource.isPublic());

        if (resource.isPublic() && resource.getPublicLinkToken() != null) {
            metadata.put("publicLinkToken", resource.getPublicLinkToken());
            metadata.put("publicLinkCreatedAt", resource.getPublicLinkCreatedAt());
        }

        return metadata;
    }

    @Transactional
    public String generatePublicLink(Long fileId, User user) {
        Resource resource = getFile(fileId, user);
        
        if (resource.getPublicLinkToken() == null) {
            resource.generatePublicLinkToken();
            resourceRepository.save(resource);
        }

        return resource.getPublicLinkToken();
    }

    @Transactional
    public void revokePublicLink(Long fileId, User user) {
        Resource resource = getFile(fileId, user);
        resource.revokePublicLink();
        resourceRepository.save(resource);
    }

    public Resource getFileByPublicToken(String token) {
        return resourceRepository.findByPublicLinkToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid or expired public link"));
    }

    public String getPublicDownloadUrl(String token) {
        Resource resource = getFileByPublicToken(token);
        
        User tempUser = new User();
        tempUser.setRead(true);
        
        int expiryMinutes = 60;
        return awsS3Service.generatePresignedDownloadUrl(resource.getFilePath(), expiryMinutes, tempUser);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserStorageInfo(User user) {
        Long actualStorageUsed = resourceRepository.getTotalStorageUsedByUser(user);
        
        if (!actualStorageUsed.equals(user.getStorageUsed())) {
            user.setStorageUsed(actualStorageUsed);
            userRepository.save(user);
        }

        Map<String, Object> storageInfo = new HashMap<>();
        storageInfo.put("storageUsed", user.getStorageUsed());
        storageInfo.put("storageQuota", user.getStorageQuota());
        storageInfo.put("storageRemaining", user.getRemainingStorage());
        storageInfo.put("storageUsedFormatted", formatBytes(user.getStorageUsed()));
        storageInfo.put("storageQuotaFormatted", formatBytes(user.getStorageQuota()));
        storageInfo.put("storageRemainingFormatted", formatBytes(user.getRemainingStorage()));
        storageInfo.put("usagePercentage", String.format("%.2f", user.getStorageUsagePercentage()));

        return storageInfo;
    }

    @Transactional
    public Resource moveFile(Long fileId, Long targetFolderId, User user) {
        Resource resource = getFile(fileId, user);
        
        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findByIdAndOwnerAndNotDeleted(targetFolderId, user)
                .orElseThrow(() -> new RuntimeException("Target folder not found"));
        }
        
        resource.setFolder(targetFolder);
        return resourceRepository.save(resource);
    }

    @Transactional
    public Resource renameFile(Long fileId, String newName, User user) {
        Resource resource = getFile(fileId, user);

        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("Invalid file name");
        }

        resource.setFileName(newName.trim());
        return resourceRepository.save(resource);
    }

    @Transactional
    public void bulkDeleteFiles(List<Long> fileIds, User user) {
        List<Resource> resources = resourceRepository.findByIdsAndUploader(fileIds, user);
        
        long totalSize = 0;
        for (Resource resource : resources) {
            awsS3Service.deleteObject(resource.getFilePath());
            
            totalSize += resource.getFileSize();
            
            resourceRepository.delete(resource);
        }
        
        user.setStorageUsed(user.getStorageUsed() - totalSize);
        userRepository.save(user);
        
        storageQuotaService.invalidateStorageCache(user.getId());
        
        log.info("Bulk deleted {} files by user: {}", resources.size(), user.getUsername());
    }

    @Async("fileOperationExecutor")
    public CompletableFuture<Void> bulkDeleteFilesAsync(List<Long> fileIds, User user) {
        try {
            bulkDeleteFiles(fileIds, user);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("Async bulk delete failed for user {}: {}", user.getUsername(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    @Transactional
    public void bulkMoveFiles(List<Long> fileIds, Long targetFolderId, User user) {
        List<Resource> resources = resourceRepository.findByIdsAndUploader(fileIds, user);
        
        Folder targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findByIdAndOwnerAndNotDeleted(targetFolderId, user)
                .orElseThrow(() -> new RuntimeException("Target folder not found"));
        }
        
        for (Resource resource : resources) {
            resource.setFolder(targetFolder);
            resourceRepository.save(resource);
        }
        
        log.info("Bulk moved {} files to folder {} by user: {}", resources.size(), targetFolderId, user.getUsername());
    }

    public Resource getFileIncludingDeleted(Long fileId, User user) {
        return resourceRepository.findByIdAndUploader(fileId, user)
            .orElseThrow(() -> new RuntimeException("File not found"));
    }

    @Transactional(readOnly = true)
    public String generateBulkDownloadUrl(List<Long> fileIds, User user) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new RuntimeException("No files selected for download");
        }

        List<Resource> resources = resourceRepository.findByIdsAndUploader(fileIds, user);
        if (resources.isEmpty()) {
            throw new RuntimeException("No files found");
        }

        Path tempZip = null;
        try {
            tempZip = Files.createTempFile("bulk-download-", ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tempZip)))) {

                for (Resource resource : resources) {
                    try (InputStream fileStream = awsS3Service.getObjectInputStream(resource.getFilePath())) {
                        ZipEntry entry = new ZipEntry(
                            resource.getFileName() != null ? resource.getFileName() : resource.getId() + ".bin");
                        zos.putNextEntry(entry);
                        fileStream.transferTo(zos);
                        zos.closeEntry();
                    } catch (Exception e) {
                        log.error("Error adding file {} to ZIP: {}", resource.getFileName(), e.getMessage());
                    }
                }
            }

            String zipObjectKey = "temp-downloads/user-" + user.getId() + "/bulk-" + System.currentTimeMillis() + ".zip";
            awsS3Service.uploadObject(zipObjectKey, tempZip, "application/zip");

            int expiryMinutes = 30;
            return awsS3Service.generatePresignedDownloadUrl(zipObjectKey, expiryMinutes, user);

        } catch (Exception e) {
            log.error("Error generating bulk download ZIP", e);
            throw new RuntimeException("Failed to create ZIP file: " + e.getMessage());
        } finally {
            if (tempZip != null) {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ex) {
                    log.warn("Failed to delete temp zip file {}", tempZip, ex);
                }
            }
        }
    }

    private String generateUniqueFileName(String originalName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomStr = UUID.randomUUID().toString().substring(0, 8);
        
        int lastDotIndex = originalName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String name = originalName.substring(0, lastDotIndex);
            String extension = originalName.substring(lastDotIndex);
            return name + "_" + timestamp + "_" + randomStr + extension;
        } else {
            return originalName + "_" + timestamp + "_" + randomStr;
        }
    }

    private String formatBytes(Long bytes) {
        if (bytes == null || bytes < 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }
}

