package com.vybe.repository;

import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.VideoReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoReactionRepository extends JpaRepository<VideoReaction, Long> {

    Optional<VideoReaction> findByUserAndVideo(User user, Video video);

    @Query("select r.video from VideoReaction r where r.user = :user and r.type = com.vybe.entity.ReactionType.LIKE order by r.createdAt desc")
    List<Video> likedVideos(@Param("user") User user);

    @Modifying
    @Query("delete from VideoReaction r where r.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
