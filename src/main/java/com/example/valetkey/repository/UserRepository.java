package com.example.valetkey.repository;

import com.example.valetkey.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>   {

    Optional<User> findUserByUsername(String username);

    Optional<User> findUserByEmail(String email);

    List<User> findAll();

    User getUserById(Long id);

    @Query("SELECT COALESCE(SUM(u.storageUsed), 0) FROM User u")
    Long getTotalStorageUsed();

    @Query("SELECT COALESCE(SUM(u.storageQuota), 0) FROM User u")
    Long getTotalStorageQuota();
}
