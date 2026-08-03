package com.scommit.global.security.jwt;

import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.service.UserService;
import com.scommit.global.security.JsonUtility;
import com.scommit.global.security.SecurityHelper;
import com.scommit.global.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    // JwtProviderTest와 동일한 시크릿 키 사용 (application-test.yml의 jwt.secretKey와 동일)
    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdW5pdC10ZXN0aW5nLW1pbmltdW0tMzJieXRlcy1yZXF1aXJlZA==";
    private static final Duration EXPIRATION = Duration.ofMinutes(30);

    @Mock
    private SecurityHelper securityHelper;

    @Mock
    private UserService userService;

    private JwtProvider jwtProvider;
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(authTokenProperties(SECRET, EXPIRATION));
        JsonUtility jsonUtility = new JsonUtility(new tools.jackson.databind.ObjectMapper());
        jwtFilter = new JwtFilter(jwtProvider, securityHelper, userService, jsonUtility);
        SecurityContextHolder.clearContext();
    }

    private static AuthTokenProperties authTokenProperties(String secretKey, Duration expiration) {
        return new AuthTokenProperties(
                new AuthTokenProperties.AccessToken(secretKey, expiration, Duration.ofMinutes(30)),
                new AuthTokenProperties.RefreshToken(Duration.ofDays(30))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 토큰이 Authorization 헤더에 있으면 SecurityContext에 인증 정보가 채워진다")
    void validToken_setsAuthentication() throws Exception {
        String token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer refresh-token-placeholder " + token);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(SecurityUser.class);
        assertThat(((SecurityUser) auth.getPrincipal()).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 SecurityContext가 비어있고 다음 필터로 넘어간다")
    void noAuthorizationHeader_doesNotSetAuthentication_andChainProceeds() throws Exception {
        given(securityHelper.getHeader("Authorization", "")).willReturn("");
        given(securityHelper.getCookieValue("refreshToken", "")).willReturn("");
        given(securityHelper.getCookieValue("accessToken", "")).willReturn("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // chain.doFilter()가 호출됐음을 확인
    }

    @Test
    @DisplayName("액세스 토큰이 만료됐어도 유효한 리프레시 토큰이 있으면 리프레시 토큰으로 재인증하고 다음 필터로 넘어간다")
    void expiredAccessToken_withValidRefreshToken_reAuthenticatesAndChainProceeds() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)));
        String expiredAccessToken = expiredProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);
        String refreshToken = "valid-refresh-token";
        User user = new User(1L, "user@test.com", "nickname", UserRole.USER);
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer " + refreshToken + " " + expiredAccessToken);
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((SecurityUser) auth.getPrincipal()).getEmail()).isEqualTo("user@test.com");
        assertThat(chain.getRequest()).isNotNull(); // chain.doFilter()가 호출됐음을 확인
    }

    @Test
    @DisplayName("액세스 토큰이 만료되면 새 액세스 토큰을 발급해 쿠키와 Authorization 헤더에 재설정한다")
    void expiredAccessToken_withValidRefreshToken_reissuesNewAccessTokenViaCookieAndHeader() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)));
        String expiredAccessToken = expiredProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);
        String refreshToken = "valid-refresh-token";
        User user = new User(1L, "user@test.com", "nickname", UserRole.USER);
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer " + refreshToken + " " + expiredAccessToken);
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(securityHelper).setCookie(eq("accessToken"), cookieCaptor.capture());
        verify(securityHelper).setHeader(eq("Authorization"), headerCaptor.capture());

        String newAccessToken = cookieCaptor.getValue();
        assertThat(newAccessToken).isEqualTo(headerCaptor.getValue());
        assertThat(newAccessToken).isNotEqualTo(expiredAccessToken);

        JwtProvider.AccessTokenPayload payload = jwtProvider.parseAccessToken(newAccessToken);
        assertThat(payload.id()).isEqualTo(1L);
        assertThat(payload.email()).isEqualTo("user@test.com");
        assertThat(payload.nickname()).isEqualTo("nickname");
        assertThat(payload.role()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("쿠키로 전달된 액세스 토큰이 만료되어도 리프레시 토큰이 유효하면 새 액세스 토큰을 재발급한다")
    void expiredAccessToken_fromCookies_alsoReissuesNewAccessToken() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)));
        String expiredAccessToken = expiredProvider.generateAccessToken(2L, "cookie@test.com", "cookieUser", UserRole.USER);
        String refreshToken = "valid-refresh-token-cookie";
        User user = new User(2L, "cookie@test.com", "cookieUser", UserRole.USER);
        given(securityHelper.getHeader("Authorization", "")).willReturn("");
        given(securityHelper.getCookieValue("refreshToken", "")).willReturn(refreshToken);
        given(securityHelper.getCookieValue("accessToken", "")).willReturn(expiredAccessToken);
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(securityHelper).setCookie(eq("accessToken"), cookieCaptor.capture());
        verify(securityHelper).setHeader(eq("Authorization"), anyString());
        assertThat(cookieCaptor.getValue()).isNotEqualTo(expiredAccessToken);
    }

    @Test
    @DisplayName("액세스 토큰이 변조되어 파싱에 실패해도 리프레시 토큰이 유효하면 새 액세스 토큰을 재발급한다")
    void malformedAccessToken_withValidRefreshToken_reissuesNewAccessToken() throws Exception {
        String malformedAccessToken = "this.is.not-a-valid-jwt";
        String refreshToken = "valid-refresh-token-malformed";
        User user = new User(3L, "malformed@test.com", "malformedUser", UserRole.USER);
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer " + refreshToken + " " + malformedAccessToken);
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        verify(securityHelper).setCookie(eq("accessToken"), anyString());
        verify(securityHelper).setHeader(eq("Authorization"), anyString());
    }

    @Test
    @DisplayName("액세스 토큰 없이 리프레시 토큰만 있으면 재인증은 되지만 액세스 토큰은 재발급되지 않는다")
    void onlyRefreshToken_noAccessToken_doesNotReissueAccessToken() throws Exception {
        String refreshToken = "valid-refresh-token-only";
        User user = new User(4L, "only@test.com", "onlyUser", UserRole.USER);
        given(securityHelper.getHeader("Authorization", "")).willReturn("");
        given(securityHelper.getCookieValue("refreshToken", "")).willReturn(refreshToken);
        given(securityHelper.getCookieValue("accessToken", "")).willReturn("");
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        verify(securityHelper, never()).setCookie(eq("accessToken"), anyString());
        verify(securityHelper, never()).setHeader(eq("Authorization"), anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("액세스 토큰이 만료되고 리프레시 토큰마저 유효하지 않으면 401을 강제하지 않고 익명 요청으로 다음 필터에 넘긴다")
    void expiredAccessToken_withInvalidRefreshToken_proceedsAnonymously() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)));
        String expiredAccessToken = expiredProvider.generateAccessToken(5L, "invalid@test.com", "invalidUser", UserRole.USER);
        String refreshToken = "invalid-refresh-token";
        given(securityHelper.getHeader("Authorization", "")).willReturn("Bearer " + refreshToken + " " + expiredAccessToken);
        given(userService.getUserByRefreshToken(refreshToken)).willReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        jwtFilter.doFilterInternal(request, response, chain);

        verify(securityHelper, never()).setCookie(eq("accessToken"), anyString());
        verify(securityHelper, never()).setHeader(eq("Authorization"), anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // chain.doFilter()가 호출됐음을 확인
    }
}