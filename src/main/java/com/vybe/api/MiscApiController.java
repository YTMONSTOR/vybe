package com.vybe.api;

import com.vybe.dto.ApiDtos.*;
import com.vybe.dto.ScoredVideo;
import com.vybe.entity.Playlist;
import com.vybe.entity.User;
import com.vybe.repository.UserRepository;
import com.vybe.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Subscriptions, playlists, search suggestions, Smart Feed paging and chart data. */
@RestController
@RequestMapping("/api")
public class MiscApiController {

    private final UserService users;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptions;
    private final PlaylistService playlists;
    private final SearchService search;
    private final RecommendationService recommendations;
    private final StatsService stats;
    private final Fmt fmt;

    public MiscApiController(UserService users, UserRepository userRepository, SubscriptionService subscriptions,
                             PlaylistService playlists, SearchService search,
                             RecommendationService recommendations, StatsService stats, Fmt fmt) {
        this.users = users;
        this.userRepository = userRepository;
        this.subscriptions = subscriptions;
        this.playlists = playlists;
        this.search = search;
        this.recommendations = recommendations;
        this.stats = stats;
        this.fmt = fmt;
    }

    public record PlaylistRequest(@NotBlank @Size(max = 80) String title, boolean collection, boolean publicVisible,
                                  String color, String icon) {}

    @PostMapping("/channels/{id}/subscribe")
    public java.util.Map<String, Object> subscribe(@PathVariable Long id) {
        boolean active = subscriptions.toggle(users.requireCurrent(), id);
        long count = userRepository.findById(id).map(User::getSubscriberCount).orElse(0L);
        return java.util.Map.of("active", active, "count", count, "label", fmt.subscribers(count));
    }

    @PostMapping("/playlists")
    public PlaylistState createPlaylist(@Valid @RequestBody PlaylistRequest req) {
        User me = users.requireCurrent();
        Playlist p = playlists.save(me, null, req.title(), null, req.collection(), req.publicVisible(),
                req.color(), req.icon());
        return new PlaylistState(p.getId(), p.getTitle(), p.isCollection(), false, 0, p.getColor(), p.getIcon());
    }

    @GetMapping("/search/suggest")
    public List<Suggestion> suggest(@RequestParam(defaultValue = "") String q) {
        return search.suggest(q);
    }

    @GetMapping("/feed")
    public List<VideoCard> feed(@RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "12") int limit) {
        User me = users.current().orElse(null);
        List<ScoredVideo> page = recommendations.smartFeed(me, Math.max(0, offset), Math.max(1, Math.min(limit, 36)));
        return page.stream().map(s -> VideoCard.of(s.video(), fmt, s.reason(), 0)).toList();
    }

    @GetMapping("/studio/views")
    public Series studioViews(@RequestParam(defaultValue = "30") int days) {
        return stats.creatorViews(users.requireCurrent(), VideoApiController.clampDays(days));
    }

    @GetMapping("/studio/subscribers")
    public Series studioSubscribers(@RequestParam(defaultValue = "30") int days) {
        return stats.creatorSubscribers(users.requireCurrent(), VideoApiController.clampDays(days));
    }

    @GetMapping("/admin/views")
    public Series adminViews(@RequestParam(defaultValue = "30") int days) {
        return stats.platformViews(VideoApiController.clampDays(days));
    }
}
