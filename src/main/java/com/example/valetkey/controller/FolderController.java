package com.example.valetkey.controller;

import com.example.valetkey.model.Folder;
import com.example.valetkey.model.User;
import com.example.valetkey.repository.UserRepository;
import com.example.valetkey.service.FolderService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private static final Logger log = LoggerFactory.getLogger(FolderController.class);

    @Autowired
    private FolderService folderService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createFolder(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            String folderName = (String) request.get("folderName");
            Long parentFolderId = request.get("parentFolderId") != null 
                ? Long.valueOf(request.get("parentFolderId").toString()) 
                : null;

            User user = userRepository.getUserById(sessionUser.getId());
            Folder folder = folderService.createFolder(folderName, parentFolderId, user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Folder created successfully");
            response.put("folder", folderToMap(folder));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> listFolders(
            @RequestParam(value = "parentFolderId", required = false) Long parentFolderId,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Folder> folders = folderService.listFolders(parentFolderId, user);

            Map<String, Object> response = new HashMap<>();
            response.put("folders", folders.stream()
                .map(this::folderToMap)
                .toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing folders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<?> getFolder(@PathVariable Long folderId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Folder folder = folderService.getFolder(folderId, user);

            return ResponseEntity.ok(folderToMap(folder));

        } catch (Exception e) {
            log.error("Error getting folder", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/tree")
    public ResponseEntity<?> getFolderTree(HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Map<String, Object>> tree = folderService.getFolderTree(user);

            Map<String, Object> response = new HashMap<>();
            response.put("tree", tree);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting folder tree", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{folderId}/breadcrumb")
    public ResponseEntity<?> getBreadcrumb(@PathVariable Long folderId, HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Map<String, Object>> breadcrumb = folderService.getBreadcrumb(folderId, user);

            Map<String, Object> response = new HashMap<>();
            response.put("breadcrumb", breadcrumb);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting breadcrumb", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/root/breadcrumb")
    public ResponseEntity<?> getRootBreadcrumb(HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Map<String, Object>> breadcrumb = folderService.getBreadcrumb(null, user);

            Map<String, Object> response = new HashMap<>();
            response.put("breadcrumb", breadcrumb);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting root breadcrumb", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{folderId}/rename")
    public ResponseEntity<?> renameFolder(
            @PathVariable Long folderId,
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
            Folder folder = folderService.renameFolder(folderId, newName, user);

            return ResponseEntity.ok(Map.of(
                "message", "Folder renamed successfully",
                "folder", folderToMap(folder)
            ));

        } catch (Exception e) {
            log.error("Error renaming folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{folderId}/move")
    public ResponseEntity<?> moveFolder(
            @PathVariable Long folderId,
            @RequestParam(value = "targetParentFolderId", required = false) Long targetParentFolderId,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            Folder folder = folderService.moveFolder(folderId, targetParentFolderId, user);

            return ResponseEntity.ok(Map.of(
                "message", "Folder moved successfully",
                "folder", folderToMap(folder)
            ));

        } catch (Exception e) {
            log.error("Error moving folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<?> deleteFolder(
            @PathVariable Long folderId,
            @RequestParam(value = "deleteContents", defaultValue = "false") boolean deleteContents,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            folderService.deleteFolder(folderId, deleteContents, user);

            return ResponseEntity.ok(Map.of("message", "Folder deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting folder", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFolders(
            @RequestParam("query") String query,
            HttpSession session) {
        try {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
            }

            User user = userRepository.getUserById(sessionUser.getId());
            List<Folder> folders = folderService.searchFolders(query, user);

            Map<String, Object> response = new HashMap<>();
            response.put("folders", folders.stream()
                .map(folder -> {
                    Map<String, Object> folderMap = folderToMap(folder);
                    folderMap.put("fullPath", folderService.getFullPath(folder));
                    return folderMap;
                })
                .toList());
            response.put("query", query);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching folders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> folderToMap(Folder folder) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", folder.getId());
        map.put("name", folder.getFolderName());
        map.put("createdAt", folder.getCreatedAt());
        map.put("lastModified", folder.getLastModified());
        if (folder.getParentFolder() != null) {
            map.put("parentId", folder.getParentFolder().getId());
        }
        return map;
    }
}

