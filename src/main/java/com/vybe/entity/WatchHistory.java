package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "watch_history", uniqueConstraints =
        @UniqueConstraint(name = "uk_history_user_video", columnNames = {"user_id", "video_id"}))
@Getter
@Setter
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private Video video;

    /** Last playback position in seconds. */
    @Column(nullable = false)
    private int positionSeconds = 0;

    @Column(nullable = false)
    private LocalDateTime watchedAt = LocalDateTime.now();

    public int getProgressPercent() {
        int d = video == null ? 0 : video.getDurationSeconds();
        if (d <= 0) return 0;
        return Math.min(100, (int) Math.round(positionSeconds * 100.0 / d));
    }
}
