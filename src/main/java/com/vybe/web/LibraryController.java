package com.vybe.web;

import com.vybe.entity.Playlist;
import com.vybe.entity.User;
import com.vybe.exception.BadRequestException;
import com.vybe.repository.WatchHistoryRepository;
import com.vybe.service.PlaylistService;
import com.vybe.service.SubscriptionService;
import com.vybe.service.UserService;
import com.vybe.service.VideoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LibraryController {

    private final UserService users;
    private final VideoService videos;
    private final PlaylistService playlists;
    private final SubscriptionService subscriptions;
    private final WatchHistoryRepository history;

    public LibraryController(UserService users, VideoService videos, PlaylistService playlists,
                             SubscriptionService subscriptions, WatchHistoryRepository history) {
        this.users = users;
        this.videos = videos;
        this.playlists = playlists;
        this.subscriptions = subscriptions;
        this.history = history;
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "0") int page, Model model) {
        User me = users.requireCurrent();
        model.addAttribute("entries", history.findByUserOrderByWatchedAtDesc(me, PageRequest.of(Math.max(0, page), 40)));
        return "library/history";
    }

    @PostMapping("/history/clear")
    @Transactional
    public String clearHistory(RedirectAttributes ra) {
        history.deleteByUser(users.requireCurrent());
        ra.addFlashAttribute("toast", "История просмотров очищена");
        return "redirect:/history";
    }

    @PostMapping("/history/{id}/remove")
    @Transactional
    public String removeHistory(@PathVariable Long id) {
        User me = users.requireCurrent();
        history.findById(id).filter(h -> h.getUser().getId().equals(me.getId())).ifPresent(history::delete);
        return "redirect:/history";
    }

    @GetMapping("/subscriptions")
    public String subscriptions(Model model) {
        User me = users.requireCurrent();
        model.addAttribute("channels", subscriptions.channelsOf(me));
        model.addAttribute("videos", videos.subscriptionFeed(me, 60));
        return "library/subscriptions";
    }

    @GetMapping("/favorites")
    public String favorites(Model model) {
        User me = users.requireCurrent();
        model.addAttribute("title", "Избранное");
        model.addAttribute("icon", "bookmark");
        model.addAttribute("subtitle", "Видео, которые вы сохранили, чтобы вернуться к ним позже");
        model.addAttribute("videos", videos.favoritesOf(me));
        return "library/videos";
    }

    @GetMapping("/liked")
    public String liked(Model model) {
        User me = users.requireCurrent();
        model.addAttribute("title", "Понравившиеся");
        model.addAttribute("icon", "thumbs-up");
        model.addAttribute("subtitle", "Всё, чему вы поставили лайк");
        model.addAttribute("videos", videos.likedBy(me));
        return "library/videos";
    }

    @GetMapping("/playlists")
    public String playlists(Model model) {
        User me = users.requireCurrent();
        model.addAttribute("items", playlists.ofUser(me, false));
        model.addAttribute("collectionMode", false);
        model.addAttribute("icons", PlaylistService.icons());
        return "library/playlists";
    }

    @GetMapping("/collections")
    public String collections(Model model) {
        User me = users.requireCurrent();
        model.addAttribute("items", playlists.ofUser(me, true));
        model.addAttribute("collectionMode", true);
        model.addAttribute("icons", PlaylistService.icons());
        return "library/playlists";
    }

    @PostMapping("/playlists")
    public String createPlaylist(@RequestParam String title, @RequestParam(required = false) String description,
                                 @RequestParam(defaultValue = "false") boolean collection,
                                 @RequestParam(defaultValue = "false") boolean publicVisible,
                                 @RequestParam(required = false) String color,
                                 @RequestParam(required = false) String icon, RedirectAttributes ra) {
        User me = users.requireCurrent();
        try {
            Playlist p = playlists.save(me, null, title, description, collection, publicVisible, color, icon);
            ra.addFlashAttribute("toast", collection ? "Коллекция создана" : "Плейлист создан");
            return "redirect:/playlist/" + p.getId();
        } catch (BadRequestException e) {
            ra.addFlashAttribute("toast", e.getMessage());
            return collection ? "redirect:/collections" : "redirect:/playlists";
        }
    }

    @GetMapping("/playlist/{id}")
    public String playlist(@PathVariable Long id, Model model) {
        User me = (User) model.getAttribute("me");
        Playlist p = playlists.getViewable(id, me);
        model.addAttribute("playlist", p);
        model.addAttribute("items", playlists.itemsOf(p, me));
        model.addAttribute("owner", playlists.isOwner(me, p));
        model.addAttribute("icons", PlaylistService.icons());
        return "library/playlist";
    }

    @PostMapping("/playlists/{id}")
    public String updatePlaylist(@PathVariable Long id, @RequestParam String title,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(defaultValue = "false") boolean publicVisible,
                                 @RequestParam(required = false) String color,
                                 @RequestParam(required = false) String icon, RedirectAttributes ra) {
        User me = users.requireCurrent();
        try {
            playlists.save(me, id, title, description, false, publicVisible, color, icon);
            ra.addFlashAttribute("toast", "Сохранено");
        } catch (BadRequestException e) {
            ra.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/playlist/" + id;
    }

    @PostMapping("/playlists/{id}/delete")
    public String deletePlaylist(@PathVariable Long id, RedirectAttributes ra) {
        User me = users.requireCurrent();
        boolean collection = playlists.getOwned(id, me).isCollection();
        playlists.delete(me, id);
        ra.addFlashAttribute("toast", "Удалено");
        return collection ? "redirect:/collections" : "redirect:/playlists";
    }

    @PostMapping("/playlists/{id}/remove/{videoId}")
    public String removeFromPlaylist(@PathVariable Long id, @PathVariable Long videoId) {
        playlists.remove(users.requireCurrent(), id, videoId);
        return "redirect:/playlist/" + id;
    }

    @PostMapping("/playlists/{id}/move/{itemId}")
    public String move(@PathVariable Long id, @PathVariable Long itemId, @RequestParam int dir) {
        playlists.move(users.requireCurrent(), id, itemId, dir);
        return "redirect:/playlist/" + id;
    }
}
