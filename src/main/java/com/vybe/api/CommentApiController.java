package com.vybe.api;

import com.vybe.dto.ApiDtos.CommentDto;
import com.vybe.entity.Comment;
import com.vybe.entity.User;
import com.vybe.service.CommentService;
import com.vybe.service.Fmt;
import com.vybe.service.UserService;
import com.vybe.service.VideoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentApiController {

    private final CommentService comments;
    private final UserService users;
    private final VideoService videos;
    private final Fmt fmt;

    public CommentApiController(CommentService comments, UserService users, VideoService videos, Fmt fmt) {
        this.comments = comments;
        this.users = users;
        this.videos = videos;
        this.fmt = fmt;
    }

    @GetMapping("/{id}/replies")
    public List<CommentDto> replies(@PathVariable Long id) {
        User me = users.current().orElse(null);
        Comment parent = comments.get(id);
        videos.getViewable(parent.getVideo().getId(), me);
        return comments.replies(parent).stream().map(c -> CommentDto.of(c, me, fmt)).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        comments.delete(users.requireCurrent(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pin")
    public CommentDto pin(@PathVariable Long id) {
        User me = users.requireCurrent();
        return CommentDto.of(comments.togglePin(me, id), me, fmt);
    }

    @PostMapping("/{id}/heart")
    public CommentDto heart(@PathVariable Long id) {
        User me = users.requireCurrent();
        return CommentDto.of(comments.toggleHeart(me, id), me, fmt);
    }
}
