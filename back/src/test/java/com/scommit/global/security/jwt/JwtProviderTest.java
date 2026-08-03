package com.scommit.global.security.jwt;

import com.scommit.domain.user.user.entity.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    // HS256은 최소 32바이트(256비트) 키 필요
    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdW5pdC10ZXN0aW5nLW1pbmltdW0tMzJieXRlcy1yZXF1aXJlZA==";
    private static final Duration EXPIRATION = Duration.ofHours(1);

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(authTokenProperties(SECRET, EXPIRATION));
    }

    private static AuthTokenProperties authTokenProperties(String secretKey, Duration expiration) {
        return new AuthTokenProperties(
                new AuthTokenProperties.AccessToken(secretKey, expiration, Duration.ofMinutes(30)),
                new AuthTokenProperties.RefreshToken(Duration.ofDays(30))
        );
    }

    @Test
    @DisplayName("generateAccessToken: 토큰 문자열을 반환한다")
    void generateAccessToken_returnsNonEmptyToken() {
        String token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Nested
    @DisplayName("parseAccessToken: 발급한 토큰의 클레임이 그대로 복원된다")
    class ParseAccessToken {

        @Test
        @DisplayName("id 클레임에 userId가 저장된다")
        void id_containsUserId() {
            Long userId = 42L;
            String token = jwtProvider.generateAccessToken(userId, "user@test.com", "nickname", UserRole.USER);

            JwtProvider.AccessTokenPayload payload = jwtProvider.parseAccessToken(token);

            assertThat(payload.id()).isEqualTo(userId);
        }

        @Test
        @DisplayName("email 클레임이 저장되고 그대로 복원된다")
        void email_isRestored() {
            String token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);

            JwtProvider.AccessTokenPayload payload = jwtProvider.parseAccessToken(token);

            assertThat(payload.email()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("nickname 클레임이 저장되고 그대로 복원된다")
        void nickname_isRestored() {
            String token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);

            JwtProvider.AccessTokenPayload payload = jwtProvider.parseAccessToken(token);

            assertThat(payload.nickname()).isEqualTo("nickname");
        }

        @Test
        @DisplayName("role 클레임이 저장되고 UserRole enum으로 복원된다 - USER")
        void role_restoredAsEnum_user() {
            String token = jwtProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);

            JwtProvider.AccessTokenPayload payload = jwtProvider.parseAccessToken(token);

            assertThat(payload.role()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("role 클레임이 저장되고 UserRole enum으로 복원된다 - ADMIN")
        void role_restoredAsEnum_admin() {
            String token = jwtProvider.generateAccessToken(1L, "admin@test.com", "admin", UserRole.ADMIN);

            JwtProvider.AccessTokenPayload payload = jwtProvider.parseAccessToken(token);

            assertThat(payload.role()).isEqualTo(UserRole.ADMIN);
        }
    }

    @Test
    @DisplayName("parseAccessToken: 만료된 토큰은 ExpiredJwtException을 던진다")
    void parseAccessToken_expired_throwsExpiredJwtException() {
        JwtProvider expiredProvider = new JwtProvider(authTokenProperties(SECRET, Duration.ofMillis(-1)));
        String expiredToken = expiredProvider.generateAccessToken(1L, "user@test.com", "nickname", UserRole.USER);

        assertThatThrownBy(() -> expiredProvider.parseAccessToken(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }
}