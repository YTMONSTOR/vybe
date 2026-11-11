package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "playlist_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_playlist_video", columnNames = {"playlist_id", "video_id"}))
@Getter
@Setter
public class PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id")
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id")
    private Video video;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();
}
