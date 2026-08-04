package com.scommit.global.security

import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.security.jwt.AuthTokenProperties
import com.scommit.global.security.jwt.JwtProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Value("\${auth-token.access-token.secret-key}")
    private lateinit var secretKey: String

    private fun validToken() = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)

    private fun expiredToken(): String {
        // cookieMaxAge는 이 경로에서 쓰이지 않지만 Kotlin에서 non-null Duration이라 더미 값이 필요하다.
        val properties =
            AuthTokenProperties(
                AuthTokenProperties.AccessToken(secretKey, Duration.ofMillis(-1), Duration.ZERO),
                AuthTokenProperties.RefreshToken(Duration.ZERO),
            )

        return JwtProvider(properties).generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)
    }

    @Test
    @DisplayName("/api/users/** 에 유효한 토큰으로 요청하면 인증을 통과한다 (401이 아니다)")
    fun protectedEndpoint_withValidToken_notUnauthorized() {
        mockMvc
            .perform(
                get("/api/users/profile")
                    .header("Authorization", "Bearer ${validToken()}"),
            ).andExpect { result ->
                assertThat(result.response.status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
            }
    }

    @Test
    @DisplayName("/api/users/** 에 토큰 없이 요청하면 401이다")
    fun protectedEndpoint_withNoToken_returns401() {
        mockMvc
            .perform(get("/api/users/profile"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("/api/users/** 에 만료된 토큰으로 요청하면 401이다")
    fun protectedEndpoint_withExpiredToken_returns401() {
        mockMvc
            .perform(
                get("/api/users/profile")
                    .header("Authorization", "Bearer ${expiredToken()}"),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("/api/users/login 은 토큰 없이 요청해도 401이 아니다 (permitAll)")
    fun loginEndpoint_withNoToken_notUnauthorized() {
        mockMvc
            .perform(get("/api/users/login"))
            .andExpect { result ->
                assertThat(result.response.status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
            }
    }

    @Test
    @DisplayName("공개(permitAll) 엔드포인트는 액세스 토큰이 만료되고 리프레시 토큰도 무효하면 401이 아니라 익명 요청으로 통과한다")
    fun publicEndpoint_withExpiredAccessTokenAndInvalidRefreshToken_notUnauthorized() {
        mockMvc
            .perform(
                get("/api/posts")
                    .header("Authorization", "Bearer invalid-refresh-token ${expiredToken()}"),
            ).andExpect { result ->
                assertThat(result.response.status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
            }
    }
}
