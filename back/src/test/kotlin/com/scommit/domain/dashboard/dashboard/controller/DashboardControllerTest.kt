package com.scommit.domain.dashboard.dashboard.controller

import com.scommit.domain.dashboard.dashboard.dto.AdminDashboard
import com.scommit.domain.dashboard.dashboard.dto.AdminDashboardMetrics
import com.scommit.domain.dashboard.dashboard.dto.CreatorDashboard
import com.scommit.domain.dashboard.dashboard.dto.CreatorDashboardMetrics
import com.scommit.domain.dashboard.dashboard.dto.CreatorRadarChart
import com.scommit.domain.dashboard.dashboard.dto.Mainpage
import com.scommit.domain.dashboard.dashboard.dto.SignupTrendPoint
import com.scommit.domain.dashboard.dashboard.dto.SubscriptionRatio
import com.scommit.domain.dashboard.dashboard.dto.SuperCreator
import com.scommit.domain.dashboard.dashboard.service.CreatorDashboardService
import com.scommit.domain.dashboard.dashboard.service.PlatformDashboardService
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.security.jwt.JwtProvider
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// SecurityConfig/JwtFilter를 목이 아니라 실제로 태워서 검증한다 — 이 컨트롤러는
// role(ADMIN) 검사와 완전 공개(permitAll) 여부가 핵심 계약이라, SecurityHelper를 모킹하면
// 정작 검증하려는 실제 인가 체인(JwtFilter -> SecurityConfig)을 건너뛰게 되어 의미가 없다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var creatorDashboardService: CreatorDashboardService

    @MockitoBean
    private lateinit var platformDashboardService: PlatformDashboardService

    private fun tokenFor(role: UserRole): String = jwtProvider.generateAccessToken(1L, "actor@test.com", "액터", role)

    private fun mockCreatorDashboard() =
        CreatorDashboard(
            metrics =
                CreatorDashboardMetrics(
                    newPostsThisPeriod = 1,
                    totalPosts = 1,
                    freePosts = 1,
                    paidPosts = 0,
                    newSeriesThisPeriod = 0,
                    totalSeries = 0,
                    viewsThisPeriod = 10,
                    totalViews = 10,
                    avgViewsPerPost = 10.0,
                    likesThisPeriod = 1,
                    totalLikes = 1,
                    avgLikesPerPost = 1.0,
                    bookmarksThisPeriod = 0,
                    totalBookmarks = 0,
                    avgBookmarksPerPost = 0.0,
                    newFollowersThisPeriod = 0,
                    totalFollowers = 0,
                    membershipConversionRate = 0.0,
                    paidMembershipCount = 0,
                ),
            heatmap = emptyList(),
            radar = CreatorRadarChart(0.0, 0.0, 0.0, 0.0, 0.0),
            topPosts = emptyList(),
            topSeries = emptyList(),
        )

    private fun mockAdminDashboard() =
        AdminDashboard(
            metrics =
                AdminDashboardMetrics(
                    newUsersThisPeriod = 1,
                    totalUsers = 1,
                    newPostsThisPeriod = 1,
                    totalPosts = 1,
                    freePosts = 1,
                    paidPosts = 0,
                    newSeriesThisPeriod = 0,
                    totalSeries = 0,
                    avgPostsPerSeries = 0.0,
                    viewsThisPeriod = 10,
                    totalViews = 10,
                    avgViewsPerPost = 10.0,
                ),
            signupTrend = listOf(SignupTrendPoint("2026-08-01", 1)),
            subscriptionRatio = SubscriptionRatio(1, 1, 50.0, 50.0),
            superCreators = listOf(mockSuperCreator()),
            topPosts = emptyList(),
        )

    private fun mockSuperCreator() = SuperCreator(id = 1L, nickname = "창작자", subscriberCount = 10, followerIncrease = 2)

    private fun mockMainpage() =
        Mainpage(
            trendingCreators = listOf(mockSuperCreator()),
            popularPaidPosts = emptyList(),
            popularFreePosts = emptyList(),
        )

    @Test
    @DisplayName("GET /api/dashboard/user - 토큰 없이 요청하면 401")
    fun getUserDashboard_noToken_unauthorized() {
        mockMvc
            .perform(get("/api/dashboard/user"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("GET /api/dashboard/user - 로그인하면 본인 창작자 대시보드를 200으로 응답한다")
    fun getUserDashboard_authenticated_success() {
        given(creatorDashboardService.getCreatorDashboard(1L, "30d")).willReturn(mockCreatorDashboard())

        mockMvc
            .perform(get("/api/dashboard/user").header("Authorization", "Bearer " + tokenFor(UserRole.USER)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.metrics.totalPosts").value(1))
    }

    @Test
    @DisplayName("GET /api/dashboard/admin - 토큰 없이 요청하면 401")
    fun getAdminDashboard_noToken_unauthorized() {
        mockMvc
            .perform(get("/api/dashboard/admin"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("GET /api/dashboard/admin - ADMIN이 아니면 403")
    fun getAdminDashboard_nonAdmin_forbidden() {
        mockMvc
            .perform(get("/api/dashboard/admin").header("Authorization", "Bearer " + tokenFor(UserRole.USER)))
            .andExpect(status().isForbidden)
    }

    @Test
    @DisplayName("GET /api/dashboard/admin - ADMIN이면 플랫폼 대시보드를 200으로 응답한다")
    fun getAdminDashboard_admin_success() {
        given(platformDashboardService.getAdminDashboard("30d")).willReturn(mockAdminDashboard())

        mockMvc
            .perform(get("/api/dashboard/admin").header("Authorization", "Bearer " + tokenFor(UserRole.ADMIN)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resultCode").value("200-1"))
            .andExpect(jsonPath("$.data.superCreators[0].nickname").value("창작자"))
    }

    @Test
    @DisplayName("GET /api/dashboard/mainpage - 토큰 없이 요청해도 200 (완전 공개)")
    fun getMainpage_noToken_publicSuccess() {
        given(platformDashboardService.getMainpage()).willReturn(mockMainpage())

        mockMvc
            .perform(get("/api/dashboard/mainpage"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.trendingCreators[0].nickname").value("창작자"))
    }
}
