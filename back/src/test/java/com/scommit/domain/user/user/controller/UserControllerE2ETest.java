package com.scommit.domain.user.user.controller;

// 결정사항 (변경 금지)
// - @SpringBootTest(webEnvironment = RANDOM_PORT) + RestTestClient
//   (TestRestTemplate은 Spring Boot 4.x에서 별도 모듈(spring-boot-resttestclient)로 분리되어
//    빌드 스크립트 수정 없이는 쓸 수 없다. RestTestClient(spring-test, 서블릿 계열)가
//    같은 역할의 표준 대안이며 webflux 의존성이 필요 없다. 상세 근거는
//    docs/user-e2e-test-plan.md 9-1 참고)
// - Mock / MockMvc / @MockBean 일절 사용 금지, 실제 HTTP 요청만
// - WebTestClient, webflux 의존성 추가 금지
// - 인증: JWT Bearer 토큰 (헤더)
// - DB: 이 클래스 전용 H2 in-memory(e2edb), 초기 데이터는 BaseInitData에 의존하지 않는다.
//   (다른 30여 개 테스트와 spring.datasource.url을 공유하면 create-drop 타이밍에 서로의
//    데이터를 지울 수 있어, 이 클래스에만 고유 DB 이름을 오버라이드한다.
//    상세 근거는 docs/user-e2e-test-plan.md 9-2 참고)
// - 픽스처: 회원가입 API로 만들 수 있는 데이터는 API로 직접 만들고(UserE2EFixtures 미사용),
//   API로 만들 수 없는 것(팔로워 수 집계, 검색 페이징용 다건 유저)만 UserE2EFixtures에서
//   리포지토리로 직접 심는다.
// - 대상: UserController의 12개 API 전부

import com.scommit.domain.user.user.dto.LoginRequest;
import com.scommit.domain.user.user.dto.LoginResponse;
import com.scommit.domain.user.user.dto.SignupRequest;
import com.scommit.domain.user.user.dto.SignupResponse;
import com.scommit.domain.user.user.dto.UserDeleteRequest;
import com.scommit.domain.user.user.dto.UserMeResponse;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 이 클래스 전용 DB 이름. 다른 @SpringBootTest 클래스들과 이름을 공유하면
        // 같은 JVM 안에서 같은 H2 인스턴스를 공유하게 되어 create-drop이 서로를 침범할 수 있다.
        properties = "spring.datasource.url=jdbc:h2:mem:e2edb;MODE=MySQL;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
@Tag("e2e")
@Import(UserE2EFixtures.class)
class UserControllerE2ETest {

    private static final String DEFAULT_PASSWORD = "password123";

