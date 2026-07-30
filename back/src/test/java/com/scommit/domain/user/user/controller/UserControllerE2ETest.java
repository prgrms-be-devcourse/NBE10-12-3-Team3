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
import com.scommit.domain.user.user.dto.UserPasswordUpdateRequest;
import com.scommit.domain.user.user.dto.UserPasswordUpdateResponse;
import com.scommit.domain.user.user.dto.UserProfileResponse;
import com.scommit.domain.user.user.dto.UserSearchResponse;
import com.scommit.domain.user.user.dto.UserUpdateRequest;
import com.scommit.domain.user.user.dto.UserUpdateResponse;
import com.scommit.domain.user.user.entity.User;
import com.scommit.domain.user.user.entity.UserRole;
import com.scommit.domain.user.user.repository.UserRepository;
import com.scommit.domain.user.usermedia.dto.UserMediaResponse;
import com.scommit.global.security.jwt.AuthTokenProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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

    // GET /api/users/search가 Spring Data의 Page<T>를 그대로 직렬화하는데(9-4장 Q4 참고),
    // Page는 인터페이스라 클라이언트에서 역직렬화할 구체 타입이 없다. Q4 결정대로
    // content/totalElements/size/number 네 필드만 최소로 검증하는 미러 레코드로 받는다.
    private record PageResult<T>(List<T> content, long totalElements, int size, int number) {}

    private static final long NON_EXISTENT_USER_ID = 999_999_999L;
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthTokenProperties authTokenProperties;

    @Autowired
    private UserE2EFixtures.FollowerCountFixture followerCountFixture;

    @Autowired
    private UserE2EFixtures.SearchPagingFixture searchPagingFixture;

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

    private RestTestClient.ResponseSpec deleteAccount(String accessToken, String password) {
        return client.method(HttpMethod.DELETE).uri("/api/users")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UserDeleteRequest(password))
                .exchange();
    }

    private RestTestClient.ResponseSpec getMe(String accessToken) {
        return client.get().uri("/api/users/me")
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    private RestTestClient.ResponseSpec patchMe(String accessToken, MultiValueMap<String, HttpEntity<?>> multipartBody) {
        return client.patch().uri("/api/users/me")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody)
                .exchange();
    }

    // MultipartBodyBuilder(spring-web)는 클래스 정의 자체가 org.reactivestreams.Publisher를
    // 참조해서, reactive-streams가 클래스패스에 없는 이 프로젝트에서는 로드 시 NoClassDefFoundError가
    // 난다(webflux 의존성 추가 금지). 대신 FormHttpMessageConverter가 맵의 key를 part name으로,
    // HttpEntity의 헤더/본문을 그대로 파트 헤더/본문으로 쓰는 것을 이용해 직접 구성한다.
    private MultiValueMap<String, HttpEntity<?>> multipartRequestPart(String nickname, String introduction) {
        HttpHeaders requestPartHeaders = new HttpHeaders();
        requestPartHeaders.setContentType(MediaType.APPLICATION_JSON);
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("request", new HttpEntity<>(new UserUpdateRequest(nickname, introduction), requestPartHeaders));
        return body;
    }

    private HttpEntity<Resource> filePart(byte[] content, String filename, MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpEntity<>(resource, headers);
    }

    // 6-9/6-10/6-12에서 공통으로 쓰는 프로필 이미지 업로드. UpdateMe에서 쓰는 것과 같은
    // LinkedMultiValueMap<String, HttpEntity<?>> 조립 방식(filePart)을 그대로 재사용한다.
    private MultiValueMap<String, HttpEntity<?>> mediaFilePart(byte[] content, String filename, MediaType contentType) {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("file", filePart(content, filename, contentType));
        return body;
    }

    private RestTestClient.ResponseSpec uploadMedia(String accessToken, MultiValueMap<String, HttpEntity<?>> multipartBody) {
        return client.post().uri("/api/users/me/medias")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(multipartBody)
                .exchange();
    }

    private RestTestClient.ResponseSpec updatePassword(String accessToken, String currentPassword, String newPassword) {
        return client.put().uri("/api/users/me/password")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UserPasswordUpdateRequest(currentPassword, newPassword))
                .exchange();
    }

    // JwtProvider는 만료된 토큰을 만드는 공개 API가 없어, 같은 서명 키로 직접 발급한다.
    private String expiredAccessToken(Long userId, String email, String nickname, UserRole role) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(authTokenProperties.accessToken().secretKey()));
        Date issuedAt = new Date(System.currentTimeMillis() - Duration.ofMinutes(31).toMillis());
        Date expiration = new Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        return Jwts.builder()
                .claim("id", userId)
                .claim("email", email)
                .claim("nickname", nickname)
                .claim("role", role.name())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
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

    @Nested
    @DisplayName("DELETE /api/users — 회원탈퇴")
    class DeleteAccount {

        @Test
        @DisplayName("1. 성공하면 200을 반환하고, 이후 같은 계정 로그인은 401-2가 되며, 같은 accessToken으로 재탈퇴해도 200이 된다")
        // FIXME: UserService.deleteUser가 findById(deletedAt 미필터)를 사용해서, 이미 탈퇴한 계정도
        // 만료 전 accessToken을 그대로 들고 있으면 다시 탈퇴시킬 수 있고 응답도 여전히 200이다.
        // 상세: docs/user-e2e-known-issues.md #6
        void deleteAccount_success_returns200_thenLoginFails_andReDeleteStillReturns200() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());
            ApiResponse<LoginResponse> loginResult = login(email, DEFAULT_PASSWORD);
            String accessToken = loginResult.data().accessToken();
            Long userId = loginResult.data().user().id();

            deleteAccount(accessToken, DEFAULT_PASSWORD)
                    .expectStatus().isOk()
                    .expectCookie().maxAge("accessToken", Duration.ZERO)
                    .expectCookie().maxAge("refreshToken", Duration.ZERO)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("200-1"));

            User deleted = userRepository.findById(userId).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();

            client.post().uri("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(email, DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-2"));

            // Q8: 재탈퇴 — deletedAt이 이미 세팅된 계정인데도 200이 그대로 난다.
            deleteAccount(accessToken, DEFAULT_PASSWORD)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("200-1"));
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void deleteAccount_unauthenticated_returns401_1() {
            client.method(HttpMethod.DELETE).uri("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UserDeleteRequest(DEFAULT_PASSWORD))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }

        @Test
        @DisplayName("3. 비밀번호가 일치하지 않으면 400-2를 반환한다")
        void deleteAccount_wrongPassword_returns400_2() {
            String email = uniqueEmail();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, uniqueNickname());

            deleteAccount(accessToken, "wrong-password")
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-2"));
        }

        @Test
        @DisplayName("4. 비밀번호가 없으면 400-1을 반환한다")
        void deleteAccount_missingPassword_returns400_1() {
            String email = uniqueEmail();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, uniqueNickname());

            deleteAccount(accessToken, null)
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/me — 내 정보 조회")
    class GetMe {

        @Test
        @DisplayName("1. 성공하면 200과 내 프로필 정보를 반환한다")
        void getMe_success_returns200WithProfile() {
            String email = uniqueEmail();
            String nickname = uniqueNickname();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, nickname);

            getMe(accessToken)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserMeResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().email()).isEqualTo(email);
                        assertThat(body.data().profile().nickname()).isEqualTo(nickname);
                        assertThat(body.data().profile().introduction()).isNull();
                        assertThat(body.data().profile().profileImageUrl()).isNull();
                    });
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void getMe_unauthenticated_returns401_1() {
            client.get().uri("/api/users/me")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }

        @Test
        @DisplayName("3. 만료된 토큰(리프레시 없음)이면 401-1을 반환한다")
        // FIXME: 만료된 액세스 토큰은 JwtFilter가 조용히 리프레시로 폴백을 시도하고, 리프레시 토큰이
        // 없으면 익명 요청으로 넘어가 최종적으로 AuthenticationEntryPoint가 401-1을 응답한다.
        // ErrorCode.TOKEN_EXPIRED(401-3)는 사용되지 않는다. 상세: docs/user-e2e-known-issues.md #2
        void getMe_expiredAccessToken_returns401_1() {
            String email = uniqueEmail();
            String nickname = uniqueNickname();
            ApiResponse<SignupResponse> signUpResult = signUp(email, DEFAULT_PASSWORD, nickname);
            String expiredToken = expiredAccessToken(signUpResult.data().id(), email, nickname, UserRole.USER);

            client.get().uri("/api/users/me")
                    .header("Authorization", bearer(expiredToken))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me — 내 정보 수정")
    class UpdateMe {

        private static final byte[] TEST_IMAGE_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        @Test
        @DisplayName("1. 성공하면 닉네임·소개글이 반영된 200을 반환한다 (request part 누락 시 500-1도 확인)")
        // FIXME: request part 자체를 빼고 보내면 MissingServletRequestPartException 전용 핸들러가
        // 없어서 400이 아니라 500-1로 응답된다. 상세: docs/user-e2e-known-issues.md #3
        void updateMe_success_updatesProfile_andMissingRequestPartReturns500() {
            String email = uniqueEmail();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, uniqueNickname());

            MultiValueMap<String, HttpEntity<?>> emptyBody = new LinkedMultiValueMap<>();
            patchMe(accessToken, emptyBody)
                    .expectStatus().is5xxServerError()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("500-1"));

            String newNickname = uniqueNickname();
            String newIntroduction = "e2e updated introduction";
            patchMe(accessToken, multipartRequestPart(newNickname, newIntroduction))
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserUpdateResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().profile().nickname()).isEqualTo(newNickname);
                        assertThat(body.data().profile().introduction()).isEqualTo(newIntroduction);
                        assertThat(body.data().profile().profileImageUrl()).isNull();
                    });

            User updated = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
            assertThat(updated.getNickname()).isEqualTo(newNickname);
            assertThat(updated.getIntroduction()).isEqualTo(newIntroduction);
        }

        @Test
        @DisplayName("2. 프로필 이미지를 함께 업로드하면 200과 profileImageUrl을 반환한다")
        void updateMe_successWithProfileImage_returns200WithImageUrl() {
            String email = uniqueEmail();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, uniqueNickname());

            HttpHeaders requestPartHeaders = new HttpHeaders();
            requestPartHeaders.setContentType(MediaType.APPLICATION_JSON);
            MultiValueMap<String, HttpEntity<?>> multipartBody = new LinkedMultiValueMap<>();
            multipartBody.add("request", new HttpEntity<>(new UserUpdateRequest(null, "with image"), requestPartHeaders));
            multipartBody.add("profileImage", filePart(TEST_IMAGE_BYTES, "profile.png", MediaType.IMAGE_PNG));

            patchMe(accessToken, multipartBody)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserUpdateResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().profile().profileImageUrl()).isNotBlank();
                    });
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        void updateMe_unauthenticated_returns401_1() {
            client.patch().uri("/api/users/me")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartRequestPart(uniqueNickname(), null))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }

        @Test
        @DisplayName("4. 다른 유저의 닉네임으로 변경하면 409-3을 반환한다")
        void updateMe_duplicateNickname_returns409_3() {
            String takenNickname = uniqueNickname();
            signUp(uniqueEmail(), DEFAULT_PASSWORD, takenNickname);

            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            patchMe(accessToken, multipartRequestPart(takenNickname, null))
                    .expectStatus().isEqualTo(409)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("409-3"));
        }

        @Test
        @DisplayName("5. 닉네임이 2자 미만이면 400-1을 반환한다")
        void updateMe_nicknameTooShort_returns400_1() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            patchMe(accessToken, multipartRequestPart("n", null))
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("6. 닉네임이 공백만으로 이루어지면 400-1을 반환한다")
        void updateMe_nicknameBlank_returns400_1() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            patchMe(accessToken, multipartRequestPart("  ", null))
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("7. 소개글이 101자면 400-1을 반환한다")
        void updateMe_introductionTooLong_returns400_1() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            patchMe(accessToken, multipartRequestPart(null, "a".repeat(101)))
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/password — 비밀번호 변경")
    class UpdatePassword {

        @Test
        @DisplayName("1. 성공하면 200과 새 토큰을 반환하고, 새 accessToken으로 GET /me가 200이 된다")
        void updatePassword_success_returns200WithNewTokens_andNewAccessTokenCanGetMe() {
            String email = uniqueEmail();
            String accessToken = createUserAndGetAccessToken(email, DEFAULT_PASSWORD, uniqueNickname());
            String newPassword = "newpassword123";

            User beforeChange = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
            String refreshTokenBeforeChange = beforeChange.getRefreshToken();

            String[] newAccessTokenHolder = new String[1];
            updatePassword(accessToken, DEFAULT_PASSWORD, newPassword)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserPasswordUpdateResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().accessToken()).isNotBlank();
                        assertThat(body.data().refreshToken()).isNotBlank();
                        assertThat(body.data().expiresIn()).isEqualTo(1800);
                        newAccessTokenHolder[0] = body.data().accessToken();
                    });

            User afterChange = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
            assertThat(afterChange.getRefreshToken()).isNotEqualTo(refreshTokenBeforeChange);

            getMe(newAccessTokenHolder[0])
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserMeResponse>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("200-1"));
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void updatePassword_unauthenticated_returns401_1() {
            client.put().uri("/api/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UserPasswordUpdateRequest(DEFAULT_PASSWORD, "newpassword123"))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }

        @Test
        @DisplayName("3. 현재 비밀번호가 일치하지 않으면 400-2를 반환한다")
        void updatePassword_wrongCurrentPassword_returns400_2() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            updatePassword(accessToken, "wrong-password", "newpassword123")
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-2"));
        }

        @Test
        @DisplayName("4. 새 비밀번호가 6자 미만이면 400-1을 반환한다")
        void updatePassword_newPasswordTooShort_returns400_1() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            updatePassword(accessToken, DEFAULT_PASSWORD, "123")
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }

        @Test
        @DisplayName("5. 현재 비밀번호가 없으면 400-1을 반환한다")
        void updatePassword_missingCurrentPassword_returns400_1() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            updatePassword(accessToken, null, "newpassword123")
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-1"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id} — 유저 프로필 조회")
    class GetUserProfile {

        @Test
        @DisplayName("1. 성공(비로그인)이면 200과 followerCount·프로필을 반환한다")
        void getUserProfile_anonymous_returns200WithFollowerCountAndProfile() {
            client.get().uri("/api/users/" + followerCountFixture.creatorId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().id()).isEqualTo(followerCountFixture.creatorId());
                        assertThat(body.data().followerCount()).isEqualTo(followerCountFixture.followerCount());
                        assertThat(body.data().profile()).isNotNull();
                    });
        }

        @Test
        @DisplayName("2. 성공(로그인 상태)이면 200을 반환한다")
        void getUserProfile_authenticated_returns200() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            client.get().uri("/api/users/" + followerCountFixture.creatorId())
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserProfileResponse>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("200-1"));
        }

        @Test
        @DisplayName("3. 존재하지 않는 id면 404-2를 반환한다")
        void getUserProfile_nonExistentId_returns404_2() {
            client.get().uri("/api/users/" + NON_EXISTENT_USER_ID)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("404-2"));
        }

        @Test
        @DisplayName("4. 탈퇴한 유저 id면 404-2를 반환한다")
        void getUserProfile_softDeletedUserId_returns404_2() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());
            ApiResponse<LoginResponse> loginResult = login(email, DEFAULT_PASSWORD);
            String accessToken = loginResult.data().accessToken();
            Long userId = loginResult.data().user().id();

            deleteAccount(accessToken, DEFAULT_PASSWORD).expectStatus().isOk();

            client.get().uri("/api/users/" + userId)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("404-2"));
        }

        @Test
        @DisplayName("5. 비숫자 id를 비로그인으로 호출하면 401-1을 반환한다 (정규식 미매칭)")
        // FIXME: SecurityConfig의 permitAll이 GET /api/users/{id:\d+}로 숫자 id만 허용해서,
        // 비숫자 경로는 이 규칙에 매칭되지 않고 /api/** → authenticated 규칙으로 떨어진다.
        // 존재하지 않는 리소스(404)나 타입 불일치(400)가 아니라 401이 응답된다.
        // 상세: docs/user-e2e-known-issues.md #4
        void getUserProfile_nonNumericId_unauthenticated_returns401_1() {
            client.get().uri("/api/users/abc")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }
    }

    @Nested
    @DisplayName("POST /api/users/me/medias — 프로필 이미지 생성")
    class UploadMedia {

        @Test
        @DisplayName("1. 성공하면 201과 업로드된 이미지 URL·mediaType을 반환한다")
        void uploadMedia_success_returns201WithUrlAndImageType() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            uploadMedia(accessToken, mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                    .expectStatus().isCreated()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserMediaResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("201-1");
                        assertThat(body.data().url()).isNotBlank();
                        assertThat(body.data().mediaType())
                                .isEqualTo(com.scommit.domain.media.media.entity.MediaType.IMAGE);
                    });
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void uploadMedia_unauthenticated_returns401_1() {
            client.post().uri("/api/users/me/medias")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }

        @Test
        @DisplayName("3. 빈 파일이면 400-4를 반환한다")
        void uploadMedia_emptyFile_returns400_4() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            uploadMedia(accessToken, mediaFilePart(new byte[0], "empty.png", MediaType.IMAGE_PNG))
                    .expectStatus().isBadRequest()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("400-4"));
        }

        @Test
        @DisplayName("4. text/plain 파일이면 415-1을 반환한다")
        void uploadMedia_unsupportedFileType_returns415_1() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            uploadMedia(accessToken, mediaFilePart(PNG_BYTES, "file.txt", MediaType.TEXT_PLAIN))
                    .expectStatus().isEqualTo(415)
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("415-1"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/medias — 프로필 이미지 조회")
    class GetMedia {

        @Test
        @DisplayName("1. 미디어가 있으면 200과 이미지 URL을 반환한다")
        void getMedia_withMedia_returns200WithUrl() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());
            ApiResponse<LoginResponse> loginResult = login(email, DEFAULT_PASSWORD);
            String accessToken = loginResult.data().accessToken();
            Long userId = loginResult.data().user().id();

            uploadMedia(accessToken, mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                    .expectStatus().isCreated();

            client.get().uri("/api/users/" + userId + "/medias")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserMediaResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().url()).isNotBlank();
                        assertThat(body.data().userId()).isEqualTo(userId);
                    });
        }

        @Test
        @DisplayName("2. 미디어가 없으면 200과 data=null을 반환한다 (404 아님)")
        void getMedia_withoutMedia_returns200WithNullData() {
            String email = uniqueEmail();
            signUp(email, DEFAULT_PASSWORD, uniqueNickname());
            ApiResponse<LoginResponse> loginResult = login(email, DEFAULT_PASSWORD);
            Long userId = loginResult.data().user().id();

            client.get().uri("/api/users/" + userId + "/medias")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<UserMediaResponse>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data()).isNull();
                    });
        }

        @Test
        @DisplayName("3. 존재하지 않는 유저면 404-2를 반환한다")
        void getMedia_nonExistentUser_returns404_2() {
            client.get().uri("/api/users/" + NON_EXISTENT_USER_ID + "/medias")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("404-2"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/search — 유저 검색")
    class SearchUsers {

        private RestTestClient.ResponseSpec searchUsers(String keyword, Integer page, Integer size) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/users/search");
            if (keyword != null) {
                builder.queryParam("keyword", keyword);
            }
            if (page != null) {
                builder.queryParam("page", page);
            }
            if (size != null) {
                builder.queryParam("size", size);
            }
            return client.get().uri(builder.build().toUriString()).exchange();
        }

        @Test
        @DisplayName("1. 키워드가 매칭되면 200과 기대 닉네임을 포함한 content를 반환한다")
        void searchUsers_matchingKeyword_returns200WithExpectedNicknames() {
            searchUsers(searchPagingFixture.nicknamePrefix(), null, null)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<PageResult<UserSearchResponse>>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().totalElements()).isEqualTo(searchPagingFixture.userIds().size());
                        assertThat(body.data().content())
                                .extracting(UserSearchResponse::nickname)
                                .contains(searchPagingFixture.nicknamePrefix() + "1");
                    });
        }

        @Test
        @DisplayName("2. keyword를 지정하지 않으면 200과 빈 Page를 반환한다")
        void searchUsers_missingKeyword_returns200WithEmptyPage() {
            searchUsers(null, null, null)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<PageResult<UserSearchResponse>>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content()).isEmpty();
                        assertThat(body.data().totalElements()).isZero();
                    });
        }

        @Test
        @DisplayName("3. 매칭되는 유저가 없는 키워드면 200과 빈 Page를 반환한다")
        void searchUsers_noMatchKeyword_returns200WithEmptyPage() {
            // 픽스처 접두사 자체에 존재하지 않는 접미사를 붙여, 다른 테스트가 회원가입으로 만든
            // 계정과 겹치지 않으면서도 빈 결과가 나오도록 한다.
            searchUsers(searchPagingFixture.nicknamePrefix() + "-no-match-xyz", null, null)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<PageResult<UserSearchResponse>>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content()).isEmpty();
                        assertThat(body.data().totalElements()).isZero();
                    });
        }

        @Test
        @DisplayName("4. page·size를 지정하면 페이징이 반영된다")
        void searchUsers_withPageAndSize_reflectsPaging() {
            searchUsers(searchPagingFixture.nicknamePrefix(), 0, 2)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<PageResult<UserSearchResponse>>>() {})
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content()).hasSize(2);
                        assertThat(body.data().totalElements()).isEqualTo(searchPagingFixture.userIds().size());
                        assertThat(body.data().size()).isEqualTo(2);
                        assertThat(body.data().number()).isZero();
                    });
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me/medias — 프로필 이미지 삭제")
    class DeleteMedia {

        private RestTestClient.ResponseSpec deleteMedia(String accessToken) {
            return client.method(HttpMethod.DELETE).uri("/api/users/me/medias")
                    .header("Authorization", bearer(accessToken))
                    .exchange();
        }

        @Test
        @DisplayName("1. 성공하면 200을 반환한다")
        void deleteMedia_success_returns200() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            uploadMedia(accessToken, mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                    .expectStatus().isCreated();

            deleteMedia(accessToken)
                    .expectStatus().isOk()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("200-1"));
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void deleteMedia_unauthenticated_returns401_1() {
            client.method(HttpMethod.DELETE).uri("/api/users/me/medias")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("401-1"));
        }

        @Test
        @DisplayName("3. 미디어가 없으면 404-7을 반환한다 (404-2와 다름)")
        void deleteMedia_noMedia_returns404_7() {
            String accessToken = createUserAndGetAccessToken(uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            deleteMedia(accessToken)
                    .expectStatus().isNotFound()
                    .expectBody(new ParameterizedTypeReference<ApiResponse<Void>>() {})
                    .value(body -> assertThat(body.resultCode()).isEqualTo("404-7"));
        }
    }
}
