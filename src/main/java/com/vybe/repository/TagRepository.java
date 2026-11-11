package com.vybe.repository;

import com.vybe.entity.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    @Query("select t.name from Tag t where lower(t.name) like :q order by t.name")
    List<String> suggest(@Param("q") String q, Pageable pageable);
}