    // RsData.statusCode는 @JsonIgnore이면서도 record의 정규 생성자 파라미터라서 응답 JSON에는
    // 나오지 않는데, RsData<T> 자체로 역직렬화하면 int statusCode에 null을 매핑하려다 실패한다
    // (tools.jackson.databind.exc.MismatchedInputException). src/main은 수정할 수 없으므로
    // 테스트에서만 쓰는 미러 레코드로 우회한다. docs/user-e2e-known-issues.md 참고.
    private record ApiResponse<T>(String resultCode, String msg, T data) {}

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpClient() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterAll
    static void cleanUpUploadedFiles() throws IOException {
        Path uploadDir = Path.of("build", "test-uploads");
        if (!Files.exists(uploadDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(uploadDir)) {
            walk.filter(path -> !path.equals(uploadDir))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@test.com";
    }

    private static String uniqueNickname() {
        return "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private ApiResponse<SignupResponse> signUp(String email, String password, String nickname) {
        return client.post().uri("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignupRequest(email, password, nickname))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<ApiResponse<SignupResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    private ApiResponse<LoginResponse> login(String email, String password) {
        return client.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    /**
     * 회원가입 API로 새 계정을 만들고 바로 로그인해 액세스 토큰을 발급받는다.
     */
    private String createUserAndGetAccessToken(String email, String password, String nickname) {
        signUp(email, password, nickname);
        ApiResponse<LoginResponse> loginResult = login(email, password);
        return loginResult.data().accessToken();
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @Test
    void signUpThenLogin_returnsAccessToken() {
        String email = uniqueEmail();
        String nickname = uniqueNickname();

        ApiResponse<SignupResponse> signUpResult = signUp(email, DEFAULT_PASSWORD, nickname);
        assertThat(signUpResult.resultCode()).isEqualTo("201-1");
        assertThat(signUpResult.data().email()).isEqualTo(email);
        assertThat(signUpResult.data().nickname()).isEqualTo(nickname);

        ApiResponse<LoginResponse> loginResult = login(email, DEFAULT_PASSWORD);
        assertThat(loginResult.resultCode()).isEqualTo("200-1");
        assertThat(loginResult.data().accessToken()).isNotBlank();
        assertThat(loginResult.data().refreshToken()).isNotBlank();
        assertThat(loginResult.data().user().email()).isEqualTo(email);
        assertThat(loginResult.data().user().nickname()).isEqualTo(nickname);
    }

    @Test
    void getMe_withValidAccessToken_returns200() {
        String email = uniqueEmail();
        String nickname = uniqueNickname();
        String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, nickname);

        client.get().uri("/api/users/me")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<ApiResponse<UserMeResponse>>() {})
                .value(body -> {
                    assertThat(body.resultCode()).isEqualTo("200-1");
                    assertThat(body.data().email()).isEqualTo(email);
                    assertThat(body.data().profile().nickname()).isEqualTo(nickname);
                });
    }

    @Nested
    @DisplayName("POST /api/users/signup — 회원가입")
    class Signup {

        @Test
        @DisplayName("1. 성공하면 201과 유저 정보를 반환하고 DB에 반영된다")
        void signUp_success_returns201AndPersistsUser() {
            String email = uniqueEmail();
            String nickname = uniqueNickname();

            ApiResponse<SignupResponse> result = signUp(email, DEFAULT_PASSWORD, nickname);

            assertThat(result.resultCode()).isEqualTo("201-1");
            assertThat(result.data().id()).isNotNull();
            assertThat(result.data().email()).isEqualTo(email);
            assertThat(result.data().nickname()).isEqualTo(nickname);
            assertThat(result.data().createdAt()).isNotNull();

            User saved = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
            assertThat(saved.getId()).isEqualTo(result.data().id());
            assertThat(saved.getRole()).isEqualTo(UserRole.USER);
            assertThat(saved.getRefreshToken()).isNotBlank();
            assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, saved.getPassword())).isTrue();
        }

        @Test
        @DisplayName("2. 이메일이 중복되면 409-1을 반환한다")
        void signUp_duplicateEmail_returns409_1() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());

            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest(email, DEFAULT_PASSWORD, uniqueNickname()))
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("409-1"));
        }

        @Test
        @DisplayName("3. 닉네임이 중복되면 409-3을 반환한다")
        void signUp_duplicateNickname_returns409_3() {
            String nickname = uniqueNickname();
            signUp(uniqueEmail(), DEFAULT_PASSWORD, nickname);

            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest(uniqueEmail(), DEFAULT_PASSWORD, nickname))
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("409-3"));
        }

        @Test
        @DisplayName("4. 이메일이 없으면 400-1을 반환한다")
        void signUp_missingEmail_returns400_1() {
            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest(null, DEFAULT_PASSWORD, uniqueNickname()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("5. 이메일 형식이 올바르지 않으면 400-1을 반환한다")
        void signUp_invalidEmailFormat_returns400_1() {
            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest("not-an-email", DEFAULT_PASSWORD, uniqueNickname()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("6. 비밀번호가 6자 미만이면 400-1을 반환한다")
        void signUp_passwordTooShort_returns400_1() {
            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest(uniqueEmail(), "12345", uniqueNickname()))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("7. 닉네임이 2자 미만이면 400-1을 반환한다")
        void signUp_nicknameTooShort_returns400_1() {
            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest(uniqueEmail(), DEFAULT_PASSWORD, "n"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("8. 닉네임이 21자면 400-1을 반환한다")
        void signUp_nicknameTooLong_returns400_1() {
            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignupRequest(uniqueEmail(), DEFAULT_PASSWORD, "n".repeat(21)))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("9. JSON 형식이 올바르지 않으면 400-1과 파싱 실패 메시지를 반환한다")
        void signUp_malformedJson_returns400_1WithParseErrorMessage() {
            client.post().uri("/api/users/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ \"email\": ")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("400-1");
                        assertThat(body.msg()).isEqualTo("올바른 JSON 요청 형식이 아닙니다.");
                    });
        }
    }

    @Nested
    @DisplayName("POST /api/users/login — 로그인")
    class Login {

        @Test
        @DisplayName("1. 성공하면 200과 토큰·유저 정보를 반환하고 쿠키를 내려준다")
        void login_success_returns200WithTokensAndCookies() {
            String email = uniqueEmail();
            String nickname = uniqueNickname();
            signUp(email, DEFAULT_PASSWORD, nickname);

            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isOk()
                    .expectCookie().exists("accessToken")
                    .expectCookie().exists("refreshToken")
                    .expectBody(new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().accessToken()).isNotBlank();
                        assertThat(body.data().refreshToken()).isNotBlank();
                        assertThat(body.data().expiresIn()).isEqualTo(1800);
                        assertThat(body.data().user().email()).isEqualTo(email);
                        assertThat(body.data().user().nickname()).isEqualTo(nickname);
                        assertThat(body.data().user().role()).isEqualTo(UserRole.USER);
                    });
        }

        @Test
        @DisplayName("2. 존재하지 않는 이메일이면 401-2를 반환한다")
        // FIXME: 현재 동작은 401-2(ErrorCode.INVALID_CREDENTIALS)다. 기대와 다르다고 볼 근거는
        // 목 기반 UserControllerTest가 이 케이스를 401-1로 어서션한다는 점인데, 이는 UserService를
        // Mockito로 목 처리해 강제로 UNAUTHORIZED(401-1)를 던지게 만든 결과일 뿐 실제 구현과
        // 다르다. E2E는 실제 구현(401-2)을 정답으로 고정한다. 상세: docs/user-e2e-known-issues.md #1
        void login_emailNotFound_returns401_2() {
            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(uniqueEmail(), DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-2"));
        }

        @Test
        @DisplayName("3. 비밀번호가 일치하지 않으면 401-2를 반환한다")
        // FIXME: docs/user-e2e-known-issues.md #1 참고 — 목 검증(401-1)과 실제 구현(401-2)이 다르다.
        void login_wrongPassword_returns401_2() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());

            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, "wrong-password"))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-2"));
        }

        @Test
        @DisplayName("4. 탈퇴(soft delete)한 계정이면 401-2를 반환한다")
        // FIXME: docs/user-e2e-known-issues.md #1 참고 — 목 검증(401-1)과 실제 구현(401-2)이 다르다.
        void login_softDeletedAccount_returns401_2() {
            String email = uniqueEmail();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, uniqueNickname());

            client.method(HttpMethod.DELETE).uri("/api/users")
                    .header("Authorization", bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UserDeleteRequest(DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isOk();

            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-2"));
        }

        @Test
        @DisplayName("5. 이메일 형식이 올바르지 않으면 400-1을 반환한다")
        void login_invalidEmailFormat_returns400_1() {
            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest("not-an-email", DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("6. 비밀번호가 없으면 400-1을 반환한다")
        void login_missingPassword_returns400_1() {
            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(uniqueEmail(), null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/logout — 로그아웃")
    class Logout {

        @Test
        @DisplayName("1. 성공하면 200을 반환하고 refreshToken을 무효화하며 쿠키를 삭제한다")
        void logout_success_returns200AndInvalidatesRefreshToken() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());
            ApiResponse<LoginResponse> loginResult = login(email, DEFAULT_PASSWORD);
            String accessToken = loginResult.data().accessToken();
            String originalRefreshToken = loginResult.data().refreshToken();
            Long userId = loginResult.data().user().id();

            client.post().uri("/api/users/logout")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectCookie().maxAge("accessToken", Duration.ZERO)
                    .expectCookie().maxAge("refreshToken", Duration.ZERO)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("200-1"));

            User updated = userRepository.findById(userId).orElseThrow();
            assertThat(updated.getRefreshToken()).isNotBlank();
            assertThat(updated.getRefreshToken()).isNotEqualTo(originalRefreshToken);
        }

        @Test
        @DisplayName("2. 토큰이 없으면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        void logout_noToken_returns401_1() {
            client.post().uri("/api/users/logout")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("401-1");
                        assertThat(body.msg()).isEqualTo("로그인 후 이용해주세요.");
                    });
        }

        @Test
        @DisplayName("3. Authorization 헤더가 Bearer 형식이 아니면 401-2를 반환한다 (JwtFilter가 직접 응답)")
        void logout_nonBearerAuthorizationHeader_returns401_2() {
            // 이 경로는 GlobalExceptionHandler가 아니라 JwtFilter.doFilterInternal의
            // catch (SecurityException) 블록이 response.getWriter()로 직접 응답을 작성한다.
            // Content-Type과 resultCode/msg/data 구조가 나머지 응답들과 동일한지 함께 확인한다.
            client.post().uri("/api/users/logout")
                    .header("Authorization", "Token xxx")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("401-2");
                        assertThat(body.msg()).isEqualTo("Authorization 헤더가 Bearer 형식이 아닙니다.");
                        assertThat(body.data()).isNull();
                    });
        }

        @Test
        @DisplayName("4. 깨진 토큰 문자열이면 401-1을 반환한다")
        // FIXME: 서명/파싱이 불가능한 토큰은 JwtFilter가 예외를 던지지 않고 조용히 익명 요청으로
        // 폴백시킨다. 최종적으로 SecurityConfig의 AuthenticationEntryPoint가 401-1을 응답하며,
        // ErrorCode.TOKEN_INVALID(401-4)는 사용되지 않는다. 상세: docs/user-e2e-known-issues.md #2
        void logout_malformedTokenString_returns401_1() {
            client.post().uri("/api/users/logout")
                    .header("Authorization", "Bearer not-a-valid-jwt")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }
    }
}
