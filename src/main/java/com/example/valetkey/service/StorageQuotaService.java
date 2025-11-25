package com.example.valetkey.service;

import com.example.valetkey.model.User;
import com.example.valetkey.repository.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class StorageQuotaService {

    private static final Logger log = LoggerFactory.getLogger(StorageQuotaService.class);

    @Autowired
    private ResourceRepository resourceRepository;

    @Cacheable(value = "storageQuota", key = "#user.id")
    public Long getStorageUsed(User user) {
        log.debug("Cache miss - querying DB for storage used by user: {}", user.getUsername());
        Long storageUsed = resourceRepository.getTotalStorageUsedByUser(user);
        return storageUsed != null ? storageUsed : 0L;
    }

    public boolean hasStorageSpace(User user, Long requiredSize) {
        Long currentUsage = getStorageUsed(user);
        Long quota = user.getStorageQuota();
        
        if (quota == null) {
            log.warn("User {} has no storage quota set, defaulting to 1GB", user.getUsername());
            quota = 1073741824L;
        }
        
        boolean hasSpace = (currentUsage + requiredSize) <= quota;
        
        if (!hasSpace) {
            log.debug("Storage check failed for user {}: current={}, required={}, quota={}", 
                user.getUsername(), currentUsage, requiredSize, quota);
        }
        
        return hasSpace;
    }

    public Long getRemainingStorage(User user) {
        Long currentUsage = getStorageUsed(user);
        Long quota = user.getStorageQuota() != null ? user.getStorageQuota() : 1073741824L;
        return Math.max(0, quota - currentUsage);
    }

    @CacheEvict(value = "storageQuota", key = "#userId")
    public void invalidateStorageCache(Long userId) {
        log.debug("Invalidating storage cache for user ID: {}", userId);
    }

    @CacheEvict(value = "storageQuota", allEntries = true)
    public void invalidateAllStorageCaches() {
        log.info("Invalidating all storage quota caches");
    }

    public String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        
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


