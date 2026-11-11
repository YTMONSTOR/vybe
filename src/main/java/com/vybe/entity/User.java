package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username", unique = true),
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 60)
    private String displayName;

    @Column(length = 1000)
    private String bio;

    private String avatarPath;

    private String bannerPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private long subscriberCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public String getInitials() {
        String n = displayName == null || displayName.isBlank() ? username : displayName.trim();
        String[] parts = n.split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return n.substring(0, Math.min(2, n.length())).toUpperCase();
    }

    /** Stable hue for gradient avatars. */
    public int getHue() {
        return (int) (Math.abs((long) (username == null ? 0 : username.hashCode())) % 360);
    }

    public String getAvatarUrl() {
        return avatarPath == null ? null : "/media/" + avatarPath;
    }

    public String getBannerUrl() {
        return bannerPath == null ? null : "/media/" + bannerPath;
    }
}
