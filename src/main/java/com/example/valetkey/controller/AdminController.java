package com.example.valetkey.controller;

import com.example.valetkey.model.User;
import com.example.valetkey.repository.UserRepository;
import com.example.valetkey.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @PostMapping("/permission/{id}")
    public ResponseEntity<?> updateUserPermission(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> permission,
            HttpSession session) {

        ResponseEntity<?> authResponse = requireAdmin(session);
        if (authResponse != null) return authResponse;

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        user.setCreate(permission.get("create"));
        user.setRead(permission.get("read"));
        user.setWrite(permission.get("write"));

        return ResponseEntity.ok(userRepository.save(user));
    }
    @GetMapping("/user-list")
    public ResponseEntity<?> getUserList(HttpSession session) {
        ResponseEntity<?> authResponse = requireAdmin(session);
        if (authResponse != null) return authResponse;
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/quota/{id}")
    public ResponseEntity<?> updateUserQuota(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        ResponseEntity<?> authResponse = requireAdmin(session);
        if (authResponse != null) return authResponse;

        try {
            Long quotaBytes = null;
            if (request.get("storageQuotaBytes") != null) {
                quotaBytes = Long.valueOf(request.get("storageQuotaBytes").toString());
            } else if (request.get("storageQuotaGb") != null) {
                double gb = Double.parseDouble(request.get("storageQuotaGb").toString());
                quotaBytes = (long) (gb * 1024 * 1024 * 1024);
            }

            if (quotaBytes == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "storageQuotaBytes or storageQuotaGb is required"));
            }

            User updatedUser = userService.updateStorageQuota(id, quotaBytes);
            return ResponseEntity.ok(Map.of(
                "message", "Storage quota updated",
                "userId", updatedUser.getId(),
                "storageQuotaBytes", updatedUser.getStorageQuota()
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/quota")
    public ResponseEntity<?> updateAllUserQuota(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        ResponseEntity<?> authResponse = requireAdmin(session);
        if (authResponse != null) return authResponse;

        try {
            Long quotaBytes = null;
            if (request.get("storageQuotaBytes") != null) {
                quotaBytes = Long.valueOf(request.get("storageQuotaBytes").toString());
            } else if (request.get("storageQuotaGb") != null) {
                double gb = Double.parseDouble(request.get("storageQuotaGb").toString());
                quotaBytes = (long) (gb * 1024 * 1024 * 1024);
            }

            if (quotaBytes == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "storageQuotaBytes or storageQuotaGb is required"));
            }

            int updated = userService.updateAllUserQuotas(quotaBytes);
            return ResponseEntity.ok(Map.of(
                "message", "Storage quota updated for all users",
                "storageQuotaBytes", quotaBytes,
                "updatedUsers", updated
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getSystemStats(
            @RequestParam(value = "top", defaultValue = "5") int top,
            HttpSession session) {

        ResponseEntity<?> authResponse = requireAdmin(session);
        if (authResponse != null) return authResponse;

        Map<String, Object> stats = userService.getSystemStats(Math.max(1, Math.min(20, top)));
        return ResponseEntity.ok(stats);
    }

    private ResponseEntity<?> requireAdmin(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Not authenticated"));
        }
        if (sessionUser.getRole() != User.Role.ROLE_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Admin privileges required"));
        }
        return null;
    }
}
