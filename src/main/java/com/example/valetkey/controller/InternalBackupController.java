package com.example.valetkey.controller;

import com.example.valetkey.model.Resource;
import com.example.valetkey.repository.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/backup")
public class InternalBackupController {

    private static final Logger log = LoggerFactory.getLogger(InternalBackupController.class);

    @Autowired
    private ResourceRepository resourceRepository;

    @Value("${internal.api.key:}")
    private String internalApiKey;

    @PostMapping("/{resourceId}/status")
    public ResponseEntity<?> updateBackupStatus(
            @PathVariable Long resourceId,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-API-Key", required = false) String headerApiKey,
            @RequestParam(value = "apiKey", required = false) String queryApiKey) {
        
        try {
            if (internalApiKey != null && !internalApiKey.isEmpty()) {
                String providedKey = headerApiKey != null ? headerApiKey : queryApiKey;
                if (providedKey == null || !providedKey.equals(internalApiKey)) {
                    log.warn("Unauthorized backup status update attempt for resourceId: {}", resourceId);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unauthorized", "message", "Invalid API key"));
                }
            }

            String status = request.get("status");
            String error = request.get("error");

            if (status == null || status.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Status is required"));
            }

            if (!status.equals("PENDING_SYNC") && 
                !status.equals("COMPLETED") && 
                !status.equals("FAILED") &&
                !status.equals("PENDING")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status", 
                                "validStatuses", "PENDING_SYNC, COMPLETED, FAILED, PENDING"));
            }

            Resource resource = resourceRepository.findById(resourceId).orElse(null);
            
            if (resource == null) {
                log.warn("Resource not found: {}", resourceId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Resource not found", "resourceId", resourceId));
            }

            resource.setBackupStatus(status);
            resource.setBackupAt(LocalDateTime.now());
            resource.setBackupError(error);
            resourceRepository.save(resource);

            log.info("Backup status updated - ResourceId: {}, Status: {}, Error: {}", 
                    resourceId, status, error);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("resourceId", resourceId);
            response.put("status", status);
            response.put("updatedAt", resource.getBackupAt());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update backup status for ResourceId={}: {}", resourceId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error", "message", e.getMessage()));
        }
    }

    @GetMapping("/{resourceId}/status")
    public ResponseEntity<?> getBackupStatus(
            @PathVariable Long resourceId,
            @RequestHeader(value = "X-API-Key", required = false) String headerApiKey,
            @RequestParam(value = "apiKey", required = false) String queryApiKey) {
        
        try {
            if (internalApiKey != null && !internalApiKey.isEmpty()) {
                String providedKey = headerApiKey != null ? headerApiKey : queryApiKey;
                if (providedKey == null || !providedKey.equals(internalApiKey)) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Unauthorized"));
                }
            }

            Resource resource = resourceRepository.findById(resourceId).orElse(null);
            
            if (resource == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Resource not found"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("resourceId", resourceId);
            response.put("backupStatus", resource.getBackupStatus());
            response.put("backupAt", resource.getBackupAt());
            response.put("backupError", resource.getBackupError());
            response.put("filePath", resource.getFilePath());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get backup status for ResourceId={}: {}", resourceId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
}



