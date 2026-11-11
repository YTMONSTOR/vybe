package com.vybe.config;

import com.vybe.service.StorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Serves uploaded media. Spring's resource handler supports HTTP Range requests,
 * so the browser can seek inside large video files.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StorageService storage;

    public WebConfig(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = storage.getRoot().toUri().toString();
        if (!location.endsWith("/")) location = location + "/";
        registry.addResourceHandler("/media/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());
    }
}
