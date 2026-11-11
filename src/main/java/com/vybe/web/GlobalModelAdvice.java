package com.vybe.web;

import com.vybe.entity.Category;
import com.vybe.entity.User;
import com.vybe.service.CategoryService;
import com.vybe.service.ReportService;
import com.vybe.service.SubscriptionService;
import com.vybe.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Data every page layout needs: current user, sidebar subscriptions and categories. */
@ControllerAdvice(basePackages = "com.vybe.web")
public class GlobalModelAdvice {

    private final UserService users;
    private final CategoryService categories;
    private final SubscriptionService subscriptions;
    private final ReportService reports;

    public GlobalModelAdvice(UserService users, CategoryService categories, SubscriptionService subscriptions,
                             ReportService reports) {
        this.users = users;
        this.categories = categories;
        this.subscriptions = subscriptions;
        this.reports = reports;
    }

    @ModelAttribute
    public void populate(Model model, HttpServletRequest request) {
        User me = users.current().orElse(null);
        model.addAttribute("me", me);
        model.addAttribute("path", request.getRequestURI());
        List<Category> cats = categories.all();
        model.addAttribute("navCategories", cats);
        model.addAttribute("navSubscriptions", me == null ? List.of() : subscriptions.channelsOf(me));
        model.addAttribute("openReports", me != null && me.isAdmin() ? reports.openCount() : 0L);
    }
}
