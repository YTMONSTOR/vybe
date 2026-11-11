package com.vybe.web;

import com.vybe.dto.ProfileForm;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.exception.BadRequestException;
import com.vybe.repository.VideoRepository;
import com.vybe.service.PlaylistService;
import com.vybe.service.SubscriptionService;
import com.vybe.service.UserService;
import com.vybe.service.VideoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class ChannelController {

    private final UserService users;
    private final VideoService videos;
    private final PlaylistService playlists;
    private final SubscriptionService subscriptions;
    private final VideoRepository videoRepository;

    public ChannelController(UserService users, VideoService videos, PlaylistService playlists,
                             SubscriptionService subscriptions, VideoRepository videoRepository) {
        this.users = users;
        this.videos = videos;
        this.playlists = playlists;
        this.subscriptions = subscriptions;
        this.videoRepository = videoRepository;
    }

    @GetMapping("/channel/{username}")
    public String channel(@PathVariable String username, @RequestParam(defaultValue = "videos") String tab,
                          @RequestParam(defaultValue = "new") String sort, Model model) {
        User me = (User) model.getAttribute("me");
        User channel = users.getByUsername(username);
        if (!channel.isEnabled() && (me == null || !me.isAdmin())) {
            throw new com.vybe.exception.NotFoundException("Канал заблокирован");
        }
        boolean owner = me != null && me.getId().equals(channel.getId());
        List<Video> list = videos.channelVideos(channel, false, sort);
        model.addAttribute("channel", channel);
        model.addAttribute("tab", tab);
        model.addAttribute("sort", sort);
        model.addAttribute("videos", list);
        model.addAttribute("featured", list.isEmpty() ? null : videos.channelVideos(channel, false, "popular").get(0));
        model.addAttribute("playlists", owner ? playlists.ofUser(channel, null) : playlists.publicOf(channel));
        model.addAttribute("owner", owner);
        model.addAttribute("subscribed", subscriptions.isSubscribed(me, channel));
        model.addAttribute("totalViews", videoRepository.sumViewsByAuthor(channel));
        model.addAttribute("videoCount", videoRepository.countByAuthorAndVisibility(channel, com.vybe.entity.Visibility.PUBLIC));
        return "channel";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        User me = users.requireCurrent();
        ProfileForm form = new ProfileForm();
        form.setDisplayName(me.getDisplayName());
        form.setBio(me.getBio());
        model.addAttribute("form", form);
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@Valid @ModelAttribute("form") ProfileForm form, BindingResult binding,
                               @RequestParam(required = false) MultipartFile avatar,
                               @RequestParam(required = false) MultipartFile banner,
                               @RequestParam(defaultValue = "false") boolean removeAvatar,
                               @RequestParam(defaultValue = "false") boolean removeBanner,
                               Model model, RedirectAttributes ra) {
        User me = users.requireCurrent();
        if (binding.hasErrors()) return "settings";
        try {
            users.updateProfile(me, form, avatar, banner, removeAvatar, removeBanner);
        } catch (BadRequestException e) {
            model.addAttribute("formError", e.getMessage());
            return "settings";
        }
        ra.addFlashAttribute("toast", "Профиль обновлён");
        return "redirect:/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, RedirectAttributes ra) {
        User me = users.requireCurrent();
        try {
            if (!newPassword.equals(confirmPassword)) throw new BadRequestException("Пароли не совпадают");
            users.changePassword(me, currentPassword, newPassword);
            ra.addFlashAttribute("toast", "Пароль изменён");
        } catch (BadRequestException e) {
            ra.addFlashAttribute("passwordError", e.getMessage());
        }
        return "redirect:/settings#security";
    }
}
