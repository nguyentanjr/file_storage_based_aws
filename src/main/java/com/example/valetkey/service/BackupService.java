package com.example.valetkey.service;

import com.example.valetkey.model.Resource;
import com.example.valetkey.repository.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.InputStream;
import java.time.LocalDateTime;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    @Autowired
    private AWSS3Service awsS3Service;

    @Autowired
    @Qualifier("ibmS3Client")
    private S3Client ibmS3Client;

    @Autowired
    private ResourceRepository resourceRepository;

    @Value("${backup.enabled:true}")
    private boolean backupEnabled;

    @Value("${ibm.cos.bucket-name:valet-backup}")
    private String ibmBucket;

    @Value("${backup.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${backup.retry.backoff-millis:5000}")
    private long backoffMillis;

    public void backupObject(String objectKey) {
        if (!backupEnabled) {
            return;
        }
        long contentLength = awsS3Service.getObjectContentLength(objectKey);
        try (InputStream in = awsS3Service.getObjectInputStream(objectKey)) {
            PutObjectRequest put = PutObjectRequest.builder()
                    .bucket(ibmBucket)
                    .key(objectKey)
                    .build();
            ibmS3Client.putObject(put, RequestBody.fromInputStream(in, contentLength));
            log.info("Backed up object to IBM COS: {}", objectKey);
        } catch (Exception e) {
            throw new RuntimeException("Backup failed for key " + objectKey + ": " + e.getMessage(), e);
        }
    }

    @Async
    public void backupObjectAsync(Long resourceId, String objectKey) {
        if (!backupEnabled) {
            return;
        }
        try {
            updateResourceStatus(resourceId, "IN_PROGRESS", null);
            backupWithRetry(objectKey);
            updateResourceStatus(resourceId, "COMPLETED", null);
        } catch (Exception ex) {
            log.error("Backup error for {}: {}", objectKey, ex.getMessage(), ex);
            updateResourceStatus(resourceId, "FAILED", ex.getMessage());
        }
    }

    private void backupWithRetry(String objectKey) {
        int attempt = 0;
        while (true) {
            try {
                backupObject(objectKey);
                return;
            } catch (Exception ex) {
                attempt++;
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                log.warn("Backup attempt {}/{} failed for {}: {}. Retrying in {} ms",
                        attempt, maxAttempts, objectKey, ex.getMessage(), backoffMillis);
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Backup retry interrupted", ie);
                }
            }
        }
    }

    private void updateResourceStatus(Long resourceId, String status, String error) {
        Resource resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            return;
        }
        resource.setBackupStatus(status);
        resource.setBackupAt(LocalDateTime.now());
        resource.setBackupError(error);
        resourceRepository.save(resource);
    }
}

