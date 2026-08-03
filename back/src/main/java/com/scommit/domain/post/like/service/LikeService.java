package com.scommit.domain.post.like.service;

import com.scommit.domain.post.like.entity.Like;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.entity.Post;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.user.user.entity.User;
import com.scommit.global.exception.BusinessException;
import com.scommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {
    private final LikeRepository likeRepository;
    private final PostRepository postRepository;

    @Transactional
    public void createLike(Long postId, User actor) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        try {
            likeRepository.save(new Like(post, actor));
            postRepository.increaseLikeCount(postId);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
    }

    @Transactional
    public void deleteLike(Long postId, User actor) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Like like = likeRepository.findByPostIdAndUserId(postId, actor.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.LIKE_NOT_FOUND));
        likeRepository.delete(like);
        postRepository.decreaseLikeCount(postId);
    }
}
