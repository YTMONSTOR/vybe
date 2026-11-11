package com.vybe.dto;

import com.vybe.entity.Comment;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.service.Fmt;

import java.util.List;

/** Small JSON payloads for the REST API (entities are never serialised directly). */
public final class ApiDtos {

    private ApiDtos() {}

    public record UserBrief(Long id, String username, String displayName, String avatarUrl,
                            String initials, int hue) {
        public static UserBrief of(User u) {
            return new UserBrief(u.getId(), u.getUsername(), u.getDisplayName(), u.getAvatarUrl(),
                    u.getInitials(), u.getHue());
        }
    }

    public record CommentDto(Long id, String text, String ago, boolean pinned, boolean hearted,
                             int replyCount, UserBrief author, boolean canDelete, boolean canModerate,
                             boolean byCreator, Long parentId) {
        public static CommentDto of(Comment c, User viewer, Fmt fmt) {
            User videoAuthor = c.getVideo().getAuthor();
            boolean isOwner = viewer != null && viewer.getId().equals(c.getAuthor().getId());
            boolean isCreator = viewer != null && viewer.getId().equals(videoAuthor.getId());
            boolean isAdmin = viewer != null && viewer.isAdmin();
            return new CommentDto(c.getId(), c.getText(), fmt.ago(c.getCreatedAt()), c.isPinned(), c.isHearted(),
                    c.getReplyCount(), UserBrief.of(c.getAuthor()), isOwner || isCreator || isAdmin,
                    isCreator, c.getAuthor().getId().equals(videoAuthor.getId()),
                    c.getParent() == null ? null : c.getParent().getId());
        }
    }

    public record VideoCard(Long id, String title, String thumbnailUrl, String videoUrl, String duration,
                            String views, String ago, UserBrief author, String reason, int progress) {
        public static VideoCard of(Video v, Fmt fmt, String reason, int progress) {
            return new VideoCard(v.getId(), v.getTitle(), v.getThumbnailUrl(), v.getVideoUrl(),
                    fmt.duration(v.getDurationSeconds()), fmt.views(v.getViews()), fmt.ago(v.getCreatedAt()),
                    UserBrief.of(v.getAuthor()), reason, progress);
        }
    }

    public record ReactionState(String reaction, long likes, long dislikes) {}

    public record ToggleState(boolean active, long count) {}

    public record PlaylistState(Long id, String title, boolean collection, boolean contains, int size,
                                String color, String icon) {}

    public record Suggestion(String text, String type) {}

    public record Series(List<String> labels, List<Long> values) {}

    public record Message(String message) {}
}
