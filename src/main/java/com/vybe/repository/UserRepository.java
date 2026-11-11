package com.vybe.repository;

import com.vybe.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByCreatedAtAfter(LocalDateTime since);

    @Query("select u from User u where lower(u.username) like :q or lower(u.displayName) like :q or lower(u.email) like :q order by u.createdAt desc")
    Page<User> search(@Param("q") String q, Pageable pageable);

    @Query("select u from User u where u.enabled = true and (lower(u.username) like :q or lower(u.displayName) like :q) order by u.subscriberCount desc")
    List<User> searchChannels(@Param("q") String q, Pageable pageable);

    @Query("select u.displayName from User u where u.enabled = true and lower(u.displayName) like :q order by u.subscriberCount desc")
    List<String> suggestNames(@Param("q") String q, Pageable pageable);
}
