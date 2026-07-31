package com.scommit.global.e2e;

import com.scommit.domain.user.user.dto.LoginRequest;
import com.scommit.domain.user.user.dto.LoginResponse;
import com.scommit.domain.user.user.dto.SignupRequest;
import com.scommit.domain.user.user.dto.SignupResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 컨트롤러를 가리지 않고 쓰이는 E2E 공통 헬퍼 — 클라이언트 생성, 회원가입/로그인, Bearer 헤더,
 * 공통 응답 어서션.
 *
 * <p>인증이 필요한 E2E는 결국 회원가입/로그인 API를 거쳐야 하므로, {@code global} 패키지에 있으면서도
 * {@code domain.user} 의 DTO({@link SignupRequest}, {@link LoginRequest}, {@link LoginResponse} 등)를
 * 참조한다. 이 프로젝트에서 인증은 user 도메인 소관이라 불가피하며 의도된 의존이다.
 * 이 의존을 끊기 위한 인터페이스/제네릭 추상화는 하지 않는다.
 *
 * <p>사용법은 {@code docs/e2e-test-convention.md} 참고.
 */
public final class E2ETestSupport {

    private E2ETestSupport() {
    }

    /**
     * {@code @LocalServerPort} 로 받은 포트에 붙는 실제 HTTP 클라이언트를 만든다.
     * Mock 서버 바인딩이 아니라 실제 서버로 요청이 나간다.
     */
    public static RestTestClient client(int port) {
        return RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    /**
     * 테스트 간 데이터 격리를 이메일/닉네임 유일성으로 해결하기 위한 값 생성기.
     * ({@code @DirtiesContext}/{@code @Transactional} 을 쓰지 않는다는 결정에 따른 것)
     */
    public static String uniqueEmail() {
        return "e2e-" + UUID.randomUUID() + "@test.com";
    }

    public static String uniqueNickname() {
        return "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * 회원가입 요청을 보내고 응답을 그대로 돌려준다(상태 코드 검증 없음).
     * 검증 실패 케이스를 쓰는 쪽에서 사용한다.
     */
    public static RestTestClient.ResponseSpec signUpRequest(RestTestClient client, SignupRequest request) {
        return client.post().uri("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    /**
     * 회원가입 성공을 전제로 응답 본문을 돌려준다.
     */
    public static ApiResponse<SignupResponse> signUp(
            RestTestClient client, String email, String password, String nickname) {
        return signUpRequest(client, new SignupRequest(email, password, nickname))
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<ApiResponse<SignupResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    /**
     * 로그인 요청을 보내고 응답을 그대로 돌려준다(상태 코드 검증 없음).
     */
    public static RestTestClient.ResponseSpec loginRequest(RestTestClient client, String email, String password) {
        return client.post().uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(email, password))
                .exchange();
    }

    /**
     * 로그인 성공을 전제로 응답 본문을 돌려준다.
     */
    public static ApiResponse<LoginResponse> login(RestTestClient client, String email, String password) {
        return loginRequest(client, email, password)
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    /**
     * 회원가입 API로 새 계정을 만들고 바로 로그인해서 로그인 응답(토큰 + user.id)을 돌려준다.
     * 유저 id까지 필요한 테스트가 쓴다.
     */
    public static LoginResponse createUserAndLogin(
            RestTestClient client, String email, String password, String nickname) {
        signUp(client, email, password, nickname);
        return login(client, email, password).data();
    }

    /**
     * 액세스 토큰만 필요한 테스트용 축약형.
     */
    public static String createUserAndGetAccessToken(
            RestTestClient client, String email, String password, String nickname) {
        return createUserAndLogin(client, email, password, nickname).accessToken();
    }

    /**
     * 상태 코드와 {@code resultCode} 만 확인하는 공통 어서션.
     * {@code msg}/{@code data} 까지 봐야 하는 케이스는 이 헬퍼를 쓰지 말고 직접 풀어 쓴다.
     */
    public static void expectResultCode(RestTestClient.ResponseSpec response, HttpStatus status, String resultCode) {
        response.expectStatus().isEqualTo(status)
                .expectBody(ApiResponse.VOID_BODY)
                .value(body -> assertThat(body.resultCode()).isEqualTo(resultCode));
    }
}
