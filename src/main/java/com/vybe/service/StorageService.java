package com.vybe.service;

import com.vybe.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded media on the local file system under {@code vybe.storage.path}.
 * Files are always renamed to random UUIDs, so user supplied names never reach the disk.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    public static final Set<String> VIDEO_EXT = Set.of("mp4", "webm", "mov", "m4v", "ogv", "ogg", "mkv");
    public static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final Path root;

    public StorageService(@Value("${vybe.storage.path}") String path) {
        this.root = Paths.get(path).toAbsolutePath().normalize();
        try {
            for (String dir : new String[]{"videos", "thumbs", "avatars", "banners"}) {
                Files.createDirectories(root.resolve(dir));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
        log.info("VYBE media storage: {}", root);
    }

    public Path getRoot() {
        return root;
    }

    public String storeVideo(MultipartFile file) {
        return store(file, "videos", VIDEO_EXT, "video/");
    }

    public String storeImage(MultipartFile file, String folder) {
        return store(file, folder, IMAGE_EXT, "image/");
    }

    public boolean hasContent(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private String store(MultipartFile file, String folder, Set<String> allowedExt, String typePrefix) {
        if (!hasContent(file)) throw new BadRequestException("Файл не выбран");
        String ext = extension(file.getOriginalFilename());
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (ext.isEmpty() && type.startsWith(typePrefix)) {
            ext = type.substring(typePrefix.length()).replace("jpeg", "jpg").replace("quicktime", "mov");
        }
        if (!allowedExt.contains(ext)) {
            throw new BadRequestException("Недопустимый формат файла: ." + ext + ". Разрешены: " + String.join(", ", allowedExt));
        }
        if (!type.isEmpty() && !type.startsWith(typePrefix) && !type.equals("application/octet-stream")) {
            throw new BadRequestException("Неверный тип файла: " + type);
        }
        String relative = folder + "/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить файл", e);
        }
        return relative;
    }

    /** Reserve a new path (not yet written) in the given folder. */
    public String newRelativePath(String folder, String ext) {
        return folder + "/" + UUID.randomUUID() + "." + ext;
    }

    public Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) throw new BadRequestException("Недопустимый путь");
        return p;
    }

    public void delete(String relative) {
        if (relative == null || relative.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(relative));
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot delete media file {}: {}", relative, e.getMessage());
        }
    }

    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
