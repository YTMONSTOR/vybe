package com.vybe.web;

import com.vybe.entity.ReportStatus;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.Visibility;
import com.vybe.exception.BadRequestException;
import com.vybe.repository.UserRepository;
import com.vybe.repository.VideoRepository;
import com.vybe.service.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService users;
    private final UserRepository userRepository;
    private final VideoService videos;
    private final VideoRepository videoRepository;
    private final StatsService stats;
    private final CategoryService categories;
    private final ReportService reports;

    public AdminController(UserService users, UserRepository userRepository, VideoService videos,
                           VideoRepository videoRepository, StatsService stats, CategoryService categories,
                           ReportService reports) {
        this.users = users;
        this.userRepository = userRepository;
        this.videos = videos;
        this.videoRepository = videoRepository;
        this.stats = stats;
        this.categories = categories;
        this.reports = reports;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totals", stats.platformTotals());
        model.addAttribute("latestVideos", videoRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 6)).getContent());
        model.addAttribute("latestReports", reports.list(ReportStatus.OPEN).stream().limit(5).toList());
        model.addAttribute("section", "dashboard");
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        model.addAttribute("users", userRepository.search(like, PageRequest.of(Math.max(0, page), 25)));
        model.addAttribute("q", q);
        model.addAttribute("section", "users");
        return "admin/users";
    }

    @PostMapping("/users/{id}/ban")
    public String toggleBan(@PathVariable Long id, RedirectAttributes ra) {
        try {
            User u = users.toggleEnabled(id, users.requireCurrent());
            ra.addFlashAttribute("toast", u.isEnabled() ? "Пользователь разблокирован" : "Пользователь заблокирован");
        } catch (BadRequestException e) {
            ra.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String toggleRole(@PathVariable Long id, RedirectAttributes ra) {
        try {
            User u = users.toggleAdmin(id, users.requireCurrent());
            ra.addFlashAttribute("toast", u.isAdmin() ? "Назначен администратором" : "Права администратора сняты");
        } catch (BadRequestException e) {
            ra.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/videos")
    public String videos(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page, Model model) {
        PageRequest pr = PageRequest.of(Math.max(0, page), 25);
        String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        model.addAttribute("videos", q.isBlank() ? videoRepository.findAllByOrderByCreatedAtDesc(pr) : videoRepository.adminSearch(like, pr));
        model.addAttribute("q", q);
        model.addAttribute("section", "videos");
        return "admin/videos";
    }

    @PostMapping("/videos/{id}/delete")
    public String deleteVideo(@PathVariable Long id, RedirectAttributes ra) {
        videos.delete(videos.get(id));
        ra.addFlashAttribute("toast", "Видео удалено");
        return "redirect:/admin/videos";
    }

    @PostMapping("/videos/{id}/visibility")
    @Transactional
    public String visibility(@PathVariable Long id, @RequestParam Visibility visibility, RedirectAttributes ra) {
        Video v = videos.get(id);
        v.setVisibility(visibility);
        ra.addFlashAttribute("toast", "Доступ изменён");
        return "redirect:/admin/videos";
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", categories.all());
        model.addAttribute("section", "categories");
        return "admin/categories";
    }

    @PostMapping("/categories")
    public String saveCategory(@RequestParam(required = false) Long id, @RequestParam String name,
                               @RequestParam(defaultValue = "folder") String icon,
                               @RequestParam(defaultValue = "#8b5cf6") String color,
                               @RequestParam(defaultValue = "0") int sortOrder, RedirectAttributes ra) {
        try {
            categories.save(id, name, icon, color, sortOrder);
            ra.addFlashAttribute("toast", id == null ? "Категория добавлена" : "Категория обновлена");
        } catch (BadRequestException e) {
            ra.addFlashAttribute("toast", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        categories.delete(id);
        ra.addFlashAttribute("toast", "Категория удалена");
        return "redirect:/admin/categories";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(defaultValue = "OPEN") String status, Model model) {
        ReportStatus st = "ALL".equals(status) ? null : ReportStatus.valueOf(status);
        model.addAttribute("reports", reports.list(st));
        model.addAttribute("status", status);
        model.addAttribute("section", "reports");
        return "admin/reports";
    }

    @PostMapping("/reports/{id}/dismiss")
    public String dismiss(@PathVariable Long id, RedirectAttributes ra) {
        reports.dismiss(id);
        ra.addFlashAttribute("toast", "Жалоба отклонена");
        return "redirect:/admin/reports";
    }

    @PostMapping("/reports/{id}/remove")
    public String remove(@PathVariable Long id, RedirectAttributes ra) {
        reports.removeContent(id);
        ra.addFlashAttribute("toast", "Контент удалён");
        return "redirect:/admin/reports";
    }
}
