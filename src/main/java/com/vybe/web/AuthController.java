package com.vybe.web;

import com.vybe.dto.RegisterForm;
import com.vybe.exception.BadRequestException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.vybe.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @GetMapping("/login")
    public String login(Model model) {
        if (model.getAttribute("me") != null) return "redirect:/";
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (model.getAttribute("me") != null) return "redirect:/";
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult binding,
                           HttpServletRequest request, Model model) {
        if (binding.hasErrors()) return "auth/register";
        try {
            users.register(form);
        } catch (BadRequestException e) {
            model.addAttribute("formError", e.getMessage());
            return "auth/register";
        }
        try {
            request.login(form.getUsername().trim(), form.getPassword());
        } catch (ServletException e) {
            return "redirect:/login?registered";
        }
        return "redirect:/?welcome";
    }
}
