package com.vybe.repository;

import com.vybe.entity.Comment;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByVideoAndParentIsNullOrderByPinnedDescCreatedAtDesc(Video video, Pageable pageable);

    List<Comment> findByParentOrderByCreatedAtAsc(Comment parent);

    long countByVideo(Video video);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("select c from Comment c where c.video.author = :author order by c.createdAt desc")
    Page<Comment> findForCreator(@Param("author") User author, Pageable pageable);

    @Query("select c from Comment c where c.video.author = :author and c.video.id = :videoId order by c.createdAt desc")
    Page<Comment> findForCreatorVideo(@Param("author") User author, @Param("videoId") Long videoId, Pageable pageable);

    @Modifying
    @Query("update Comment c set c.pinned = false where c.video = :video")
    void unpinAll(@Param("video") Video video);

    @Modifying
    @Query("delete from Comment c where c.parent = :parent")
    int deleteReplies(@Param("parent") Comment parent);

    @Modifying
    @Query("delete from Comment c where c.video = :video and c.parent is not null")
    void deleteRepliesByVideo(@Param("video") Video video);

    @Modifying
    @Query("delete from Comment c where c.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
