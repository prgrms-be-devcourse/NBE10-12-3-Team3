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
import com.scommit.domain.user.user.dto.UserMeResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
