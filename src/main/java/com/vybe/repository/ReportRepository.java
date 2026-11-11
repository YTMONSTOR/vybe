package com.vybe.repository;

import com.vybe.entity.Report;
import com.vybe.entity.ReportStatus;
import com.vybe.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDesc();

    long countByStatus(ReportStatus status);

    @Modifying
    @Query("delete from Report r where r.video = :video")
    void deleteByVideo(@Param("video") Video video);
}
