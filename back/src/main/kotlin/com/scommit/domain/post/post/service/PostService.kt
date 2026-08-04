package com.scommit.domain.post.post.service

import com.scommit.domain.notification.notification.dto.NotificationResponse
import com.scommit.domain.notification.notification.dto.NotificationType
import com.scommit.domain.notification.notification.repository.SseEmitterRepository
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Suppress("TooManyFunctions")
@Service
@Transactional(readOnly = true)
class PostService
    @Suppress("LongParameterList")
    constructor(
        private val postRepository: PostRepository,
        private val seriesRepository: SeriesRepository,
        private val userRepository: UserRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val sseEmitterRepository: SseEmitterRepository,
        private val likeRepository: LikeRepository,
        private val bookmarkRepository: BookmarkRepository,
    ) {
        // 게시글 생성
        @Suppress("LongParameterList")
        @Transactional
        fun createPost(
            actor: User,
            title: String?,
            body: String?,
            publishStatus: PublishStatus,
            accessLevel: PostAccessLevel,
            seriesId: Long?,
        ): PostResponse {
            val series =
                seriesId?.let {
                    seriesRepository.findByIdOrNull(it) ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)
                }

            val post = Post(actor, series, title, body, publishStatus, accessLevel)

            postRepository.save(post)

            if (publishStatus == PublishStatus.PUBLIC) {
                sendSse(post)
            }

            return PostResponse(post)
        }

        // 홈페이지 전체 조회 - 무한 스크롤
        fun getPosts(
            creatorId: Long?,
            actor: User?,
            pageable: Pageable,
        ): Slice<PostListResponse> {
            if (creatorId != null) {
                val creator =
                    userRepository.findByIdAndDeletedAtIsNull(creatorId)
                        ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
                return postRepository
                    .findSliceByUserAndDeletedAtIsNull(creator, pageable)
                    .map { post ->
                        val postId = checkNotNull(post.id)
                        PostListResponse(post, isLiked(postId, actor), isBookmarked(postId, actor))
                    }
            }
            return postRepository
                .findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable)
                .map { post ->
                    val postId = checkNotNull(post.id)
                    PostListResponse(post, isLiked(postId, actor), isBookmarked(postId, actor))
                }
        }

        // 게시글 상세 조회
        @Transactional
        fun getPost(
            id: Long,
            actor: User?,
        ): PostResponse {
            val post =
                postRepository.findByIdAndDeletedAtIsNull(id)
                    ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

            val isOwner = actor != null && post.user.id == actor.id

            // PRIVATE 게시글은 작성자만 접근 가능
            if (post.publishStatus == PublishStatus.PRIVATE && !isOwner) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            post.increaseViewCount()

            // PAID 게시글은 작성자 또는 멤버십 구독자만 본문 열람 가능
            if (post.accessLevel == PostAccessLevel.PAID && !isOwner) {
                val isMember =
                    actor != null &&
                        subscriptionRepository
                            .findByUserIdAndCreatorId(checkNotNull(actor.id), checkNotNull(post.user.id))
                            ?.tier == SubscriptionTier.MEMBERSHIP
                if (!isMember) {
                    return PostResponse(post, true, isLiked(id, actor), isBookmarked(id, actor))
                }
            }

            return PostResponse(post, false, isLiked(id, actor), isBookmarked(id, actor))
        }

        // 게시글 수정
        @Suppress("LongParameterList", "ThrowsCount")
        @Transactional
        fun updatePost(
            actor: User,
            id: Long,
            title: String?,
            body: String?,
            publishStatus: PublishStatus,
            accessLevel: PostAccessLevel,
            seriesId: Long?,
        ): PostResponse {
            val post =
                postRepository.findByIdAndDeletedAtIsNull(id)
                    ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

            if (post.user.id != actor.id) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            val series =
                seriesId?.let {
                    seriesRepository.findByIdOrNull(it) ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)
                }

            val oldStatus = post.publishStatus
            post.update(title, body, publishStatus, accessLevel, series)

            if (oldStatus != PublishStatus.PUBLIC && publishStatus == PublishStatus.PUBLIC) {
                sendSse(post)
            }

            return PostResponse(post, false, isLiked(id, actor), isBookmarked(id, actor))
        }

        // 게시글 삭제
        @Transactional
        fun deletePost(
            actor: User,
            id: Long,
        ) {
            val post =
                postRepository.findByIdAndDeletedAtIsNull(id)
                    ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

            if (post.user.id != actor.id) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            post.softDelete()
        }

        // 특정 유저의 게시글 조회 - 번호 페이지네이션 (프로필 화면)
        fun getUserPosts(
            userId: Long,
            actor: User?,
            pageable: Pageable,
        ): Page<PostListResponse> {
            val user =
                userRepository.findByIdAndDeletedAtIsNull(userId)
                    ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            return postRepository
                .findByUserAndDeletedAtIsNull(user, pageable)
                .map { post ->
                    val postId = checkNotNull(post.id)
                    PostListResponse(post, isLiked(postId, actor), isBookmarked(postId, actor))
                }
        }

        // 키워드 검색
        fun searchPosts(
            keyword: String,
            pageable: Pageable,
        ): Page<PostListResponse> =
            postRepository
                .searchByKeyword(keyword, PublishStatus.PUBLIC, pageable)
                .map { post -> PostListResponse(post, false, false) }

        // 로그인 유저의 게시글 조회
        fun getMyPosts(
            actor: User,
            pageable: Pageable,
        ): Page<PostListResponse> =
            postRepository
                .findByUserAndDeletedAtIsNull(actor, pageable)
                .map { post ->
                    val postId = checkNotNull(post.id)
                    PostListResponse(post, isLiked(postId, actor), isBookmarked(postId, actor))
                }

        // 시리즈에 포스트 추가
        @Suppress("ThrowsCount")
        @Transactional
        fun addPostToSeries(
            postId: Long,
            seriesId: Long,
            actor: User,
        ) {
            val post =
                postRepository.findByIdAndDeletedAtIsNull(postId)
                    ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)
            if (post.user.id != actor.id) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            val series =
                seriesRepository.findByIdAndDeletedAtIsNull(seriesId)
                    ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)
            if (series.user.id != actor.id) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            post.updateSeries(series)
        }

        // 시리즈에서 포스트 제거
        @Suppress("ThrowsCount")
        @Transactional
        fun removePostFromSeries(
            postId: Long,
            seriesId: Long,
            actor: User,
        ) {
            val post =
                postRepository.findByIdAndDeletedAtIsNull(postId)
                    ?: throw BusinessException(ErrorCode.POST_NOT_FOUND)

            val series = post.series
            if (series == null || series.id != seriesId) {
                throw BusinessException(ErrorCode.SERIES_NOT_FOUND)
            }
            if (series.user.id != actor.id) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            post.updateSeries(null)
        }

        // 시리즈 내 게시글 조회
        fun getPostsBySeriesId(
            seriesId: Long,
            actor: User?,
        ): List<PostListResponse> =
            postRepository
                .findBySeriesIdAndDeletedAtIsNull(seriesId)
                .map { post ->
                    val postId = checkNotNull(post.id)
                    PostListResponse(post, isLiked(postId, actor), isBookmarked(postId, actor))
                }

        private fun sendSse(post: Post) {
            val creatorId: Long = checkNotNull(post.user.id)
            val subscriberIds: List<Long> =
                if (post.accessLevel == PostAccessLevel.PAID) {
                    subscriptionRepository
                        .findByCreatorIdAndTierAndDeletedAtIsNull(creatorId, SubscriptionTier.MEMBERSHIP)
                        .map { checkNotNull(it.user.id) }
                } else {
                    subscriptionRepository
                        .findByCreatorIdAndDeletedAtIsNull(creatorId)
                        .map { checkNotNull(it.user.id) }
                }

            for (subscriberId in subscriberIds) {
                sseEmitterRepository.sendToUser(
                    subscriberId,
                    NotificationResponse(
                        NotificationType.NEW_POST,
                        "${post.user.nickname}님이 새 게시글을 작성했습니다.",
                        post.id,
                    ),
                )
            }
        }

        private fun isLiked(
            postId: Long,
            actor: User?,
        ): Boolean = actor != null && likeRepository.existsByPostIdAndUserId(postId, checkNotNull(actor.id))

        private fun isBookmarked(
            postId: Long,
            actor: User?,
        ): Boolean = actor != null && bookmarkRepository.existsByPostIdAndUserId(postId, checkNotNull(actor.id))
    }
