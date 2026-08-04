package com.scommit.global.security.jwt

import com.scommit.domain.user.user.entity.UserRole
import io.jsonwebtoken.ExpiredJwtException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class JwtProviderTest {
    private lateinit var jwtProvider: JwtProvider

    @BeforeEach
    fun setUp() {
        jwtProvider = JwtProvider(authTokenProperties(SECRET, EXPIRATION))
    }

    @Test
    @DisplayName("generateAccessToken: 토큰 문자열을 반환한다")
    fun generateAccessToken_returnsNonEmptyToken() {
        val token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)

        assertThat(token).isNotNull().isNotEmpty()
    }

    @Nested
    @DisplayName("parseAccessToken: 발급한 토큰의 클레임이 그대로 복원된다")
    inner class ParseAccessToken {
        @Test
        @DisplayName("id 클레임에 userId가 저장된다")
        fun id_containsUserId() {
            val userId = 42L
            val token = jwtProvider.generateAccessToken(userId, "user@test.com", "nickname", UserRole.USER)

            val payload = jwtProvider.parseAccessToken(token)

            assertThat(payload.id).isEqualTo(userId)
        }

        @Test
        @DisplayName("email 클레임이 저장되고 그대로 복원된다")
        fun email_isRestored() {
            val token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)

            val payload = jwtProvider.parseAccessToken(token)

            assertThat(payload.email).isEqualTo("user@test.com")
        }

        @Test
        @DisplayName("nickname 클레임이 저장되고 그대로 복원된다")
        fun nickname_isRestored() {
            val token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)

            val payload = jwtProvider.parseAccessToken(token)

            assertThat(payload.nickname).isEqualTo("nickname")
        }

        @Test
        @DisplayName("role 클레임이 저장되고 UserRole enum으로 복원된다 - USER")
        fun role_restoredAsEnum_user() {
            val token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)

            val payload = jwtProvider.parseAccessToken(token)

            assertThat(payload.role).isEqualTo(UserRole.USER)
        }

        @Test
        @DisplayName("role 클레임이 저장되고 UserRole enum으로 복원된다 - ADMIN")
        fun role_restoredAsEnum_admin() {
            val token = jwtProvider.generateAccessToken(1L, "admin@test.com", "admin", UserRole.ADMIN)

            val payload = jwtProvider.parseAccessToken(token)

            assertThat(payload.role).isEqualTo(UserRole.ADMIN)
        }
    }

    @Test
    @DisplayName("parseAccessToken: 만료된 토큰은 ExpiredJwtException을 던진다")
    fun parseAccessToken_expired_throwsExpiredJwtException() {
        val expiredProvider = JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)))
        val expiredToken = expiredProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)

        assertThatThrownBy { expiredProvider.parseAccessToken(expiredToken) }
            .isInstanceOf(ExpiredJwtException::class.java)
    }

    companion object {
        // HS256은 최소 32바이트(256비트) 키 필요
        private const val SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdW5pdC10ZXN0aW5nLW1pbmltdW0tMzJieXRlcy1yZXF1aXJlZA=="
        private val EXPIRATION: Duration = Duration.ofHours(1)

        private fun authTokenProperties(
            secretKey: String,
            expiration: Duration,
        ) = AuthTokenProperties(
            AuthTokenProperties.AccessToken(secretKey, expiration, Duration.ofMinutes(30)),
            AuthTokenProperties.RefreshToken(Duration.ofDays(30)),
        )
    }
}
