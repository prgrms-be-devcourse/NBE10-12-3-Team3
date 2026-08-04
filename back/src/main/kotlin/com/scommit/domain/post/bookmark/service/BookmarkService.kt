package com.scommit.domain.post.bookmark.service

import com.scommit.domain.post.bookmark.entity.Bookmark
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.entity.PublishStatus
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
            postRepository.findByIdAndDeletedAtIsNull(postId)
                ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val isOwner = post.user.id == actor.id
        if (post.publishStatus != PublishStatus.PUBLIC && !isOwner) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }

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
        val page = bookmarkRepository.findByUserIdAndPostDeletedAtIsNull(actorId, pageable)

        // 항목마다 existsByPostIdAndUserId를 부르는 N+1 대신 postId 목록으로 한 번에 조회한다.
        val postIds = page.content.mapNotNull { it.post.id }
        val likedPostIds =
            if (postIds.isEmpty()) {
                emptySet()
            } else {
                likeRepository.findPostIdsByPostIdInAndUserId(postIds, actorId).toSet()
            }

        return page.map { bookmark ->
            PostListResponse(
                bookmark.post,
                likedPostIds.contains(bookmark.post.id),
                true,
            )
        }
    }

    @Transactional
    fun deleteBookmark(
        postId: Long,
        actor: User,
    ) {
        postRepository.findByIdAndDeletedAtIsNull(postId)
            ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

        val actorId = requireNotNull(actor.id)
        val deletedCount = bookmarkRepository.deleteByPostIdAndUserId(postId, actorId)
        if (deletedCount == 0) {
            throw BusinessException(ErrorCode.BOOKMARK_NOT_FOUND)
        }
        postRepository.decreaseBookmarkCount(postId)
    }
}
