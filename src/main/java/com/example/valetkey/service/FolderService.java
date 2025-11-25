package com.example.valetkey.service;

import com.example.valetkey.model.Folder;
import com.example.valetkey.model.User;
import com.example.valetkey.repository.FolderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FolderService {

    private static final Logger log = LoggerFactory.getLogger(FolderService.class);

    @Autowired
    private FolderRepository folderRepository;

    @Transactional
    public Folder createFolder(String folderName, Long parentFolderId, User owner) {
        if (folderName == null || folderName.trim().isEmpty()) {
            throw new RuntimeException("Folder name is required");
        }

        folderName = folderName.trim();

        if (folderName.contains("/") || folderName.contains("\\") || folderName.contains("..")) {
            throw new RuntimeException("Invalid folder name");
        }

        Folder parentFolder = null;
        if (parentFolderId != null) {
            parentFolder = folderRepository.findByIdAndOwnerAndNotDeleted(parentFolderId, owner)
                .orElseThrow(() -> new RuntimeException("Parent folder not found"));
            
            if (isCircularReference(parentFolder, parentFolderId)) {
                throw new RuntimeException("Cannot create folder: circular reference detected");
            }
        }

        if (parentFolder != null) {
            if (folderRepository.existsByOwnerAndParentFolderAndFolderName(owner, parentFolder, folderName)) {
                throw new RuntimeException("Folder with this name already exists in this location");
            }
        } else {
            List<Folder> rootFolders = folderRepository.findRootFoldersByOwner(owner);
            String finalFolderName = folderName;
            if (rootFolders.stream().anyMatch(f -> f.getFolderName().equals(finalFolderName))) {
                throw new RuntimeException("Folder with this name already exists in root");
            }
        }

        Folder folder = new Folder(folderName, parentFolder, owner);
        folder = folderRepository.save(folder);

        log.info("Created folder: {} by user: {}", folderName, owner.getUsername());
        return folder;
    }

    @Transactional(readOnly = true)
    public Folder getFolder(Long folderId, User owner) {
        return folderRepository.findByIdAndOwnerAndNotDeleted(folderId, owner)
            .orElseThrow(() -> new RuntimeException("Folder not found"));
    }

    @Transactional(readOnly = true)
    public List<Folder> listFolders(Long parentFolderId, User owner) {
        if (parentFolderId == null) {
            return folderRepository.findRootFoldersByOwner(owner);
        } else {
            Folder parentFolder = folderRepository.findByIdAndOwnerAndNotDeleted(parentFolderId, owner)
                .orElseThrow(() -> new RuntimeException("Parent folder not found"));
            return folderRepository.findByOwnerAndParentFolderAndNotDeleted(owner, parentFolder);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFolderTree(User owner) {
        List<Folder> allFolders = folderRepository.findAllByOwnerAndNotDeleted(owner);
        Map<Long, Map<String, Object>> folderMap = new HashMap<>();
        List<Map<String, Object>> rootFolders = new ArrayList<>();

        for (Folder folder : allFolders) {
            Map<String, Object> folderData = new HashMap<>();
            folderData.put("id", folder.getId());
            folderData.put("name", folder.getFolderName());
            folderData.put("createdAt", folder.getCreatedAt());
            folderData.put("lastModified", folder.getLastModified());
            folderData.put("children", new ArrayList<Map<String, Object>>());
            
            if (folder.getParentFolder() != null) {
                folderData.put("parentId", folder.getParentFolder().getId());
            }
            
            folderMap.put(folder.getId(), folderData);
        }

        for (Map<String, Object> folderData : folderMap.values()) {
            Long parentId = (Long) folderData.get("parentId");
            if (parentId == null) {
                rootFolders.add(folderData);
            } else {
                Map<String, Object> parent = folderMap.get(parentId);
                if (parent != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                    children.add(folderData);
                }
            }
        }

        return rootFolders;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getBreadcrumb(Long folderId, User owner) {
        List<Map<String, Object>> breadcrumb = new ArrayList<>();
        
        Map<String, Object> root = new HashMap<>();
        root.put("id", null);
        root.put("name", "My Files");
        breadcrumb.add(root);

        if (folderId == null) {
            return breadcrumb;
        }

        Folder current = folderRepository.findByIdAndOwnerAndNotDeleted(folderId, owner)
            .orElseThrow(() -> new RuntimeException("Folder not found"));

        List<Map<String, Object>> path = new ArrayList<>();
        while (current != null) {
            Map<String, Object> folderData = new HashMap<>();
            folderData.put("id", current.getId());
            folderData.put("name", current.getFolderName());
            path.add(0, folderData);
            current = current.getParentFolder();
        }

        breadcrumb.addAll(path);
        return breadcrumb;
    }

    @Transactional
    public Folder renameFolder(Long folderId, String newName, User owner) {
        Folder folder = getFolder(folderId, owner);

        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("Folder name is required");
        }

        newName = newName.trim();

        Folder parent = folder.getParentFolder();
        if (parent != null) {
            if (folderRepository.existsByOwnerAndParentFolderAndFolderName(owner, parent, newName)) {
                throw new RuntimeException("Folder with this name already exists");
            }
        } else {
            List<Folder> rootFolders = folderRepository.findRootFoldersByOwner(owner);
            String finalNewName = newName;
            if (rootFolders.stream().anyMatch(f -> f.getFolderName().equals(finalNewName) && !f.getId().equals(folderId))) {
                throw new RuntimeException("Folder with this name already exists");
            }
        }

        folder.setFolderName(newName);
        folder = folderRepository.save(folder);

        log.info("Renamed folder {} to {} by user: {}", folderId, newName, owner.getUsername());
        return folder;
    }

    @Transactional
    public Folder moveFolder(Long folderId, Long targetParentFolderId, User owner) {
        Folder folder = getFolder(folderId, owner);

        Folder targetParent = null;
        if (targetParentFolderId != null) {
            targetParent = folderRepository.findByIdAndOwnerAndNotDeleted(targetParentFolderId, owner)
                .orElseThrow(() -> new RuntimeException("Target parent folder not found"));
            
            if (isDescendant(folder, targetParentFolderId)) {
                throw new RuntimeException("Cannot move folder into itself or its descendants");
            }
        }

        if (targetParent != null) {
            if (folderRepository.existsByOwnerAndParentFolderAndFolderName(owner, targetParent, folder.getFolderName())) {
                throw new RuntimeException("Folder with this name already exists in target location");
            }
        } else {
            List<Folder> rootFolders = folderRepository.findRootFoldersByOwner(owner);
            Folder finalFolder = folder;
            if (rootFolders.stream().anyMatch(f -> f.getFolderName().equals(finalFolder.getFolderName()) && !f.getId().equals(folderId))) {
                throw new RuntimeException("Folder with this name already exists in root");
            }
        }

        folder.setParentFolder(targetParent);
        folder = folderRepository.save(folder);

        log.info("Moved folder {} to parent {} by user: {}", folderId, targetParentFolderId, owner.getUsername());
        return folder;
    }

    @Transactional
    public void deleteFolder(Long folderId, boolean deleteContents, User owner) {
        Folder folder = getFolder(folderId, owner);

        folder.moveToTrash();
        folderRepository.save(folder);

        log.info("Deleted folder {} by user: {}", folderId, owner.getUsername());
    }

    @Transactional(readOnly = true)
    public List<Folder> searchFolders(String query, User owner) {
        List<Folder> allFolders = folderRepository.findAllByOwnerAndNotDeleted(owner);
        String lowerQuery = query.toLowerCase();
        
        return allFolders.stream()
            .filter(folder -> folder.getFolderName().toLowerCase().contains(lowerQuery))
            .toList();
    }

    @Transactional(readOnly = true)
    public String getFullPath(Folder folder) {
        List<String> pathParts = new ArrayList<>();
        Folder current = folder;
        
        while (current != null) {
            pathParts.add(0, current.getFolderName());
            current = current.getParentFolder();
        }
        
        return String.join("/", pathParts);
    }

    private boolean isCircularReference(Folder folder, Long targetId) {
        Folder current = folder;
        while (current != null) {
            if (current.getId().equals(targetId)) {
                return true;
            }
            current = current.getParentFolder();
        }
        return false;
    }

    private boolean isDescendant(Folder folder, Long ancestorId) {
        Folder current = folder.getParentFolder();
        while (current != null) {
            if (current.getId().equals(ancestorId)) {
                return true;
            }
            current = current.getParentFolder();
        }
        return false;
    }
}

