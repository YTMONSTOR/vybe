package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One counted view — used for trending and creator analytics. */
@Entity
@Table(name = "view_events", indexes = {
        @Index(name = "idx_view_events_video_time", columnList = "video_id, created_at"),
        @Index(name = "idx_view_events_time", columnList = "created_at")
})
@Getter
@Setter
public class ViewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private Video video;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
