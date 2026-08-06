package com.scommit.domain.post.post.service

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.subscription.subscription.entity.SubscriptionTier
import com.scommit.domain.subscription.subscription.repository.SubscriptionRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.springframework.stereotype.Component

// PRIVATE/PAID 게시글의 소유자·구독자 판정을 한 곳에 모아둔 컴포넌트.
// Post 본문(PostService.getPost)과 미디어(PostMediaService)가 각자 같은 판정 로직을
// 복붙해서 쓰다가 한쪽에 반영을 빠뜨려 접근 제어가 새는 사고(TRIPLES-54)가 있었기 때문에,
// "누가 볼 수 있는가"의 판정 자체는 여기 하나로 모으고, 판정 결과를 어떻게 쓸지
// (완전 차단 vs 잠금 표시)는 호출부(도메인별 서비스)에 맡긴다.
@Component
class PostAccessGuard(
    private val subscriptionRepository: SubscriptionRepository,
) {
    fun isOwner(
        post: Post,
        actor: User?,
    ): Boolean = actor != null && post.user.id == actor.id

    fun isPaidMember(
        post: Post,
        actor: User?,
    ): Boolean =
        actor != null &&
            subscriptionRepository
                .findByUserIdAndCreatorId(checkNotNull(actor.id), checkNotNull(post.user.id))
                ?.tier == SubscriptionTier.MEMBERSHIP

    // PRIVATE 게시글은 작성자가 아니면 예외 없이 완전 차단.
    fun blockIfPrivate(
        post: Post,
        actor: User?,
    ) {
        if (isPrivateBlocked(post, isOwner(post, actor))) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }
    }

    // 본문처럼 "미리보기/잠금 표시" 개념이 없는 리소스(미디어 등)를 위한 완전 차단 버전.
    // PRIVATE은 작성자만, PAID는 작성자·멤버십 구독자만 통과시키고 나머지는 전부 차단한다.
    fun enforceFullAccess(
        post: Post,
        actor: User?,
    ) {
        val owner = isOwner(post, actor)
        if (isPrivateBlocked(post, owner) || isPaidBlocked(post, actor, owner)) {
            throw BusinessException(ErrorCode.ACCESS_DENIED)
        }
    }

    private fun isPrivateBlocked(
        post: Post,
        isOwner: Boolean,
    ): Boolean = post.publishStatus == PublishStatus.PRIVATE && !isOwner

    private fun isPaidBlocked(
        post: Post,
        actor: User?,
        isOwner: Boolean,
    ): Boolean = post.accessLevel == PostAccessLevel.PAID && !isOwner && !isPaidMember(post, actor)
}
