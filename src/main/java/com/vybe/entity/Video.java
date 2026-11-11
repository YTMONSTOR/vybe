package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "videos", indexes = {
        @Index(name = "idx_videos_author", columnList = "author_id"),
        @Index(name = "idx_videos_created", columnList = "created_at"),
        @Index(name = "idx_videos_category", columnList = "category_id")
})
@Getter
@Setter
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String filePath;

    private String thumbnailPath;

    @Column(length = 100)
    private String contentType;

    private long fileSize;

    private int durationSeconds;

    @Column(nullable = false)
    private long views = 0;

    @Column(nullable = false)
    private long likes = 0;

    @Column(nullable = false)
    private long dislikes = 0;

    @Column(nullable = false)
    private long commentCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private boolean commentsEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id")
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToMany
    @JoinTable(name = "video_tags",
            joinColumns = @JoinColumn(name = "video_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "video", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startSeconds ASC")
    private List<Chapter> chapters = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getVideoUrl() {
        return "/media/" + filePath;
    }

    public String getThumbnailUrl() {
        return thumbnailPath == null ? "/img/placeholder.svg" : "/media/" + thumbnailPath;
    }

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }

    public String getTagsAsString() {
        StringBuilder sb = new StringBuilder();
        for (Tag t : tags) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.getName());
        }
        return sb.toString();
    }

    public int getLikeRatio() {
        long total = likes + dislikes;
        return total == 0 ? 100 : (int) Math.round(likes * 100.0 / total);
    }
}
