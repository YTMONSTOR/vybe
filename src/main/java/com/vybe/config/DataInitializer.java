package com.vybe.config;

import com.vybe.entity.Category;
import com.vybe.entity.Role;
import com.vybe.entity.User;
import com.vybe.repository.CategoryRepository;
import com.vybe.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the platform on first start: the administrator account and the base category set.
 * No fake videos or users are created — all content comes from real uploads.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String[][] CATEGORIES = {
            {"Музыка", "music", "music", "#a855f7"},
            {"Игры", "games", "gamepad-2", "#6366f1"},
            {"Технологии", "tech", "cpu", "#3b82f6"},
            {"Образование", "education", "graduation-cap", "#0ea5e9"},
            {"Кино и анимация", "film", "clapperboard", "#ec4899"},
            {"Спорт", "sport", "trophy", "#22c55e"},
            {"Путешествия", "travel", "plane", "#14b8a6"},
            {"Еда", "food", "utensils", "#f97316"},
            {"Наука", "science", "atom", "#8b5cf6"},
            {"Юмор", "comedy", "laugh", "#eab308"},
            {"Подкасты", "podcasts", "mic", "#d946ef"},
            {"Лайфстайл", "lifestyle", "sparkles", "#f43f5e"},
    };

    private final UserRepository users;
    private final CategoryRepository categories;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(UserRepository users, CategoryRepository categories, PasswordEncoder encoder,
                           @Value("${vybe.admin.username}") String adminUsername,
                           @Value("${vybe.admin.email}") String adminEmail,
                           @Value("${vybe.admin.password}") String adminPassword) {
        this.users = users;
        this.categories = categories;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByUsernameIgnoreCase(adminUsername).isEmpty()) {
            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setEmail(adminEmail);
            admin.setDisplayName("VYBE Team");
            admin.setBio("Официальный канал команды VYBE.");
            admin.setPassword(encoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            users.save(admin);
            log.info("Created administrator account '{}' — change the password after first login.", adminUsername);
        }
        if (categories.count() == 0) {
            int order = 0;
            for (String[] c : CATEGORIES) {
                Category cat = new Category();
                cat.setName(c[0]);
                cat.setSlug(c[1]);
                cat.setIcon(c[2]);
                cat.setColor(c[3]);
                cat.setSortOrder(order++);
                categories.save(cat);
            }
            log.info("Created {} default categories", CATEGORIES.length);
        }
    }
}
