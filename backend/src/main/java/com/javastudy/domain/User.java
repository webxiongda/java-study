package com.javastudy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    public User(String username, String secretHash) {
        this.username = username;
        this.secretHash = secretHash;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void updateSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
