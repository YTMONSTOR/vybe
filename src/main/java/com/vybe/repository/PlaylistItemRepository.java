package com.vybe.repository;

import com.vybe.entity.Playlist;
import com.vybe.entity.PlaylistItem;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {

    Optional<PlaylistItem> findByPlaylistAndVideo(Playlist playlist, Video video);

    @Query("select coalesce(max(i.position), 0) from PlaylistItem i where i.playlist = :playlist")
    int maxPosition(@Param("playlist") Playlist playlist);

    @Query("select i.playlist.id from PlaylistItem i where i.video = :video and i.playlist.owner = :owner")
    List<Long> playlistIdsContaining(@Param("video") Video video, @Param("owner") User owner);

    @Modifying
    @Query("delete from PlaylistItem i where i.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
