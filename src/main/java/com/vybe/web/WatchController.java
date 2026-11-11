package com.vybe.web;

import com.vybe.entity.Playlist;
import com.vybe.entity.ReportReason;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.exception.NotFoundException;
import com.vybe.service.*;
import com.vybe.ws.TogetherRoomService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class WatchController {

    private final VideoService videos;
    private final RecommendationService recommendations;
    private final SubscriptionService subscriptions;
    private final PlaylistService playlists;
    private final CategoryService categories;
    private final TogetherRoomService rooms;
    private final UserService users;

    public WatchController(VideoService videos, RecommendationService recommendations,
                           SubscriptionService subscriptions, PlaylistService playlists,
                           CategoryService categories, TogetherRoomService rooms, UserService users) {
        this.videos = videos;
        this.recommendations = recommendations;
        this.subscriptions = subscriptions;
        this.playlists = playlists;
        this.categories = categories;
        this.rooms = rooms;
        this.users = users;
    }

    @GetMapping("/watch/{id}")
    public String watch(@PathVariable Long id, @RequestParam(required = false) Integer t,
                        @RequestParam(required = false) Long list, Model model) {
        User me = (User) model.getAttribute("me");
        Video v = videos.getViewable(id, me);
        model.addAttribute("video", v);
        model.addAttribute("related", recommendations.related(v, 14));
        model.addAttribute("reaction", videos.reactionOf(me, v));
        model.addAttribute("favorite", videos.isFavorite(me, v));
        model.addAttribute("subscribed", subscriptions.isSubscribed(me, v.getAuthor()));
        model.addAttribute("canEdit", videos.canEdit(me, v));
        model.addAttribute("isAuthor", me != null && me.getId().equals(v.getAuthor().getId()));
        model.addAttribute("startAt", t != null ? Math.max(0, t) : videos.resumePosition(me, v));
        model.addAttribute("reportReasons", ReportReason.values());
        if (list != null) {
            try {
                Playlist p = playlists.getViewable(list, me);
                List<Video> items = playlists.videosOf(p, me);
                int idx = -1;
                for (int i = 0; i < items.size(); i++) if (items.get(i).getId().equals(v.getId())) idx = i;
                model.addAttribute("playlist", p);
                model.addAttribute("playlistVideos", items);
                model.addAttribute("playlistIndex", idx);
                model.addAttribute("nextVideo", idx >= 0 && idx + 1 < items.size() ? items.get(idx + 1) : null);
            } catch (NotFoundException ignored) {
                // playlist not available — just play the video
            }
        }
        if (!model.containsAttribute("nextVideo")) {
            List<Video> related = recommendations.related(v, 1);
            model.addAttribute("nextVideo", related.isEmpty() ? null : related.get(0));
        }
        Video next = (Video) model.getAttribute("nextVideo");
        Playlist current = (Playlist) model.getAttribute("playlist");
        model.addAttribute("nextUrl", next == null ? null
                : "/watch/" + next.getId() + (current != null ? "?list=" + current.getId() : ""));
        return "watch";
    }

    @GetMapping("/upload")
    public String upload(Model model) {
        users.requireCurrent();
        model.addAttribute("categories", categories.all());
        return "upload";
    }

    @PostMapping("/together")
    public String createRoom(@RequestParam Long videoId) {
        User me = users.requireCurrent();
        TogetherRoomService.Room room = rooms.create(me, videoId);
        return "redirect:/together/" + room.getCode();
    }

    @GetMapping("/together/{code}")
    public String room(@PathVariable String code, Model model) {
        TogetherRoomService.Room room = rooms.find(code)
                .orElseThrow(() -> new NotFoundException("Комната не найдена или уже закрыта"));
        model.addAttribute("room", room);
        model.addAttribute("suggestions", videos.trending(30, 12));
        return "together";
    }
}
