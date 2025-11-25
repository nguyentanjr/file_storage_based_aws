package com.example.valetkey.repository;

import com.example.valetkey.model.Folder;
import com.example.valetkey.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    
    // Find folders by owner and parent (excluding deleted)
    @Query("SELECT f FROM Folder f WHERE f.owner = :owner AND f.parentFolder = :parentFolder AND f.isDeleted = false ORDER BY f.folderName ASC")
    List<Folder> findByOwnerAndParentFolderAndNotDeleted(User owner, Folder parentFolder);
    
    // Find root folders (parent is null)
    @Query("SELECT f FROM Folder f WHERE f.owner = :owner AND f.parentFolder IS NULL AND f.isDeleted = false ORDER BY f.folderName ASC")
    List<Folder> findRootFoldersByOwner(User owner);
    
    // Find folder by ID and owner (including deleted)
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.owner = :owner")
    Optional<Folder> findByIdAndOwner(Long folderId, User owner);
    
    // Find folder by ID and owner (excluding deleted)
    @Query("SELECT f FROM Folder f WHERE f.id = :folderId AND f.owner = :owner AND f.isDeleted = false")
    Optional<Folder> findByIdAndOwnerAndNotDeleted(Long folderId, User owner);
    
    // Find all subfolders recursively
    @Query("SELECT f FROM Folder f WHERE f.owner = :owner AND f.isDeleted = false")
    List<Folder> findAllByOwnerAndNotDeleted(User owner);
    
    // Check if folder name exists in parent
    @Query("SELECT COUNT(f) > 0 FROM Folder f WHERE f.owner = :owner AND f.parentFolder = :parentFolder AND f.folderName = :folderName AND f.isDeleted = false")
    boolean existsByOwnerAndParentFolderAndFolderName(User owner, Folder parentFolder, String folderName);
    
    // Find deleted folders
    @Query("SELECT f FROM Folder f WHERE f.owner = :owner AND f.isDeleted = true ORDER BY f.deletedAt DESC")
    List<Folder> findDeletedFoldersByOwner(User owner);
}

