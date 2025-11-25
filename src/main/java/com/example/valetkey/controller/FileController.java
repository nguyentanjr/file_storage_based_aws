package com.example.valetkey.controller;

import com.example.valetkey.model.Resource;
import com.example.valetkey.model.User;
import com.example.valetkey.repository.ResourceRepository;
import com.example.valetkey.repository.UserRepository;
import com.example.valetkey.service.FileService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private com.example.valetkey.service.CloudWatchMetricsService cloudWatchMetricsService;

    @PostMapping("/upload-url")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> generateUploadUrl(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .<Map<String, Object>>body(Map.of("message", "Not authenticated"))
                );
            }

            String fileName = (String) request.get("fileName");
            Long fileSize = request.get("fileSize") != null 
                ? Long.valueOf(request.get("fileSize").toString()) 
                : null;
            Long folderId = request.get("folderId") != null
                ? Long.valueOf(request.get("folderId").toString())
                : null;

            if (fileName == null || fileSize == null) {
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                        .<Map<String, Object>>body(Map.of("message", "fileName and fileSize are required"))
                );
            }

            User user = userRepository.getUserById(sessionUser.getId());
            
            return fileService.generateUploadUrlAsync(fileName, fileSize, folderId, user)
                .thenApply(uploadInfo -> ResponseEntity.ok(uploadInfo))
                .exceptionally(ex -> {
                    log.error("Error generating upload URL", ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .<Map<String, Object>>body(Map.of("message", "Failed to generate upload URL: " + ex.getMessage()));
                });

        } catch (Exception e) {
            log.error("Error generating upload URL", e);
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .<Map<String, Object>>body(Map.of("message", "Failed to generate upload URL: " + e.getMessage()))
            );
        }
    }

    @PostMapping("/upload/confirm")
    public ResponseEntity<?> confirmUpload(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            Long fileId = request.get("fileId") != null 
                ? Long.valueOf(request.get("fileId").toString()) 
                : null;
            String contentType = (String) request.get("contentType");

            if (fileId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileId is required"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Resource resource = fileService.confirmUpload(fileId, contentType, user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "File uploaded successfully");
            response.put("file", fileToMap(resource));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error confirming upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Failed to confirm upload: " + e.getMessage()));
        }
    }

    @GetMapping("/{fileId:\\d+}")
    public ResponseEntity<?> getFile(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Map<String, Object> metadata = fileService.getFileMetadata(fileId, user);

            return ResponseEntity.ok(metadata);

        } catch (Exception e) {
            log.error("Error getting file", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{fileId:\\d+}/download")
    public ResponseEntity<?> getDownloadUrl(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                log.warn("Download request for file {} failed: No session user", fileId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated. Please login again."));
            }

            log.debug("Download request for file {} by user {}", fileId, sessionUser.getUsername());
            User user = userRepository.getUserById(sessionUser.getId());
            
            if (user == null) {
                log.warn("User not found in database: {}", sessionUser.getId());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "User not found"));
            }

            String downloadUrl = fileService.getDownloadUrl(fileId, user);

            log.info("Download URL generated for file {} by user {}", fileId, user.getUsername());
            return ResponseEntity.ok(Map.of(
                "downloadUrl", downloadUrl,
                "expiresInMinutes", 10
            ));

        } catch (RuntimeException e) {
            log.error("Error generating download URL for file {}: {}", fileId, e.getMessage());
            if (e.getMessage().contains("not found") || e.getMessage().contains("File not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "File not found"));
            } else if (e.getMessage().contains("Access denied") || e.getMessage().contains("permission")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", e.getMessage()));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to generate download URL: " + e.getMessage()));
            }
        } catch (Exception e) {
            log.error("Unexpected error generating download URL for file {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Internal server error"));
        }
    }

    @DeleteMapping("/{fileId:\\d+}")
    public ResponseEntity<?> deleteFile(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.deleteFile(fileId, user);

            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/all-ids")
    public ResponseEntity<?> getAllFileIds(
            @RequestParam(value = "folderId", required = false) Long folderId,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Resource> allFiles = fileService.getAllFiles(user, folderId);
            List<Long> fileIds = allFiles.stream()
                .map(Resource::getId)
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "fileIds", fileIds,
                "count", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error getting all file IDs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFiles(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "folderId", required = false) Long folderId,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Page<Resource> filesPage = fileService.listFiles(user, folderId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("files", filesPage.getContent().stream()
                .map(this::fileToMap)
                .collect(Collectors.toList()));
            response.put("currentPage", filesPage.getNumber());
            response.put("totalPages", filesPage.getTotalPages());
            response.put("totalItems", filesPage.getTotalElements());
            response.put("hasNext", filesPage.hasNext());
            response.put("hasPrevious", filesPage.hasPrevious());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(
            @RequestParam("query") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Page<Resource> filesPage = fileService.searchFiles(user, query, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("files", filesPage.getContent().stream()
                .map(this::fileToMap)
                .collect(Collectors.toList()));
            response.put("currentPage", filesPage.getNumber());
            response.put("totalPages", filesPage.getTotalPages());
            response.put("totalItems", filesPage.getTotalElements());
            response.put("query", query);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{fileId:\\d+}/move")
    public ResponseEntity<?> moveFile(
            @PathVariable Long fileId,
            @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Resource resource = fileService.moveFile(fileId, targetFolderId, user);

            return ResponseEntity.ok(Map.of(
                "message", "File moved successfully",
                "file", fileToMap(resource)
            ));

        } catch (Exception e) {
            log.error("Error moving file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{fileId:\\d+}/rename")
    public ResponseEntity<?> renameFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> request,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            String newName = request.get("newName");
            if (newName == null || newName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "New name is required"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Resource resource = fileService.renameFile(fileId, newName, user);

            return ResponseEntity.ok(Map.of(
                "message", "File renamed successfully",
                "file", fileToMap(resource)
            ));

        } catch (Exception e) {
            log.error("Error renaming file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{fileId:\\d+}/share")
    public ResponseEntity<?> generatePublicLink(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            String token = fileService.generatePublicLink(fileId, user);

            return ResponseEntity.ok(Map.of(
                "message", "Public link generated successfully",
                "publicLinkToken", token,
                "publicUrl", "/api/public/files/" + token
            ));

        } catch (Exception e) {
            log.error("Error generating public link", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{fileId:\\d+}/share")
    public ResponseEntity<?> revokePublicLink(@PathVariable Long fileId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.revokePublicLink(fileId, user);

            return ResponseEntity.ok(Map.of("message", "Public link revoked successfully"));

        } catch (Exception e) {
            log.error("Error revoking public link", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/storage")
    public ResponseEntity<?> getStorageInfo(HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Map<String, Object> storageInfo = fileService.getUserStorageInfo(user);

            return ResponseEntity.ok(storageInfo);

        } catch (Exception e) {
            log.error("Error getting storage info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }


    @PostMapping("/bulk-delete")
    public ResponseEntity<?> bulkDeleteFiles(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            @SuppressWarnings("unchecked")
            List<Object> fileIdsObj = (List<Object>) request.get("fileIds");
            if (fileIdsObj == null || fileIdsObj.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileIds is required"));
            }

            List<Long> fileIds = fileIdsObj.stream()
                .map(id -> {
                    if (id instanceof Long) {
                        return (Long) id;
                    } else if (id instanceof Integer) {
                        return ((Integer) id).longValue();
                    } else if (id instanceof Number) {
                        return ((Number) id).longValue();
                    } else {
                        return Long.valueOf(id.toString());
                    }
                })
                .collect(java.util.stream.Collectors.toList());

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.bulkDeleteFiles(fileIds, user);

            return ResponseEntity.ok(Map.of(
                "message", "Files deleted successfully",
                "count", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error bulk deleting files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/bulk-move")
    public ResponseEntity<?> bulkMoveFiles(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            @SuppressWarnings("unchecked")
            List<Object> fileIdsObj = (List<Object>) request.get("fileIds");
            if (fileIdsObj == null || fileIdsObj.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileIds is required"));
            }

            List<Long> fileIds = fileIdsObj.stream()
                .map(id -> {
                    if (id instanceof Long) {
                        return (Long) id;
                    } else if (id instanceof Integer) {
                        return ((Integer) id).longValue();
                    } else if (id instanceof Number) {
                        return ((Number) id).longValue();
                    } else {
                        return Long.valueOf(id.toString());
                    }
                })
                .collect(java.util.stream.Collectors.toList());

            Long targetFolderId = null;
            if (request.get("targetFolderId") != null) {
                Object targetIdObj = request.get("targetFolderId");
                if (targetIdObj instanceof Long) {
                    targetFolderId = (Long) targetIdObj;
                } else if (targetIdObj instanceof Integer) {
                    targetFolderId = ((Integer) targetIdObj).longValue();
                } else if (targetIdObj instanceof Number) {
                    targetFolderId = ((Number) targetIdObj).longValue();
                } else {
                    targetFolderId = Long.valueOf(targetIdObj.toString());
                }
            }

            User user = userRepository.getUserById(sessionUser.getId());
            fileService.bulkMoveFiles(fileIds, targetFolderId, user);

            return ResponseEntity.ok(Map.of(
                "message", "Files moved successfully",
                "count", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error bulk moving files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }


    private Map<String, Object> fileToMap(Resource resource) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", resource.getId());
        map.put("fileName", resource.getFileName());
        map.put("originalName", resource.getOriginalName());
        map.put("fileSize", resource.getFileSize());
        map.put("contentType", resource.getContentType());
        map.put("uploadedAt", resource.getUploadedAt());
        map.put("lastModified", resource.getLastModified());
        map.put("isPublic", resource.isPublic());

        return map;
    }
    
    @GetMapping("/test-cloudwatch-metrics")
    public ResponseEntity<?> testCloudWatchMetrics(HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }
            
            String testObjectKey = "test-metrics/" + System.currentTimeMillis() + ".txt";
            
            cloudWatchMetricsService.recordBackupResult("PENDING", testObjectKey);
            log.info("Sent metric: BackupSuccess (Status=PENDING) for {}", testObjectKey);
            
            cloudWatchMetricsService.recordBackupResult("COMPLETED", testObjectKey);
            log.info("Sent metric: BackupSuccess (Status=COMPLETED) for {}", testObjectKey);
            
            double testLatency = 1.0 + (Math.random() * 9.0);
            cloudWatchMetricsService.recordBackupLatency(testObjectKey, testLatency);
            log.info("Sent metric: BackupLatency = {} seconds for {}", testLatency, testObjectKey);
            
            double testThroughput = 1.0 + (Math.random() * 19.0);
            cloudWatchMetricsService.recordBackupThroughput(testObjectKey, testThroughput);
            log.info("Sent metric: BackupThroughput = {} MB/s for {}", testThroughput, testObjectKey);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test metrics sent to CloudWatch!");
            response.put("namespace", "ValetKey/Backup");
            response.put("testObjectKey", testObjectKey);
            response.put("metrics", Map.of(
                "BackupSuccess (PENDING)", 1,
                "BackupSuccess (COMPLETED)", 1,
                "BackupLatency", testLatency + " seconds",
                "BackupThroughput", testThroughput + " MB/s"
            ));
            response.put("note", "Wait 2-5 minutes, then check AWS CloudWatch Console → Metrics → ValetKey/Backup");
            response.put("grafana", "After 5 minutes, refresh your Grafana dashboard");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to send test metrics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "note", "Check application logs for details. Verify AWS credentials and CloudWatch permissions."
                ));
        }
    }

    @GetMapping("/search/advanced")
    public ResponseEntity<?> advancedSearch(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "fileType", required = false) String fileType,
            @RequestParam(value = "minSize", required = false) Long minSize,
            @RequestParam(value = "maxSize", required = false) Long maxSize,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpSession session) {

        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Page<Resource> filesPage = fileService.searchFilesWithFilters(
                user, folderId, query, fileType, minSize, maxSize, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("files", filesPage.getContent().stream()
                .map(this::fileToMap)
                .collect(Collectors.toList()));
            response.put("currentPage", filesPage.getNumber());
            response.put("totalPages", filesPage.getTotalPages());
            response.put("totalItems", filesPage.getTotalElements());
            response.put("query", query);
            response.put("filters", Map.of(
                "folderId", folderId != null ? folderId : "all",
                "fileType", fileType != null ? fileType : "all",
                "minSize", minSize != null ? minSize : "none",
                "maxSize", maxSize != null ? maxSize : "none"
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in advanced search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/bulk-download")
    public ResponseEntity<?> bulkDownload(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            @SuppressWarnings("unchecked")
            List<Object> fileIdsObj = (List<Object>) request.get("fileIds");
            if (fileIdsObj == null || fileIdsObj.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "fileIds is required"));
            }

            List<Long> fileIds = fileIdsObj.stream()
                .map(id -> {
                    if (id instanceof Long) return (Long) id;
                    else if (id instanceof Integer) return ((Integer) id).longValue();
                    else if (id instanceof Number) return ((Number) id).longValue();
                    else return Long.valueOf(id.toString());
                })
                .collect(java.util.stream.Collectors.toList());

            User user = userRepository.getUserById(sessionUser.getId());
            String downloadUrl = fileService.generateBulkDownloadUrl(fileIds, user);

            return ResponseEntity.ok(Map.of(
                "downloadUrl", downloadUrl,
                "expiresInMinutes", 30,
                "fileCount", fileIds.size()
            ));

        } catch (Exception e) {
            log.error("Error generating bulk download", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

}

