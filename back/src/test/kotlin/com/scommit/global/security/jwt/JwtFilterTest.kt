package com.scommit.global.security.jwt

import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.service.UserService
import com.scommit.global.security.JsonUtility
import com.scommit.global.security.SecurityHelper
import com.scommit.global.security.SecurityUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.ObjectMapper
import java.time.Duration

private fun <T> eqKotlin(value: T): T {
    eq(value)
    return value
}

@ExtendWith(MockitoExtension::class)
class JwtFilterTest {
    @Mock
    private lateinit var securityHelper: SecurityHelper

    @Mock
    private lateinit var userService: UserService

    private lateinit var jwtProvider: JwtProvider
    private lateinit var jwtFilter: JwtFilter

    @BeforeEach
    fun setUp() {
        jwtProvider = JwtProvider(authTokenProperties(SECRET, EXPIRATION))
        val jsonUtility = JsonUtility(ObjectMapper())
        jwtFilter = JwtFilter(jwtProvider, securityHelper, userService, jsonUtility)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("유효한 토큰이 Authorization 헤더에 있으면 SecurityContext에 인증 정보가 채워진다")
    fun validToken_setsAuthentication() {
        val token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer refresh-token-placeholder $token")
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        val auth = requireNotNull(SecurityContextHolder.getContext().authentication)
        assertThat(auth.principal).isInstanceOf(SecurityUser::class.java)
        assertThat((auth.principal as SecurityUser).id).isEqualTo(1L)
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 SecurityContext가 비어있고 다음 필터로 넘어간다")
    fun noAuthorizationHeader_doesNotSetAuthentication_andChainProceeds() {
        given(securityHelper.getHeader("Authorization", "")).willReturn("")
        given(securityHelper.getCookieValue("refreshToken", "")).willReturn("")
        given(securityHelper.getCookieValue("accessToken", "")).willReturn("")
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        assertThat(chain.request).isNotNull() // chain.doFilter()가 호출됐음을 확인
    }

    @Test
    @DisplayName("액세스 토큰이 만료됐어도 유효한 리프레시 토큰이 있으면 리프레시 토큰으로 재인증하고 다음 필터로 넘어간다")
    fun expiredAccessToken_withValidRefreshToken_reAuthenticatesAndChainProceeds() {
        val expiredProvider = JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)))
        val expiredAccessToken = expiredProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)
        val refreshToken = "valid-refresh-token"
        val user = User(1L, "user@test.com", "nickname", UserRole.USER)
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer $refreshToken $expiredAccessToken")
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user)
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        val auth = requireNotNull(SecurityContextHolder.getContext().authentication)
        assertThat((auth.principal as SecurityUser).email).isEqualTo("user@test.com")
        assertThat(chain.request).isNotNull() // chain.doFilter()가 호출됐음을 확인
    }

    @Test
    @DisplayName("액세스 토큰이 만료되면 새 액세스 토큰을 발급해 쿠키와 Authorization 헤더에 재설정한다")
    fun expiredAccessToken_withValidRefreshToken_reissuesNewAccessTokenViaCookieAndHeader() {
        val expiredProvider = JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)))
        val expiredAccessToken = expiredProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER)
        val refreshToken = "valid-refresh-token"
        val user = User(1L, "user@test.com", "nickname", UserRole.USER)
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer $refreshToken $expiredAccessToken")
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user)
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        val cookieCaptor = ArgumentCaptor.forClass(String::class.java)
        val headerCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(securityHelper).setCookie(eqKotlin("accessToken"), cookieCaptor.capture())
        verify(securityHelper).setHeader(eqKotlin("Authorization"), headerCaptor.capture())

        val newAccessToken = cookieCaptor.value
        assertThat(newAccessToken).isEqualTo(headerCaptor.value)
        assertThat(newAccessToken).isNotEqualTo(expiredAccessToken)

        val payload = jwtProvider.parseAccessToken(newAccessToken)
        assertThat(payload.id).isEqualTo(1L)
        assertThat(payload.email).isEqualTo("user@test.com")
        assertThat(payload.nickname).isEqualTo("nickname")
        assertThat(payload.role).isEqualTo(UserRole.USER)
    }

    @Test
    @DisplayName("쿠키로 전달된 액세스 토큰이 만료되어도 리프레시 토큰이 유효하면 새 액세스 토큰을 재발급한다")
    fun expiredAccessToken_fromCookies_alsoReissuesNewAccessToken() {
        val expiredProvider = JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)))
        val expiredAccessToken = expiredProvider.generateAccessToken(2L, "cookie@test.com", "cookieUser", UserRole.USER)
        val refreshToken = "valid-refresh-token-cookie"
        val user = User(2L, "cookie@test.com", "cookieUser", UserRole.USER)
        given(securityHelper.getHeader("Authorization", "")).willReturn("")
        given(securityHelper.getCookieValue("refreshToken", "")).willReturn(refreshToken)
        given(securityHelper.getCookieValue("accessToken", "")).willReturn(expiredAccessToken)
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user)
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        val cookieCaptor = ArgumentCaptor.forClass(String::class.java)
        verify(securityHelper).setCookie(eqKotlin("accessToken"), cookieCaptor.capture())
        verify(securityHelper).setHeader(eqKotlin("Authorization"), anyString())
        assertThat(cookieCaptor.value).isNotEqualTo(expiredAccessToken)
    }

    @Test
    @DisplayName("액세스 토큰이 변조되어 파싱에 실패해도 리프레시 토큰이 유효하면 새 액세스 토큰을 재발급한다")
    fun malformedAccessToken_withValidRefreshToken_reissuesNewAccessToken() {
        val malformedAccessToken = "this.is.not-a-valid-jwt"
        val refreshToken = "valid-refresh-token-malformed"
        val user = User(3L, "malformed@test.com", "malformedUser", UserRole.USER)
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer $refreshToken $malformedAccessToken")
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user)
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        verify(securityHelper).setCookie(eqKotlin("accessToken"), anyString())
        verify(securityHelper).setHeader(eqKotlin("Authorization"), anyString())
    }

    @Test
    @DisplayName("액세스 토큰 없이 리프레시 토큰만 있으면 재인증은 되지만 액세스 토큰은 재발급되지 않는다")
    fun onlyRefreshToken_noAccessToken_doesNotReissueAccessToken() {
        val refreshToken = "valid-refresh-token-only"
        val user = User(4L, "only@test.com", "onlyUser", UserRole.USER)
        given(securityHelper.getHeader("Authorization", "")).willReturn("")
        given(securityHelper.getCookieValue("refreshToken", "")).willReturn(refreshToken)
        given(securityHelper.getCookieValue("accessToken", "")).willReturn("")
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user)
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        verify(securityHelper, never()).setCookie(eqKotlin("accessToken"), anyString())
        verify(securityHelper, never()).setHeader(eqKotlin("Authorization"), anyString())
        assertThat(SecurityContextHolder.getContext().authentication).isNotNull()
    }

    @Test
    @DisplayName("액세스 토큰이 만료되고 리프레시 토큰마저 유효하지 않으면 401을 강제하지 않고 익명 요청으로 다음 필터에 넘긴다")
    fun expiredAccessToken_withInvalidRefreshToken_proceedsAnonymously() {
        val expiredProvider = JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)))
        val expiredAccessToken =
            expiredProvider.generateAccessToken(
                5L,
                "invalid@test.com",
                "invalidUser",
                UserRole.USER,
            )
        val refreshToken = "invalid-refresh-token"
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer $refreshToken $expiredAccessToken")
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(null)
        val request = MockHttpServletRequest().apply { requestURI = "/api/test" }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        jwtFilter.doFilterInternal(request, response, chain)

        verify(securityHelper, never()).setCookie(eqKotlin("accessToken"), anyString())
        verify(securityHelper, never()).setHeader(eqKotlin("Authorization"), anyString())
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
        // chain.doFilter()가 호출됐음을 확인
        assertThat(chain.request).isNotNull()
    }

    companion object {
        // JwtProviderTest와 동일한 시크릿 키 사용 (application-test.yml의 jwt.secretKey와 동일)
        private const val SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdW5pdC10ZXN0aW5nLW1pbmltdW0tMzJieXRlcy1yZXF1aXJlZA=="
        private val EXPIRATION: Duration = Duration.ofMinutes(30)

        private fun authTokenProperties(
            secretKey: String,
            expiration: Duration,
        ) = AuthTokenProperties(
            // cookieMaxAge/refreshToken은 이 테스트 경로에서 쓰이지 않지만 Kotlin에서 non-null 타입이라 더미 값이 필요하다.
            AuthTokenProperties.AccessToken(secretKey, expiration, Duration.ZERO),
            AuthTokenProperties.RefreshToken(Duration.ZERO),
        )
    }
}
