package com.scommit.domain.post.post.service

import com.scommit.domain.notification.notification.service.NotificationService
import com.scommit.domain.post.bookmark.repository.BookmarkRepository
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.repository.PostMediaRepository
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
        private val notificationService: NotificationService,
        private val likeRepository: LikeRepository,
        private val bookmarkRepository: BookmarkRepository,
        private val postAccessGuard: PostAccessGuard,
        private val postMediaRepository: PostMediaRepository,
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
                val isOwner = actor?.id == creatorId
                val slice =
                    if (isOwner) {
                        postRepository.findSliceByUserAndDeletedAtIsNull(creator, pageable)
                    } else {
                        postRepository.findSliceByUserAndDeletedAtIsNullAndPublishStatus(
                            creator,
                            PublishStatus.PUBLIC,
                            pageable,
                        )
                    }
                val liked = likedPostIds(slice.content, actor)
                val bookmarked = bookmarkedPostIds(slice.content, actor)
                val thumbnails = getThumbnailMap(slice.content)
                return slice.map { post ->
                    PostListResponse(post, liked.contains(post.id), bookmarked.contains(post.id), thumbnails[post.id])
                }
            }
            val slice = postRepository.findAllByDeletedAtIsNullAndPublishStatus(PublishStatus.PUBLIC, pageable)
            val liked = likedPostIds(slice.content, actor)
            val bookmarked = bookmarkedPostIds(slice.content, actor)
            val thumbnails = getThumbnailMap(slice.content)
            return slice.map { post ->
                PostListResponse(post, liked.contains(post.id), bookmarked.contains(post.id), thumbnails[post.id])
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

            // PRIVATE 게시글은 작성자만 접근 가능
            postAccessGuard.blockIfPrivate(post, actor)

            // DRAFT 게시글은 작성자만 접근 가능 (TRIPLES-32)
            if (post.publishStatus == PublishStatus.DRAFT && actor?.id != post.user.id) {
                throw BusinessException(ErrorCode.ACCESS_DENIED)
            }

            post.increaseViewCount()

            val thumbnailUrl = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)?.media?.url

            // PAID 게시글은 작성자 또는 멤버십 구독자만 본문 열람 가능 (그 외는 잠금 표시로 응답)
            if (post.accessLevel == PostAccessLevel.PAID &&
                !postAccessGuard.isOwner(post, actor) &&
                !postAccessGuard.isPaidMember(post, actor)
            ) {
                return PostResponse(post, true, isLiked(id, actor), isBookmarked(id, actor), thumbnailUrl)
            }

            return PostResponse(post, false, isLiked(id, actor), isBookmarked(id, actor), thumbnailUrl)
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

            val thumbnailUrl = postMediaRepository.findByPostAndType(post, PostMediaType.THUMBNAIL)?.media?.url

            return PostResponse(post, false, isLiked(id, actor), isBookmarked(id, actor), thumbnailUrl)
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

            // softDelete()의 변경을 먼저 만들어 둔다 — deleteAllByPostId가
            // flushAutomatically=true라 이 시점의 더티 상태를 자동으로 먼저 flush한 뒤
            // clearAutomatically=true로 컨텍스트를 비운다. 순서를 바꾸면 post가 clear로 detach된
            // 뒤 softDelete()를 호출하는 꼴이 되어 변경이 flush 없이 사라진다(과거 delete flush 회귀와 동일한 함정).
            post.softDelete()
            likeRepository.deleteAllByPostId(id)
            bookmarkRepository.deleteAllByPostId(id)
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
            val isOwner = actor?.id == userId
            val page =
                if (isOwner) {
                    postRepository.findByUserAndDeletedAtIsNull(user, pageable)
                } else {
                    postRepository.findByUserAndDeletedAtIsNullAndPublishStatus(user, PublishStatus.PUBLIC, pageable)
                }
            val liked = likedPostIds(page.content, actor)
            val bookmarked = bookmarkedPostIds(page.content, actor)
            val thumbnails = getThumbnailMap(page.content)
            return page.map { post ->
                PostListResponse(post, liked.contains(post.id), bookmarked.contains(post.id), thumbnails[post.id])
            }
        }

        // 키워드 검색
        fun searchPosts(
            keyword: String,
            pageable: Pageable,
        ): Page<PostListResponse> {
            val page = postRepository.searchByKeyword(keyword, PublishStatus.PUBLIC, pageable)
            val thumbnails = getThumbnailMap(page.content)
            return page.map { post -> PostListResponse(post, false, false, thumbnails[post.id]) }
        }

        // 로그인 유저의 게시글 조회
        fun getMyPosts(
            actor: User,
            pageable: Pageable,
        ): Page<PostListResponse> {
            val page = postRepository.findByUserAndDeletedAtIsNull(actor, pageable)
            val liked = likedPostIds(page.content, actor)
            val bookmarked = bookmarkedPostIds(page.content, actor)
            val thumbnails = getThumbnailMap(page.content)
            return page.map { post ->
                PostListResponse(post, liked.contains(post.id), bookmarked.contains(post.id), thumbnails[post.id])
            }
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
        ): List<PostListResponse> {
            seriesRepository.findByIdAndDeletedAtIsNull(seriesId)
                ?: throw BusinessException(ErrorCode.SERIES_NOT_FOUND)

            val posts = postRepository.findBySeriesIdAndDeletedAtIsNull(seriesId)
            val liked = likedPostIds(posts, actor)
            val bookmarked = bookmarkedPostIds(posts, actor)
            val thumbnails = getThumbnailMap(posts)
            return posts.map { post ->
                PostListResponse(post, liked.contains(post.id), bookmarked.contains(post.id), thumbnails[post.id])
            }
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

            notificationService.notifyNewPost(subscriberIds, post.user.nickname, post.id)
        }

        private fun isLiked(
            postId: Long,
            actor: User?,
        ): Boolean = actor != null && likeRepository.existsByPostIdAndUserId(postId, checkNotNull(actor.id))

        private fun isBookmarked(
            postId: Long,
            actor: User?,
        ): Boolean = actor != null && bookmarkRepository.existsByPostIdAndUserId(postId, checkNotNull(actor.id))

        // 목록 조회용 — 항목마다 existsByPostIdAndUserId를 부르는 N+1 대신 postId 목록으로 한 번에 조회한다.
        private fun likedPostIds(
            posts: List<Post>,
            actor: User?,
        ): Set<Long> {
            if (actor == null || posts.isEmpty()) return emptySet()
            val postIds = posts.mapNotNull { it.id }
            return likeRepository.findPostIdsByPostIdInAndUserId(postIds, checkNotNull(actor.id)).toSet()
        }

        private fun bookmarkedPostIds(
            posts: List<Post>,
            actor: User?,
        ): Set<Long> {
            if (actor == null || posts.isEmpty()) return emptySet()
            val postIds = posts.mapNotNull { it.id }
            return bookmarkRepository.findPostIdsByPostIdInAndUserId(postIds, checkNotNull(actor.id)).toSet()
        }

        private fun getThumbnailMap(posts: List<Post>): Map<Long, String> {
            if (posts.isEmpty()) return emptyMap()
            val mediaList = postMediaRepository.findByPostInAndType(posts, PostMediaType.THUMBNAIL)
            return mediaList
                .mapNotNull { pm ->
                    val postId = pm.post.id
                    val url = pm.media.url
                    if (postId != null && url != null) postId to url else null
                }.toMap()
        }
    }
