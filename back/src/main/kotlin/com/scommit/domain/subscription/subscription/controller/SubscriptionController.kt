package com.scommit.domain.subscription.subscription.controller

import com.scommit.domain.subscription.subscription.dto.SubscriptionResponse
import com.scommit.domain.subscription.subscription.dto.SubscriptionStatusResponse
import com.scommit.domain.subscription.subscription.service.SubscriptionService
import com.scommit.global.dto.PageResponse
import com.scommit.global.dto.RsData
import com.scommit.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Subscription", description = "구독 및 멤버십 관련 API")
@RestController
@RequestMapping("/api/subscriptions")
class SubscriptionController(
    private val subscriptionService: SubscriptionService
) {

    @Operation(summary = "창작자 팔로우", description = "특정 창작자를 팔로우합니다.")
    @PostMapping("/follow/{creatorId}")
    fun follow(
        @PathVariable("creatorId") creatorId: Long,
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<Void> {
        subscriptionService.follow(user.id, creatorId)
        return RsData("200-1", "팔로우 성공")
    }

    @Operation(summary = "창작자 언팔로우", description = "팔로우 중인 창작자를 언팔로우합니다.")
    @DeleteMapping("/follow/{creatorId}")
    fun unfollow(
        @PathVariable("creatorId") creatorId: Long,
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<Void> {
        subscriptionService.unfollow(user.id, creatorId)
        return RsData("200-1", "언팔로우 성공")
    }

    @Operation(summary = "멤버십 가입", description = "창작자의 멤버십에 가입합니다. (팔로우 자동 처리)")
    @PostMapping("/membership/{creatorId}")
    fun joinMembership(
        @PathVariable("creatorId") creatorId: Long,
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<Void> {
        subscriptionService.joinMembership(user.id, creatorId)
        return RsData("200-1", "멤버십 가입 성공")
    }

    @Operation(summary = "멤버십 해지", description = "가입 중인 멤버십을 해지하고 팔로우 상태로 돌아갑니다.")
    @DeleteMapping("/membership/{creatorId}")
    fun cancelMembership(
        @PathVariable("creatorId") creatorId: Long,
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<Void> {
        subscriptionService.cancelMembership(user.id, creatorId)
        return RsData("200-1", "멤버십 해지 성공")
    }

    @Operation(summary = "내 구독 목록 조회", description = "내가 팔로우 또는 멤버십 구독 중인 창작자 목록을 조회합니다.")
    @GetMapping
    fun getMySubscriptions(
        @AuthenticationPrincipal user: SecurityUser,
        @PageableDefault(size = 10) pageable: Pageable
    ): RsData<PageResponse<SubscriptionResponse>> {
        val infoPage = subscriptionService.getMySubscriptions(user.id, pageable)
        val responsePage = infoPage.map { SubscriptionResponse.from(it) }
        return RsData("200-1", "내 구독 목록 조회 성공", PageResponse(responsePage))
    }

    @Operation(summary = "내 구독 총 수 조회", description = "내가 현재 구독(팔로우/멤버십) 중인 총 창작자 수를 반환합니다.")
    @GetMapping("/count")
    fun getMySubscriptionCount(
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<Long> {
        val count = subscriptionService.getMySubscriptionCount(user.id)
        return RsData("200-1", "구독 수 조회 성공", count)
    }

    @Operation(summary = "구독 상태 확인", description = "특정 창작자에 대한 현재 로그인 사용자의 구독 상태(NONE, FOLLOW, MEMBERSHIP)를 조회합니다.")
    @GetMapping("/status/{creatorId}")
    fun getSubscriptionStatus(
        @PathVariable("creatorId") creatorId: Long,
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<SubscriptionStatusResponse> {
        val status = subscriptionService.getSubscriptionStatus(user.id, creatorId)
        return RsData("200-1", "구독 상태 조회 성공", SubscriptionStatusResponse(status))
    }

    @Operation(summary = "내 팔로워 수 조회", description = "나를 팔로우(멤버십 포함)하고 있는 유저의 총 숫자를 조회합니다.")
    @GetMapping("/followers/count")
    fun getMyFollowerCount(
        @AuthenticationPrincipal user: SecurityUser
    ): RsData<Long> {
        val followerCount = subscriptionService.getFollowerCount(user.id)
        return RsData("200-1", "팔로워 수 조회 성공", followerCount)
    }
}
