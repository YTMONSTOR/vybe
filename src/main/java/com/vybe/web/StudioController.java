package com.vybe.web;

import com.vybe.dto.VideoForm;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.exception.BadRequestException;
import com.vybe.service.*;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;

/** Creator Studio — channel dashboard, video management, comment moderation, Watch Statistics. */
@Controller
@RequestMapping("/studio")
public class StudioController {

    private final UserService users;
    private final VideoService videos;
    private final StatsService stats;
    private final CommentService comments;
    private final CategoryService categories;

    public StudioController(UserService users, VideoService videos, StatsService stats, CommentService comments,
                            CategoryService categories) {
        this.users = users;
        this.videos = videos;
        this.stats = stats;
        this.comments = comments;
        this.categories = categories;
    }

    @GetMapping
    public String dashboard(Model model) {
        User me = users.requireCurrent();
        List<Video> mine = videos.channelVideos(me, true, "new");
        model.addAttribute("totals", stats.creatorTotals(me));
        model.addAttribute("topVideos", mine.stream().sorted(Comparator.comparingLong(Video::getViews).reversed()).limit(5).toList());
        model.addAttribute("recentVideos", mine.stream().limit(4).toList());
        model.addAttribute("recentComments", comments.forCreator(me, null, 0).getContent().stream().limit(6).toList());
        model.addAttribute("section", "dashboard");
        return "studio/dashboard";
    }

    @GetMapping("/videos")
    public String videos(@RequestParam(defaultValue = "") String q, Model model) {
        User me = users.requireCurrent();
        String query = q.trim().toLowerCase();
        List<Video> list = videos.channelVideos(me, true, "new").stream()
                .filter(v -> query.isEmpty() || v.getTitle().toLowerCase().contains(query)).toList();
        model.addAttribute("videos", list);
        model.addAttribute("q", q);
        model.addAttribute("section", "videos");
        return "studio/videos";
    }

    @GetMapping("/videos/{id}")
    public String edit(@PathVariable Long id, Model model) {
        User me = users.requireCurrent();
        Video v = videos.getEditable(id, me);
        if (!model.containsAttribute("form")) {
            VideoForm form = new VideoForm();
            form.setTitle(v.getTitle());
            form.setDescription(v.getDescription());
            form.setCategoryId(v.getCategory() == null ? null : v.getCategory().getId());
            form.setTags(v.getTagsAsString());
            form.setVisibility(v.getVisibility());
            form.setCommentsEnabled(v.isCommentsEnabled());
            model.addAttribute("form", form);
        }
        model.addAttribute("video", v);
        model.addAttribute("categories", categories.all());
        model.addAttribute("section", "videos");
        return "studio/edit";
    }

    @PostMapping("/videos/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") VideoForm form, BindingResult binding,
                         @RequestParam(required = false) MultipartFile thumbnail,
                         @RequestParam(defaultValue = "false") boolean regenerateThumbnail,
                         Model model, RedirectAttributes ra) {
        User me = users.requireCurrent();
        Video v = videos.getEditable(id, me);
        if (binding.hasErrors()) {
            model.addAttribute("video", v);
            model.addAttribute("categories", categories.all());
            model.addAttribute("section", "videos");
            return "studio/edit";
        }
        try {
            videos.update(v, form, thumbnail, regenerateThumbnail);
            ra.addFlashAttribute("toast", "Изменения сохранены");
        } catch (BadRequestException e) {
            ra.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/studio/videos/" + id;
    }

    @PostMapping("/videos/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        User me = users.requireCurrent();
        videos.delete(videos.getEditable(id, me));
        ra.addFlashAttribute("toast", "Видео удалено");
        return "redirect:/studio/videos";
    }

    @GetMapping("/comments")
    public String comments(@RequestParam(required = false) Long video, @RequestParam(defaultValue = "0") int page,
                           Model model) {
        User me = users.requireCurrent();
        model.addAttribute("comments", comments.forCreator(me, video, Math.max(0, page)));
        model.addAttribute("videos", videos.channelVideos(me, true, "new"));
        model.addAttribute("videoFilter", video);
        model.addAttribute("section", "comments");
        return "studio/comments";
    }

    @PostMapping("/comments/{id}/delete")
    public String deleteComment(@PathVariable Long id, @RequestParam(required = false) Long video, RedirectAttributes ra) {
        comments.delete(users.requireCurrent(), id);
        ra.addFlashAttribute("toast", "Комментарий удалён");
        return "redirect:/studio/comments" + (video == null ? "" : "?video=" + video);
    }

    @PostMapping("/comments/{id}/pin")
    public String pin(@PathVariable Long id, @RequestParam(required = false) Long video) {
        comments.togglePin(users.requireCurrent(), id);
        return "redirect:/studio/comments" + (video == null ? "" : "?video=" + video);
    }

    @PostMapping("/comments/{id}/heart")
    public String heart(@PathVariable Long id, @RequestParam(required = false) Long video) {
        comments.toggleHeart(users.requireCurrent(), id);
        return "redirect:/studio/comments" + (video == null ? "" : "?video=" + video);
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        User me = users.requireCurrent();
        model.addAttribute("totals", stats.creatorTotals(me));
        model.addAttribute("videos", videos.channelVideos(me, true, "new").stream()
                .sorted(Comparator.comparingLong(Video::getViews).reversed()).toList());
        model.addAttribute("section", "analytics");
        return "studio/analytics";
    }
}
