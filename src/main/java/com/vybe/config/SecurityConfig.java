package com.vybe.config;

import com.vybe.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher API = (HttpServletRequest r) -> r.getRequestURI().startsWith(r.getContextPath() + "/api/");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserService userService) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico", "/media/**", "/error").permitAll()
                .requestMatchers("/", "/login", "/register", "/trending", "/search", "/explore", "/explore/**",
                        "/watch/**", "/channel/**", "/playlist/**", "/together/*", "/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search/suggest", "/api/feed", "/api/videos/*/comments",
                        "/api/comments/*/replies").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/videos/*/view").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll())
            .rememberMe(rm -> rm
                .key("vybe-remember-me-key-change-in-production")
                .tokenValiditySeconds(60 * 60 * 24 * 30)
                .userDetailsService(userService))
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
}
