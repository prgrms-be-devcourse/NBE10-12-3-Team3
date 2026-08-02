package com.scommit.domain.post.bookmark.service

import com.scommit.domain.post.bookmark.entity.Bookmark
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val postRepository: PostRepository,
    private val likeRepository: LikeRepository,
) {
    @Transactional
    fun createBookmark(
        postId: Long,
        actor: User,
    ) {
        val post =
            postRepository
                .findByIdAndDeletedAtIsNull(postId)
                .orElseThrow { BusinessException(ErrorCode.POST_NOT_FOUND) }

        try {
            bookmarkRepository.save(Bookmark(post, actor))
            postRepository.increaseBookmarkCount(postId)
        } catch (ignored: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.ALREADY_BOOKMARKED)
        }
    }

    @Transactional(readOnly = true)
    fun getMyBookmarks(
        actor: User,
        pageable: Pageable,
    ): Page<PostListResponse> {
        val actorId = requireNotNull(actor.id)
        return bookmarkRepository
            .findByUserIdAndPostDeletedAtIsNull(actorId, pageable)
            .map { bookmark ->
                PostListResponse(
                    bookmark.post,
                    likeRepository.existsByPostIdAndUserId(requireNotNull(bookmark.post.id), actorId),
                    true,
                )
            }
    }

    @Transactional
    fun deleteBookmark(
        postId: Long,
        actor: User,
    ) {
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow { BusinessException(ErrorCode.POST_NOT_FOUND) }

        val bookmark =
            bookmarkRepository.findByPostIdAndUserId(postId, requireNotNull(actor.id))
                ?: throw BusinessException(ErrorCode.BOOKMARK_NOT_FOUND)
        bookmarkRepository.delete(bookmark)
        postRepository.decreaseBookmarkCount(postId)
    }
}
