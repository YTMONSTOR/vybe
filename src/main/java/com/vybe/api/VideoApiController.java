package com.vybe.api;

import com.vybe.dto.ApiDtos.*;
import com.vybe.dto.VideoForm;
import com.vybe.entity.*;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.ForbiddenException;
import com.vybe.repository.WatchHistoryRepository;
import com.vybe.service.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/videos")
public class VideoApiController {

    private final VideoService videos;
    private final UserService users;
    private final CommentService comments;
    private final PlaylistService playlists;
    private final ReportService reports;
    private final StatsService stats;
    private final WatchHistoryRepository history;
    private final Fmt fmt;

    public VideoApiController(VideoService videos, UserService users, CommentService comments,
                              PlaylistService playlists, ReportService reports, StatsService stats,
                              WatchHistoryRepository history, Fmt fmt) {
        this.videos = videos;
        this.users = users;
        this.comments = comments;
        this.playlists = playlists;
        this.reports = reports;
        this.stats = stats;
        this.history = history;
        this.fmt = fmt;
    }

    public record ReactRequest(@NotNull ReactionType type) {}

    public record ProgressRequest(int position) {}

    public record CommentRequest(@NotNull @Size(min = 1, max = 2000) String text, Long parentId) {}

    public record ReportRequest(@NotNull ReportReason reason, @Size(max = 1000) String details) {}

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@Valid @ModelAttribute VideoForm form, BindingResult binding,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                                    @RequestParam(value = "autoThumbnail", required = false) MultipartFile autoThumbnail) {
        User me = users.requireCurrent();
        if (binding.hasErrors()) {
            FieldError fe = binding.getFieldError();
            throw new BadRequestException(fe == null ? "Проверьте поля формы" : fe.getDefaultMessage());
        }
        Video v = videos.upload(me, form, file, thumbnail, autoThumbnail);
        return ResponseEntity.ok(Map.of("id", v.getId(), "url", "/watch/" + v.getId(),
                "editUrl", "/studio/videos/" + v.getId()));
    }

    @PostMapping("/{id}/react")
    public ReactionState react(@PathVariable Long id, @Valid @RequestBody ReactRequest req) {
        return videos.react(users.requireCurrent(), id, req.type());
    }

    /** Counts a view once per session per video. */
    @PostMapping("/{id}/view")
    public Map<String, Object> view(@PathVariable Long id, HttpSession session) {
        User me = users.current().orElse(null);
        Video v = videos.getViewable(id, me);
        @SuppressWarnings("unchecked")
        Set<Long> seen = (Set<Long>) session.getAttribute("vybe.viewed");
        if (seen == null) {
            seen = Collections.synchronizedSet(new HashSet<>());
            session.setAttribute("vybe.viewed", seen);
        }
        long views = v.getViews();
        boolean counted = false;
        if (seen.add(id)) {
            views = videos.registerView(v, me);
            counted = true;
        }
        if (me != null && history.findByUserAndVideo(me, v).isEmpty()) {
            videos.saveProgress(me, v, 0);
        }
        return Map.of("views", views, "label", fmt.views(views), "counted", counted);
    }

    @PostMapping("/{id}/progress")
    public ResponseEntity<Void> progress(@PathVariable Long id, @RequestBody ProgressRequest req) {
        User me = users.requireCurrent();
        videos.saveProgress(me, videos.getViewable(id, me), req.position());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/favorite")
    public ToggleState favorite(@PathVariable Long id) {
        return new ToggleState(videos.toggleFavorite(users.requireCurrent(), id), 0);
    }

    @GetMapping("/{id}/playlists")
    public List<PlaylistState> playlists(@PathVariable Long id) {
        User me = users.requireCurrent();
        return playlists.states(me, videos.getViewable(id, me));
    }

    @PostMapping("/{id}/playlists/{playlistId}")
    public ToggleState togglePlaylist(@PathVariable Long id, @PathVariable Long playlistId) {
        User me = users.requireCurrent();
        boolean added = playlists.toggle(me, playlistId, videos.getViewable(id, me));
        return new ToggleState(added, 0);
    }

    @PostMapping("/{id}/report")
    public Message report(@PathVariable Long id, @Valid @RequestBody ReportRequest req) {
        User me = users.requireCurrent();
        Video v = videos.getViewable(id, me);
        if (v.getAuthor().getId().equals(me.getId())) throw new BadRequestException("Нельзя пожаловаться на своё видео");
        reports.create(me, v, req.reason(), req.details());
        return new Message("Спасибо! Модераторы рассмотрят жалобу.");
    }

    @GetMapping("/{id}/comments")
    public Map<String, Object> comments(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) {
        User me = users.current().orElse(null);
        Video v = videos.getViewable(id, me);
        int size = 20;
        List<CommentDto> list = comments.roots(v, Math.max(0, page), size).stream()
                .map(c -> CommentDto.of(c, me, fmt)).toList();
        return Map.of("items", list, "hasMore", list.size() == size, "total", v.getCommentCount(),
                "enabled", v.isCommentsEnabled());
    }

    @PostMapping("/{id}/comments")
    public CommentDto addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        User me = users.requireCurrent();
        Video v = videos.getViewable(id, me);
        return CommentDto.of(comments.add(me, v, req.text(), req.parentId()), me, fmt);
    }

    @GetMapping("/{id}/stats")
    public Series stats(@PathVariable Long id, @RequestParam(defaultValue = "30") int days) {
        User me = users.requireCurrent();
        Video v = videos.get(id);
        if (!videos.canEdit(me, v)) throw new ForbiddenException("Нет доступа");
        return stats.videoViews(v, clampDays(days));
    }

    static int clampDays(int days) {
        return Math.max(7, Math.min(days, 365));
    }
}
