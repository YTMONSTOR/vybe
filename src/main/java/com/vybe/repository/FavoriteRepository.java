package com.vybe.repository;

import com.vybe.entity.Favorite;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserAndVideo(User user, Video video);

    boolean existsByUserAndVideo(User user, Video video);

    List<Favorite> findByUserOrderByCreatedAtDesc(User user);

    @Modifying
    @Query("delete from Favorite f where f.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
