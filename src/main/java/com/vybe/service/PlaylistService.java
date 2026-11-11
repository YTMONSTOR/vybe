package com.vybe.service;

import com.vybe.dto.ApiDtos.PlaylistState;
import com.vybe.entity.Playlist;
import com.vybe.entity.PlaylistItem;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.entity.Visibility;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.ForbiddenException;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.PlaylistItemRepository;
import com.vybe.repository.PlaylistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Playlists and Collections. */
@Service
public class PlaylistService {

    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> ICONS = Set.of("layers", "bookmark", "star", "heart", "flame", "music",
            "gamepad-2", "graduation-cap", "code", "film", "camera", "rocket", "coffee", "sparkles", "zap",
            "book-open", "dumbbell", "plane", "palette", "lightbulb");

    private final PlaylistRepository playlists;
    private final PlaylistItemRepository items;

    public PlaylistService(PlaylistRepository playlists, PlaylistItemRepository items) {
        this.playlists = playlists;
        this.items = items;
    }

    public static Set<String> icons() {
        return ICONS;
    }

    public Playlist get(Long id) {
        return playlists.findById(id).orElseThrow(() -> new NotFoundException("Плейлист не найден"));
    }

    public Playlist getViewable(Long id, User viewer) {
        Playlist p = get(id);
        if (!p.isPublicVisible() && !isOwner(viewer, p)) throw new NotFoundException("Плейлист не найден");
        return p;
    }

    public Playlist getOwned(Long id, User owner) {
        Playlist p = get(id);
        if (!isOwner(owner, p)) throw new ForbiddenException("Это не ваш плейлист");
        return p;
    }

    public boolean isOwner(User user, Playlist p) {
        return user != null && p.getOwner().getId().equals(user.getId());
    }

    public List<Playlist> ofUser(User owner, Boolean collection) {
        if (collection == null) return playlists.findByOwnerOrderByUpdatedAtDesc(owner);
        return playlists.findByOwnerAndCollectionOrderByUpdatedAtDesc(owner, collection);
    }

    public List<Playlist> publicOf(User owner) {
        return playlists.findByOwnerAndPublicVisibleTrueOrderByUpdatedAtDesc(owner);
    }

    /** Items visible to the viewer (private videos of other authors are hidden). */
    public List<PlaylistItem> itemsOf(Playlist p, User viewer) {
        return p.getItems().stream()
                .filter(i -> i.getVideo().getVisibility() != Visibility.PRIVATE
                        || (viewer != null && (viewer.isAdmin() || i.getVideo().getAuthor().getId().equals(viewer.getId()))))
                .toList();
    }

    public List<Video> videosOf(Playlist p, User viewer) {
        return itemsOf(p, viewer).stream().map(PlaylistItem::getVideo).toList();
    }

    @Transactional
    public void remove(User owner, Long playlistId, Long videoId) {
        Playlist p = getOwned(playlistId, owner);
        p.getItems().removeIf(i -> i.getVideo().getId().equals(videoId));
        p.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public Playlist save(User owner, Long id, String title, String description, boolean collection,
                         boolean publicVisible, String color, String icon) {
        if (title == null || title.isBlank() || title.trim().length() > 80) {
            throw new BadRequestException("Название: от 1 до 80 символов");
        }
        if (description != null && description.length() > 500) throw new BadRequestException("Описание не длиннее 500 символов");
        Playlist p = id == null ? new Playlist() : getOwned(id, owner);
        if (id == null) {
            p.setOwner(owner);
            p.setCollection(collection);
        }
        p.setTitle(title.trim());
        p.setDescription(description == null || description.isBlank() ? null : description.trim());
        p.setPublicVisible(publicVisible);
        if (color != null && COLOR.matcher(color).matches()) p.setColor(color);
        if (icon != null && ICONS.contains(icon)) p.setIcon(icon);
        p.setUpdatedAt(LocalDateTime.now());
        return playlists.save(p);
    }

    @Transactional
    public void delete(User owner, Long id) {
        playlists.delete(getOwned(id, owner));
    }

    /** @return true if the video is in the playlist after the call. */
    @Transactional
    public boolean toggle(User owner, Long playlistId, Video video) {
        Playlist p = getOwned(playlistId, owner);
        Optional<PlaylistItem> existing = items.findByPlaylistAndVideo(p, video);
        p.setUpdatedAt(LocalDateTime.now());
        if (existing.isPresent()) {
            p.getItems().remove(existing.get());
            return false;
        }
        if (p.getItems().size() >= 500) throw new BadRequestException("В плейлисте не может быть больше 500 видео");
        PlaylistItem item = new PlaylistItem();
        item.setPlaylist(p);
        item.setVideo(video);
        item.setPosition(items.maxPosition(p) + 1);
        p.getItems().add(item);
        return true;
    }

    @Transactional
    public void move(User owner, Long playlistId, Long itemId, int direction) {
        Playlist p = getOwned(playlistId, owner);
        List<PlaylistItem> list = p.getItems();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(itemId)) {
                int j = i + (direction < 0 ? -1 : 1);
                if (j < 0 || j >= list.size()) return;
                PlaylistItem a = list.get(i), b = list.get(j);
                int pos = a.getPosition();
                a.setPosition(b.getPosition());
                b.setPosition(pos);
                if (a.getPosition() == b.getPosition()) a.setPosition(a.getPosition() + direction);
                return;
            }
        }
    }

    public List<PlaylistState> states(User owner, Video video) {
        Set<Long> containing = new HashSet<>(items.playlistIdsContaining(video, owner));
        return playlists.findByOwnerOrderByUpdatedAtDesc(owner).stream()
                .map(p -> new PlaylistState(p.getId(), p.getTitle(), p.isCollection(), containing.contains(p.getId()),
                        p.getItems().size(), p.getColor(), p.getIcon()))
                .toList();
    }
}
