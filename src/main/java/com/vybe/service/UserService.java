package com.vybe.service;

import com.vybe.dto.ProfileForm;
import com.vybe.dto.RegisterForm;
import com.vybe.entity.Role;
import com.vybe.entity.User;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final StorageService storage;

    public UserService(UserRepository users, PasswordEncoder encoder, StorageService storage) {
        this.users = users;
        this.encoder = encoder;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String l = login == null ? "" : login.trim();
        User u = (l.contains("@") ? users.findByEmailIgnoreCase(l) : users.findByUsernameIgnoreCase(l))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                .password(u.getPassword())
                .roles(u.getRole().name())
                .disabled(!u.isEnabled())
                .build();
    }

    /** The currently authenticated user, if any. */
    @Transactional(readOnly = true)
    public Optional<User> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return users.findByUsernameIgnoreCase(auth.getName());
    }

    public User requireCurrent() {
        return current().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Требуется вход"));
    }

    public Optional<User> fromPrincipal(Principal principal) {
        if (principal == null) return Optional.empty();
        return users.findByUsernameIgnoreCase(principal.getName());
    }

    public User getByUsername(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("Канал не найден"));
    }

    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    @Transactional
    public User register(RegisterForm form) {
        String username = form.getUsername().trim();
        String email = form.getEmail().trim().toLowerCase();
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BadRequestException("Пароли не совпадают");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Имя пользователя уже занято");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("Этот email уже зарегистрирован");
        }
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setDisplayName(form.getDisplayName().trim());
        u.setPassword(encoder.encode(form.getPassword()));
        u.setRole(Role.USER);
        return users.save(u);
    }

    @Transactional
    public User updateProfile(User user, ProfileForm form, MultipartFile avatar, MultipartFile banner,
                              boolean removeAvatar, boolean removeBanner) {
        User u = get(user.getId());
        u.setDisplayName(form.getDisplayName().trim());
        u.setBio(form.getBio() == null ? null : form.getBio().trim());
        if (storage.hasContent(avatar)) {
            String old = u.getAvatarPath();
            u.setAvatarPath(storage.storeImage(avatar, "avatars"));
            storage.delete(old);
        } else if (removeAvatar) {
            storage.delete(u.getAvatarPath());
            u.setAvatarPath(null);
        }
        if (storage.hasContent(banner)) {
            String old = u.getBannerPath();
            u.setBannerPath(storage.storeImage(banner, "banners"));
            storage.delete(old);
        } else if (removeBanner) {
            storage.delete(u.getBannerPath());
            u.setBannerPath(null);
        }
        return u;
    }

    @Transactional
    public void changePassword(User user, String current, String next) {
        User u = get(user.getId());
        if (!encoder.matches(current, u.getPassword())) throw new BadRequestException("Текущий пароль неверен");
        if (next == null || next.length() < 8 || next.length() > 72) {
            throw new BadRequestException("Новый пароль: от 8 до 72 символов");
        }
        u.setPassword(encoder.encode(next));
    }

    @Transactional
    public User toggleEnabled(Long id, User actor) {
        User u = get(id);
        if (u.getId().equals(actor.getId())) throw new BadRequestException("Нельзя заблокировать самого себя");
        u.setEnabled(!u.isEnabled());
        return u;
    }

    @Transactional
    public User toggleAdmin(Long id, User actor) {
        User u = get(id);
        if (u.getId().equals(actor.getId())) throw new BadRequestException("Нельзя изменить собственную роль");
        u.setRole(u.getRole() == Role.ADMIN ? Role.USER : Role.ADMIN);
        return u;
    }
}
