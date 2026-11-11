package com.vybe.service;

import com.vybe.entity.*;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reports;
    private final VideoService videos;

    public ReportService(ReportRepository reports, VideoService videos) {
        this.reports = reports;
        this.videos = videos;
    }

    @Transactional
    public Report create(User reporter, Video video, ReportReason reason, String details) {
        if (reason == null) throw new BadRequestException("Укажите причину жалобы");
        if (details != null && details.length() > 1000) throw new BadRequestException("Описание не длиннее 1000 символов");
        Report r = new Report();
        r.setReporter(reporter);
        r.setVideo(video);
        r.setReason(reason);
        r.setDetails(details == null || details.isBlank() ? null : details.trim());
        return reports.save(r);
    }

    public List<Report> list(ReportStatus status) {
        return status == null ? reports.findAllByOrderByCreatedAtDesc() : reports.findByStatusOrderByCreatedAtDesc(status);
    }

    public long openCount() {
        return reports.countByStatus(ReportStatus.OPEN);
    }

    @Transactional
    public void dismiss(Long id) {
        Report r = reports.findById(id).orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
        r.setStatus(ReportStatus.DISMISSED);
        r.setResolvedAt(LocalDateTime.now());
    }

    /** Removes the reported video (all its reports are deleted together with it). */
    @Transactional
    public void removeContent(Long id) {
        Report r = reports.findById(id).orElseThrow(() -> new NotFoundException("Жалоба не найдена"));
        videos.delete(r.getVideo());
    }
}
