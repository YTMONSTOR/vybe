package com.vybe.service;

import com.vybe.dto.ApiDtos.ReactionState;
import com.vybe.dto.VideoForm;
import com.vybe.entity.*;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.ForbiddenException;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoService.class);

    private final VideoRepository videos;
    private final TagRepository tags;
    private final CategoryRepository categories;
    private final VideoReactionRepository reactions;
    private final WatchHistoryRepository history;
    private final FavoriteRepository favorites;
    private final PlaylistItemRepository playlistItems;
    private final ViewEventRepository viewEvents;
    private final CommentRepository comments;
    private final ReportRepository reports;
    private final SubscriptionRepository subscriptions;
    private final StorageService storage;
    private final MediaService media;

    public VideoService(VideoRepository videos, TagRepository tags, CategoryRepository categories,
                        VideoReactionRepository reactions, WatchHistoryRepository history,
                        FavoriteRepository favorites, PlaylistItemRepository playlistItems,
                        ViewEventRepository viewEvents, CommentRepository comments, ReportRepository reports,
                        SubscriptionRepository subscriptions, StorageService storage, MediaService media) {
        this.videos = videos;
        this.tags = tags;
        this.categories = categories;
        this.reactions = reactions;
        this.history = history;
        this.favorites = favorites;
        this.playlistItems = playlistItems;
        this.viewEvents = viewEvents;
        this.comments = comments;
        this.reports = reports;
        this.subscriptions = subscriptions;
        this.storage = storage;
        this.media = media;
    }

    // ------------------------------------------------------------------ lookup

    public Video get(Long id) {
        return videos.findById(id).orElseThrow(() -> new NotFoundException("Видео не найдено"));
    }

    /** Returns the video if the viewer may watch it (private videos only for the author and admins). */
    public Video getViewable(Long id, User viewer) {
        Video v = get(id);
        if (v.getVisibility() == Visibility.PRIVATE && !canEdit(viewer, v)) {
            throw new NotFoundException("Видео не найдено или доступ ограничен");
        }
        return v;
    }

    public Video getEditable(Long id, User user) {
        Video v = get(id);
        if (!canEdit(user, v)) throw new ForbiddenException("Нет доступа к этому видео");
        return v;
    }

    public boolean canEdit(User user, Video v) {
        return user != null && (user.isAdmin() || v.getAuthor().getId().equals(user.getId()));
    }

    // ------------------------------------------------------------------ lists

    public List<Video> latest(int limit) {
        return videos.findByVisibilityOrderByCreatedAtDesc(Visibility.PUBLIC, PageRequest.of(0, limit));
    }

    public List<Video> latest(int page, int size) {
        return videos.findByVisibilityOrderByCreatedAtDesc(Visibility.PUBLIC, PageRequest.of(page, size));
    }

    public List<Video> popular(int limit) {
        return videos.findByVisibilityOrderByViewsDesc(Visibility.PUBLIC, PageRequest.of(0, limit));
    }

    public List<Video> byCategory(Category c, String sort, int limit) {
        if ("popular".equals(sort)) {
            return videos.findByCategoryAndVisibilityOrderByViewsDesc(c, Visibility.PUBLIC, PageRequest.of(0, limit));
        }
        return videos.findByCategoryAndVisibilityOrderByCreatedAtDesc(c, Visibility.PUBLIC, PageRequest.of(0, limit));
    }

    public List<Video> channelVideos(User channel, boolean includeAll, String sort) {
        if (includeAll) return videos.findByAuthorOrderByCreatedAtDesc(channel);
        if ("popular".equals(sort)) return videos.findByAuthorAndVisibilityOrderByViewsDesc(channel, Visibility.PUBLIC);
        return videos.findByAuthorAndVisibilityOrderByCreatedAtDesc(channel, Visibility.PUBLIC);
    }

    public List<Video> subscriptionFeed(User user, int limit) {
        List<User> channels = subscriptions.channelsOf(user);
        if (channels.isEmpty()) return List.of();
        return videos.feedFor(channels, PageRequest.of(0, limit));
    }

    /** Trending: most counted views in the last {@code days}; falls back to all-time popular. */
    @Transactional(readOnly = true)
    public List<Video> trending(int days, int limit) {
        List<Object[]> rows = viewEvents.trending(LocalDateTime.now().minusDays(days), PageRequest.of(0, limit));
        List<Long> ids = rows.stream().map(r -> (Long) r[0]).toList();
        Map<Long, Video> byId = videos.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Video::getId, v -> v));
        List<Video> result = new ArrayList<>();
        for (Long id : ids) {
            Video v = byId.get(id);
            if (v != null) result.add(v);
        }
        if (result.size() < limit) {
            Set<Long> have = new HashSet<>(ids);
            for (Video v : popular(limit * 2)) {
                if (result.size() >= limit) break;
                if (have.add(v.getId())) result.add(v);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ upload / edit

    @Transactional
    public Video upload(User author, VideoForm form, MultipartFile file, MultipartFile thumbnail,
                        MultipartFile autoThumbnail) {
        if (!storage.hasContent(file)) throw new BadRequestException("Выберите видеофайл");
        String videoPath = storage.storeVideo(file);
        try {
            Video v = new Video();
            v.setAuthor(author);
            v.setFilePath(videoPath);
            v.setContentType(file.getContentType());
            v.setFileSize(file.getSize());
            Path abs = storage.resolve(videoPath);
            int duration = media.probeDuration(abs).orElse(form.getDuration() == null ? 0 : Math.max(0, form.getDuration()));
            v.setDurationSeconds(duration);
            applyForm(v, form);
            v.setThumbnailPath(resolveThumbnail(v, abs, thumbnail, autoThumbnail));
            return videos.save(v);
        } catch (RuntimeException e) {
            storage.delete(videoPath);
            throw e;
        }
    }

    private String resolveThumbnail(Video v, Path videoFile, MultipartFile custom, MultipartFile auto) {
        if (storage.hasContent(custom)) return storage.storeImage(custom, "thumbs");
        String rel = storage.newRelativePath("thumbs", "jpg");
        double at = v.getDurationSeconds() > 0 ? Math.min(v.getDurationSeconds() * 0.2, 30) : 1;
        if (media.extractFrame(videoFile, at, storage.resolve(rel))) return rel;
        if (storage.hasContent(auto)) {
            try {
                return storage.storeImage(auto, "thumbs");
            } catch (BadRequestException e) {
                log.debug("Client thumbnail rejected: {}", e.getMessage());
            }
        }
        media.generateArtwork(v.getTitle(), System.nanoTime(), storage.resolve(rel));
        return rel;
    }

    @Transactional
    public Video update(Video v, VideoForm form, MultipartFile thumbnail, boolean regenerateThumbnail) {
        applyForm(v, form);
        if (storage.hasContent(thumbnail)) {
            String old = v.getThumbnailPath();
            v.setThumbnailPath(storage.storeImage(thumbnail, "thumbs"));
            storage.delete(old);
        } else if (regenerateThumbnail) {
            String old = v.getThumbnailPath();
            v.setThumbnailPath(resolveThumbnail(v, storage.resolve(v.getFilePath()), null, null));
            storage.delete(old);
        }
        return v;
    }

    private void applyForm(Video v, VideoForm form) {
        v.setTitle(form.getTitle().trim());
        v.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        v.setVisibility(form.getVisibility() == null ? Visibility.PUBLIC : form.getVisibility());
        v.setCommentsEnabled(form.isCommentsEnabled());
        v.setCategory(form.getCategoryId() == null ? null : categories.findById(form.getCategoryId()).orElse(null));
        v.getTags().clear();
        v.getTags().addAll(resolveTags(form.getTags()));
        v.getChapters().clear();
        v.getChapters().addAll(ChapterParser.parse(v.getDescription(), v.getDurationSeconds(), v));
    }

    private Set<Tag> resolveTags(String csv) {
        Set<Tag> result = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return result;
        for (String raw : csv.split("[,;\\n]")) {
            String name = raw.trim().replaceFirst("^#+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (name.isEmpty() || name.length() > 40) continue;
            Tag tag = tags.findByNameIgnoreCase(name).orElseGet(() -> tags.save(new Tag(name)));
            result.add(tag);
            if (result.size() >= 15) break;
        }
        return result;
    }

    @Transactional
    public void delete(Video v) {
        reactions.deleteByVideo(v);
        history.deleteByVideo(v);
        favorites.deleteByVideo(v);
        playlistItems.deleteByVideo(v);
        viewEvents.deleteByVideo(v);
        reports.deleteByVideo(v);
        comments.unpinAll(v);
        comments.deleteRepliesByVideo(v);
        comments.deleteByVideo(v);
        String file = v.getFilePath();
        String thumb = v.getThumbnailPath();
        videos.delete(v);
        videos.flush();
        storage.delete(file);
        storage.delete(thumb);
    }

    // ------------------------------------------------------------------ interactions

    @Transactional
    public ReactionState react(User user, Long videoId, ReactionType type) {
        Video v = getViewable(videoId, user);
        Optional<VideoReaction> existing = reactions.findByUserAndVideo(user, v);
        String state;
        if (existing.isPresent()) {
            VideoReaction r = existing.get();
            adjust(v, r.getType(), -1);
            if (r.getType() == type) {
                reactions.delete(r);
                state = "NONE";
            } else {
                r.setType(type);
                adjust(v, type, 1);
                state = type.name();
            }
        } else {
            VideoReaction r = new VideoReaction();
            r.setUser(user);
            r.setVideo(v);
            r.setType(type);
            reactions.save(r);
            adjust(v, type, 1);
            state = type.name();
        }
        return new ReactionState(state, v.getLikes(), v.getDislikes());
    }

    private void adjust(Video v, ReactionType type, int delta) {
        if (type == ReactionType.LIKE) v.setLikes(Math.max(0, v.getLikes() + delta));
        else v.setDislikes(Math.max(0, v.getDislikes() + delta));
    }

    public String reactionOf(User user, Video v) {
        if (user == null) return "NONE";
        return reactions.findByUserAndVideo(user, v).map(r -> r.getType().name()).orElse("NONE");
    }

    @Transactional
    public long registerView(Video v, User viewer) {
        videos.incrementViews(v.getId());
        ViewEvent e = new ViewEvent();
        e.setVideo(v);
        e.setUserId(viewer == null ? null : viewer.getId());
        viewEvents.save(e);
        return v.getViews() + 1;
    }

    @Transactional
    public void saveProgress(User user, Video v, int position) {
        WatchHistory h = history.findByUserAndVideo(user, v).orElseGet(() -> {
            WatchHistory n = new WatchHistory();
            n.setUser(user);
            n.setVideo(v);
            return n;
        });
        int max = v.getDurationSeconds() > 0 ? v.getDurationSeconds() : Integer.MAX_VALUE;
        h.setPositionSeconds(Math.max(0, Math.min(position, max)));
        h.setWatchedAt(LocalDateTime.now());
        history.save(h);
    }

    public int resumePosition(User user, Video v) {
        if (user == null) return 0;
        return history.findByUserAndVideo(user, v)
                .filter(h -> h.getPositionSeconds() > 5 && (v.getDurationSeconds() == 0
                        || h.getPositionSeconds() < v.getDurationSeconds() * 0.92))
                .map(WatchHistory::getPositionSeconds).orElse(0);
    }

    @Transactional
    public boolean toggleFavorite(User user, Long videoId) {
        Video v = getViewable(videoId, user);
        Optional<Favorite> f = favorites.findByUserAndVideo(user, v);
        if (f.isPresent()) {
            favorites.delete(f.get());
            return false;
        }
        Favorite n = new Favorite();
        n.setUser(user);
        n.setVideo(v);
        favorites.save(n);
        return true;
    }

    public boolean isFavorite(User user, Video v) {
        return user != null && favorites.existsByUserAndVideo(user, v);
    }

    public List<Video> favoritesOf(User user) {
        return favorites.findByUserOrderByCreatedAtDesc(user).stream().map(Favorite::getVideo)
                .filter(v -> v.getVisibility() != Visibility.PRIVATE || canEdit(user, v)).toList();
    }

    public List<Video> likedBy(User user) {
        return reactions.likedVideos(user).stream()
                .filter(v -> v.getVisibility() != Visibility.PRIVATE || canEdit(user, v)).toList();
    }
}
