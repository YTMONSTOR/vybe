package com.vybe.repository;

import com.vybe.entity.Category;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByVisibilityOrderByCreatedAtDesc(Visibility visibility, Pageable pageable);

    List<Video> findByVisibilityOrderByViewsDesc(Visibility visibility, Pageable pageable);

    List<Video> findTop500ByVisibilityOrderByCreatedAtDesc(Visibility visibility);

    List<Video> findByCategoryAndVisibilityOrderByCreatedAtDesc(Category category, Visibility visibility, Pageable pageable);

    List<Video> findByCategoryAndVisibilityOrderByViewsDesc(Category category, Visibility visibility, Pageable pageable);

    List<Video> findByAuthorAndVisibilityOrderByCreatedAtDesc(User author, Visibility visibility);

    List<Video> findByAuthorAndVisibilityOrderByViewsDesc(User author, Visibility visibility);

    List<Video> findByAuthorOrderByCreatedAtDesc(User author);

    List<Video> findByIdIn(Collection<Long> ids);

    long countByAuthor(User author);

    long countByAuthorAndVisibility(User author, Visibility visibility);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("select v from Video v where v.author in :authors and v.visibility = com.vybe.entity.Visibility.PUBLIC order by v.createdAt desc")
    List<Video> feedFor(@Param("authors") Collection<User> authors, Pageable pageable);

    @Query("select distinct v from Video v left join v.tags t " +
            "where v.visibility = com.vybe.entity.Visibility.PUBLIC and (" +
            "lower(v.title) like :q or lower(coalesce(v.description, '')) like :q or lower(t.name) like :q " +
            "or lower(v.author.displayName) like :q or lower(v.author.username) like :q)")
    List<Video> searchByTerm(@Param("q") String q, Pageable pageable);

    @Query("select v.title from Video v where v.visibility = com.vybe.entity.Visibility.PUBLIC and lower(v.title) like :q order by v.views desc")
    List<String> suggestTitles(@Param("q") String q, Pageable pageable);

    @Query("select v from Video v where lower(v.title) like :q or lower(v.author.username) like :q order by v.createdAt desc")
    Page<Video> adminSearch(@Param("q") String q, Pageable pageable);

    Page<Video> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select coalesce(sum(v.views), 0) from Video v where v.author = :author")
    long sumViewsByAuthor(@Param("author") User author);

    @Query("select coalesce(sum(v.likes), 0) from Video v where v.author = :author")
    long sumLikesByAuthor(@Param("author") User author);

    @Query("select coalesce(sum(v.commentCount), 0) from Video v where v.author = :author")
    long sumCommentsByAuthor(@Param("author") User author);

    @Query("select coalesce(sum(v.views), 0) from Video v")
    long sumAllViews();

    @Query("select coalesce(sum(v.fileSize), 0) from Video v")
    long sumStorage();

    @Modifying
    @Query("update Video v set v.views = v.views + 1 where v.id = :id")
    void incrementViews(@Param("id") Long id);

    @Modifying
    @Query("update Video v set v.category = null where v.category = :category")
    void clearCategory(@Param("category") Category category);
}
