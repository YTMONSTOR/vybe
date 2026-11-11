package com.vybe.repository;

import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.ViewEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ViewEventRepository extends JpaRepository<ViewEvent, Long> {

    @Query("select e.video.id, count(e) from ViewEvent e where e.createdAt > :since " +
            "and e.video.visibility = com.vybe.entity.Visibility.PUBLIC group by e.video.id order by count(e) desc")
    List<Object[]> trending(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("select e.createdAt from ViewEvent e where e.video.author = :author and e.createdAt > :since")
    List<LocalDateTime> viewTimesForAuthor(@Param("author") User author, @Param("since") LocalDateTime since);

    @Query("select e.createdAt from ViewEvent e where e.video = :video and e.createdAt > :since")
    List<LocalDateTime> viewTimesForVideo(@Param("video") Video video, @Param("since") LocalDateTime since);

    @Query("select e.createdAt from ViewEvent e where e.createdAt > :since")
    List<LocalDateTime> viewTimesSince(@Param("since") LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);

    @Modifying
    @Query("delete from ViewEvent e where e.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
