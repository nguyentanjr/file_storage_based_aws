package com.example.valetkey.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resources")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath; // Path in AWS S3

    @Column(name = "original_name")
    private String originalName;

    @ManyToOne
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @ManyToOne
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "is_public")
    private boolean isPublic = false;

    // Token for public link sharing
    @Column(name = "public_link_token", unique = true)
    private String publicLinkToken;

    @Column(name = "public_link_created_at")
    private LocalDateTime publicLinkCreatedAt;

    @Column(name = "last_modified")
    private LocalDateTime lastModified = LocalDateTime.now();

    // Trash/Recycle Bin fields
    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Backup status to secondary storage
    @Column(name = "backup_status")
    private String backupStatus; // PENDING, COMPLETED, FAILED, null if disabled

    @Column(name = "backup_at")
    private LocalDateTime backupAt;

    @Column(name = "backup_error", length = 512)
    private String backupError;

    // Constructors
    public Resource() {
    }

    public Resource(Long id, String fileName, String filePath, String originalName, User uploader, LocalDateTime uploadedAt, Long fileSize, String contentType, boolean isPublic, String publicLinkToken, LocalDateTime publicLinkCreatedAt, LocalDateTime lastModified, boolean isDeleted, LocalDateTime deletedAt, String backupStatus, LocalDateTime backupAt, String backupError) {
        this.id = id;
        this.fileName = fileName;
        this.filePath = filePath;
        this.originalName = originalName;
        this.uploader = uploader;
        this.uploadedAt = uploadedAt;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.isPublic = isPublic;
        this.publicLinkToken = publicLinkToken;
        this.publicLinkCreatedAt = publicLinkCreatedAt;
        this.lastModified = lastModified;
        this.isDeleted = isDeleted;
        this.deletedAt = deletedAt;
        this.backupStatus = backupStatus;
        this.backupAt = backupAt;
        this.backupError = backupError;
    }

    @PreUpdate
    private void preUpdate() {
        lastModified = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public User getUploader() {
        return uploader;
    }

    public void setUploader(User uploader) {
        this.uploader = uploader;
    }

    public Folder getFolder() {
        return folder;
    }

    public void setFolder(Folder folder) {
        this.folder = folder;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public String getPublicLinkToken() {
        return publicLinkToken;
    }

    public void setPublicLinkToken(String publicLinkToken) {
        this.publicLinkToken = publicLinkToken;
    }

    public LocalDateTime getPublicLinkCreatedAt() {
        return publicLinkCreatedAt;
    }

    public void setPublicLinkCreatedAt(LocalDateTime publicLinkCreatedAt) {
        this.publicLinkCreatedAt = publicLinkCreatedAt;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getBackupStatus() {
        return backupStatus;
    }

    public void setBackupStatus(String backupStatus) {
        this.backupStatus = backupStatus;
    }

    public LocalDateTime getBackupAt() {
        return backupAt;
    }

    public void setBackupAt(LocalDateTime backupAt) {
        this.backupAt = backupAt;
    }

    public String getBackupError() {
        return backupError;
    }

    public void setBackupError(String backupError) {
        this.backupError = backupError;
    }

    // Generate public link token
    public void generatePublicLinkToken() {
        this.publicLinkToken = UUID.randomUUID().toString();
        this.publicLinkCreatedAt = LocalDateTime.now();
        this.isPublic = true;
    }

    // Revoke public link
    public void revokePublicLink() {
        this.publicLinkToken = null;
        this.publicLinkCreatedAt = null;
        this.isPublic = false;
    }

    // Move to trash (soft delete)
    public void moveToTrash() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    // Restore from trash
    public void restoreFromTrash() {
        this.isDeleted = false;
        this.deletedAt = null;
    }
}
