package com.scommit.domain.post.bookmark.service;

import com.scommit.domain.post.bookmark.entity.Bookmark;
import com.scommit.domain.post.bookmark.repository.BookmarkRepository;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.dto.PostListResponse;
import com.scommit.domain.post.post.entity.Post;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public void createBookmark(Long postId, User actor) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        try {
            bookmarkRepository.save(new Bookmark(post, actor));
            postRepository.increaseBookmarkCount(postId);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_BOOKMARKED);
        }
    }

    @Transactional(readOnly = true)
    public Page<PostListResponse> getMyBookmarks(User actor, Pageable pageable) {
        return bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(actor.getId(), pageable)
                .map(bookmark -> new PostListResponse(
                        bookmark.getPost(),
                        likeRepository.existsByPostIdAndUserId(bookmark.getPost().getId(), actor.getId()),
                        true
                ));
    }

    @Transactional
    public void deleteBookmark(Long postId, User actor) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Bookmark bookmark = bookmarkRepository.findByPostIdAndUserId(postId, actor.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND));
        bookmarkRepository.delete(bookmark);
        postRepository.decreaseBookmarkCount(postId);
    }
}
