package com.vybe.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A playlist (ordered list to watch in sequence) or a collection
 * (a visual board for organising videos by theme). Both share one table.
 */
@Entity
@Table(name = "playlists")
@Getter
@Setter
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean collection = false;

    @Column(nullable = false)
    private boolean publicVisible = true;

    @Column(nullable = false, length = 9)
    private String color = "#8b5cf6";

    @Column(nullable = false, length = 40)
    private String icon = "layers";

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PlaylistItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getCoverUrl() {
        for (PlaylistItem i : items) {
            return i.getVideo().getThumbnailUrl();
        }
        return null;
    }
}
