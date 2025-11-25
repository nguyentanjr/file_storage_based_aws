package com.example.valetkey.repository;

import com.example.valetkey.model.Folder;
import com.example.valetkey.model.Resource;
import com.example.valetkey.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    
    // Find all files by uploader (with pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = false ORDER BY r.uploadedAt DESC")
    Page<Resource> findByUploaderOrderByUploadedAtDesc(User uploader, Pageable pageable);
    
    // Find all files by uploader and folder (with pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.folder = :folder AND r.isDeleted = false ORDER BY r.uploadedAt DESC")
    Page<Resource> findByUploaderAndFolderOrderByUploadedAtDesc(User uploader, Folder folder, Pageable pageable);
    
    // Find all files by uploader and folder (null for root) (with pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND (r.folder IS NULL AND :folder IS NULL OR r.folder = :folder) AND r.isDeleted = false ORDER BY r.uploadedAt DESC")
    Page<Resource> findByUploaderAndFolderNullableOrderByUploadedAtDesc(User uploader, Folder folder, Pageable pageable);
    
    // Find all files by uploader (without pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = false ORDER BY r.uploadedAt DESC")
    List<Resource> findByUploaderOrderByUploadedAtDesc(User uploader);
    
    // Find all files by uploader and folder (without pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND (r.folder IS NULL AND :folder IS NULL OR r.folder = :folder) AND r.isDeleted = false ORDER BY r.uploadedAt DESC")
    List<Resource> findByUploaderAndFolderNullableOrderByUploadedAtDesc(User uploader, Folder folder);
    
    // Search files by name (case-insensitive, with pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = false AND LOWER(r.fileName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Resource> searchByUploaderAndFileName(User uploader, String query, Pageable pageable);
    
    // Search files by name with folder filter (case-insensitive, with pagination) - exclude deleted
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND (r.folder IS NULL AND :folder IS NULL OR r.folder = :folder) AND r.isDeleted = false AND LOWER(r.fileName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Resource> searchByUploaderAndFolderAndFileName(User uploader, Folder folder, String query, Pageable pageable);
    
    // Search files with filters (file type, size range, date range)
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = false " +
           "AND (:folder IS NULL OR r.folder = :folder) " +
           "AND (:query IS NULL OR LOWER(r.fileName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:fileType IS NULL OR r.contentType LIKE CONCAT(:fileType, '%')) " +
           "AND (:minSize IS NULL OR r.fileSize >= :minSize) " +
           "AND (:maxSize IS NULL OR r.fileSize <= :maxSize) " +
           "ORDER BY r.uploadedAt DESC")
    Page<Resource> searchWithFilters(User uploader, Folder folder, String query, String fileType, Long minSize, Long maxSize, Pageable pageable);
    
    // Search files by name (case-insensitive, without pagination)
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND LOWER(r.fileName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Resource> searchByUploaderAndFileName(User uploader, String query);
    
    // Find file by public link token
    Optional<Resource> findByPublicLinkToken(String token);
    
    // Find file by uploader and file path
    Optional<Resource> findByUploaderAndFilePath(User uploader, String filePath);
    
    // Get total storage used by user (excluding deleted)
    @Query("SELECT COALESCE(SUM(r.fileSize), 0) FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = false")
    Long getTotalStorageUsedByUser(User uploader);
    
    // Trash/Recycle Bin queries
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = true ORDER BY r.deletedAt DESC")
    Page<Resource> findDeletedFilesByUser(User uploader, Pageable pageable);
    
    @Query("SELECT r FROM Resource r WHERE r.uploader = :uploader AND r.isDeleted = true ORDER BY r.deletedAt DESC")
    List<Resource> findAllDeletedFilesByUser(User uploader);
    
    // Find file by ID and user (including deleted)
    @Query("SELECT r FROM Resource r WHERE r.id = :fileId AND r.uploader = :uploader")
    Optional<Resource> findByIdAndUploader(Long fileId, User uploader);
    
    // Find files by IDs for bulk operations
    @Query("SELECT r FROM Resource r WHERE r.id IN :fileIds AND r.uploader = :uploader AND r.isDeleted = false")
    List<Resource> findByIdsAndUploader(List<Long> fileIds, User uploader);
    
}
