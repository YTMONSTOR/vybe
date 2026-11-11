package com.vybe.service;

import com.vybe.entity.Comment;
import com.vybe.entity.User;
import com.vybe.entity.Video;
import com.vybe.exception.BadRequestException;
import com.vybe.exception.ForbiddenException;
import com.vybe.exception.NotFoundException;
import com.vybe.repository.CommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository comments;

    public CommentService(CommentRepository comments) {
        this.comments = comments;
    }

    public Comment get(Long id) {
        return comments.findById(id).orElseThrow(() -> new NotFoundException("Комментарий не найден"));
    }

    public List<Comment> roots(Video v, int page, int size) {
        return comments.findByVideoAndParentIsNullOrderByPinnedDescCreatedAtDesc(v, PageRequest.of(page, size));
    }

    public List<Comment> replies(Comment parent) {
        return comments.findByParentOrderByCreatedAtAsc(parent);
    }

    public Page<Comment> forCreator(User creator, Long videoId, int page) {
        PageRequest pr = PageRequest.of(page, 30);
        return videoId == null ? comments.findForCreator(creator, pr) : comments.findForCreatorVideo(creator, videoId, pr);
    }

    @Transactional
    public Comment add(User author, Video video, String text, Long parentId) {
        if (!video.isCommentsEnabled()) throw new BadRequestException("Комментарии к этому видео отключены");
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new BadRequestException("Комментарий не может быть пустым");
        if (t.length() > 2000) throw new BadRequestException("Комментарий не длиннее 2000 символов");
        Comment c = new Comment();
        c.setAuthor(author);
        c.setVideo(video);
        c.setText(t);
        if (parentId != null) {
            Comment parent = get(parentId);
            if (!parent.getVideo().getId().equals(video.getId())) throw new BadRequestException("Неверный комментарий");
            Comment root = parent.getParent() != null ? parent.getParent() : parent;
            c.setParent(root);
            root.setReplyCount(root.getReplyCount() + 1);
        }
        video.setCommentCount(video.getCommentCount() + 1);
        return comments.save(c);
    }

    public boolean canDelete(User user, Comment c) {
        return user != null && (user.isAdmin() || c.getAuthor().getId().equals(user.getId())
                || c.getVideo().getAuthor().getId().equals(user.getId()));
    }

    @Transactional
    public void delete(User user, Long id) {
        Comment c = get(id);
        if (!canDelete(user, c)) throw new ForbiddenException("Нельзя удалить этот комментарий");
        Video v = c.getVideo();
        int removed = 1;
        if (c.getParent() == null) {
            removed += comments.deleteReplies(c);
        } else {
            Comment root = c.getParent();
            root.setReplyCount(Math.max(0, root.getReplyCount() - 1));
        }
        comments.delete(c);
        v.setCommentCount(Math.max(0, v.getCommentCount() - removed));
    }

    @Transactional
    public Comment togglePin(User user, Long id) {
        Comment c = requireCreator(user, id);
        if (c.getParent() != null) throw new BadRequestException("Закрепить можно только основной комментарий");
        boolean pin = !c.isPinned();
        if (pin) comments.unpinAll(c.getVideo());
        c = get(id);
        c.setPinned(pin);
        return c;
    }

    @Transactional
    public Comment toggleHeart(User user, Long id) {
        Comment c = requireCreator(user, id);
        c.setHearted(!c.isHearted());
        return c;
    }

    private Comment requireCreator(User user, Long id) {
        Comment c = get(id);
        if (user == null || !(user.isAdmin() || c.getVideo().getAuthor().getId().equals(user.getId()))) {
            throw new ForbiddenException("Только автор видео может это сделать");
        }
        return c;
    }
}
