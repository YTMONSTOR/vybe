package com.vybe.repository;

import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.WatchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    Optional<WatchHistory> findByUserAndVideo(User user, Video video);

    Page<WatchHistory> findByUserOrderByWatchedAtDesc(User user, Pageable pageable);

    List<WatchHistory> findTop100ByUserOrderByWatchedAtDesc(User user);

    @Query("select h from WatchHistory h where h.user = :user and h.positionSeconds > 5 " +
            "and h.video.durationSeconds > 0 and h.positionSeconds < h.video.durationSeconds * 0.92 " +
            "and h.video.visibility <> com.vybe.entity.Visibility.PRIVATE order by h.watchedAt desc")
    List<WatchHistory> continueWatching(@Param("user") User user, Pageable pageable);

    @Query("select h.video.id from WatchHistory h where h.user = :user")
    List<Long> watchedIds(@Param("user") User user);

    @Modifying
    @Query("delete from WatchHistory h where h.user = :user")
    void deleteByUser(@Param("user") User user);

    @Modifying
    @Query("delete from WatchHistory h where h.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
