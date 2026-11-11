package com.vybe.web;

import com.vybe.entity.Category;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.repository.WatchHistoryRepository;
import com.vybe.service.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    private final VideoService videos;
    private final RecommendationService recommendations;
    private final SearchService search;
    private final CategoryService categories;
    private final WatchHistoryRepository history;

    public HomeController(VideoService videos, RecommendationService recommendations, SearchService search,
                          CategoryService categories, WatchHistoryRepository history) {
        this.videos = videos;
        this.recommendations = recommendations;
        this.search = search;
        this.categories = categories;
        this.history = history;
    }

    @GetMapping("/")
    public String home(Model model) {
        User me = (User) model.getAttribute("me");
        List<Video> trending = videos.trending(7, 10);
        model.addAttribute("hero", trending.isEmpty() ? null : trending.get(0));
        model.addAttribute("heroSide", trending.size() > 1 ? trending.subList(1, Math.min(4, trending.size())) : List.of());
        model.addAttribute("trending", trending);
        model.addAttribute("feed", recommendations.smartFeed(me, 0, 12));
        model.addAttribute("latest", videos.latest(8));
        model.addAttribute("continueWatching", me == null ? List.of() : history.continueWatching(me, PageRequest.of(0, 10)));
        model.addAttribute("subscriptionFeed", me == null ? List.of() : videos.subscriptionFeed(me, 8));
        return "index";
    }

    @GetMapping("/explore/{slug}")
    public String category(@PathVariable String slug, @RequestParam(defaultValue = "new") String sort, Model model) {
        Category c = categories.bySlug(slug);
        model.addAttribute("category", c);
        model.addAttribute("sort", sort);
        model.addAttribute("videos", videos.byCategory(c, sort, 60));
        return "explore";
    }

    @GetMapping("/explore")
    public String exploreAll() {
        return "redirect:/trending";
    }

    @GetMapping("/trending")
    public String trending(@RequestParam(required = false) String category,
                           @RequestParam(defaultValue = "7") int days, Model model) {
        int period = days == 1 || days == 30 ? days : 7;
        List<Video> list = videos.trending(period, 120);
        if (category != null && !category.isBlank()) {
            list = list.stream().filter(v -> v.getCategory() != null && category.equals(v.getCategory().getSlug())).toList();
        }
        model.addAttribute("videos", list.size() > 50 ? list.subList(0, 50) : list);
        model.addAttribute("days", period);
        model.addAttribute("activeCategory", category);
        return "trending";
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "relevance") String sort,
                         @RequestParam(defaultValue = "") String duration,
                         @RequestParam(required = false) Long category, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("sort", sort);
        model.addAttribute("duration", duration);
        model.addAttribute("categoryId", category);
        model.addAttribute("videos", search.search(q, sort, duration, category, 60));
        model.addAttribute("channels", search.channels(q, 4));
        return "search";
    }
}
