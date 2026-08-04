package com.scommit.global.security

import com.scommit.global.security.jwt.AuthTokenProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration

class SecurityHelperTest {
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse
    private lateinit var securityHelper: SecurityHelper

    @BeforeEach
    fun setUp() {
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()

        val authTokenProperties =
            AuthTokenProperties(
                AuthTokenProperties.AccessToken("secret", Duration.ofMinutes(30), ACCESS_TOKEN_COOKIE_MAX_AGE),
                AuthTokenProperties.RefreshToken(REFRESH_TOKEN_COOKIE_MAX_AGE),
            )

        securityHelper = SecurityHelper(request, response, authTokenProperties)
        ReflectionTestUtils.setField(securityHelper, "cookieDomain", "localhost")
    }

    @Test
    @DisplayName("accessToken 쿠키는 access-token.cookie-max-age를 max-age로 사용한다")
    fun setCookie_accessToken_usesAccessTokenCookieMaxAge() {
        securityHelper.setCookie("accessToken", "access-token-value")

        val cookie = requireNotNull(response.getCookie("accessToken"))
        assertThat(cookie.maxAge).isEqualTo(ACCESS_TOKEN_COOKIE_MAX_AGE.toSeconds().toInt())
    }

    @Test
    @DisplayName("refreshToken 쿠키는 refresh-token.cookie-max-age를 max-age로 사용한다")
    fun setCookie_refreshToken_usesRefreshTokenCookieMaxAge() {
        securityHelper.setCookie("refreshToken", "refresh-token-value")

        val cookie = requireNotNull(response.getCookie("refreshToken"))
        assertThat(cookie.maxAge).isEqualTo(REFRESH_TOKEN_COOKIE_MAX_AGE.toSeconds().toInt())
    }

    @Test
    @DisplayName("accessToken과 refreshToken 쿠키의 max-age는 서로 다르다")
    fun setCookie_accessTokenAndRefreshToken_haveDifferentMaxAge() {
        securityHelper.setCookie("accessToken", "access-token-value")
        securityHelper.setCookie("refreshToken", "refresh-token-value")

        val accessTokenMaxAge = requireNotNull(response.getCookie("accessToken")).maxAge
        val refreshTokenMaxAge = requireNotNull(response.getCookie("refreshToken")).maxAge

        assertThat(accessTokenMaxAge).isNotEqualTo(refreshTokenMaxAge)
    }

    @Test
    @DisplayName("값이 비어있으면 쿠키 이름과 무관하게 max-age를 0으로 설정해 즉시 만료시킨다")
    fun setCookie_blankValue_setsMaxAgeToZero() {
        securityHelper.setCookie("refreshToken", "")

        val cookie = requireNotNull(response.getCookie("refreshToken"))
        assertThat(cookie.maxAge).isZero()
    }

    @Test
    @DisplayName("getAccessTokenCookieExpiresInSecond는 access-token.cookie-max-age를 초 단위로 반환한다")
    fun getAccessTokenCookieExpiresInSecond_returnsAccessTokenCookieMaxAgeInSeconds() {
        val expiresInSecond = securityHelper.accessTokenCookieExpiresInSecond

        assertThat(expiresInSecond).isEqualTo(ACCESS_TOKEN_COOKIE_MAX_AGE.toSeconds().toInt())
    }

    companion object {
        private val ACCESS_TOKEN_COOKIE_MAX_AGE: Duration = Duration.ofMinutes(30)
        private val REFRESH_TOKEN_COOKIE_MAX_AGE: Duration = Duration.ofDays(30)
    }
}
