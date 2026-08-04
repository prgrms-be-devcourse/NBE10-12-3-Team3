package com.scommit.domain.user.user.controller

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
// - 대상: UserController의 12개 API 전부 + 시나리오(통합 흐름) 3개
// - 공통 요소(ApiResponse 미러 레코드, 클라이언트 생성, 회원가입/로그인/Bearer 헬퍼)는
//   com.scommit.global.e2e 로 분리해 static import 한다. 상속 강제(추상 클래스)는 하지 않는다.
//   컨벤션 문서는 docs/e2e-test-convention.md 참고.

import com.scommit.domain.user.user.dto.LoginResponse
import com.scommit.domain.user.user.dto.SignupRequest
import com.scommit.domain.user.user.dto.UserDeleteRequest
import com.scommit.domain.user.user.dto.UserMeResponse
import com.scommit.domain.user.user.dto.UserPasswordUpdateRequest
import com.scommit.domain.user.user.dto.UserPasswordUpdateResponse
import com.scommit.domain.user.user.dto.UserProfileResponse
import com.scommit.domain.user.user.dto.UserSearchResponse
import com.scommit.domain.user.user.dto.UserUpdateRequest
import com.scommit.domain.user.user.dto.UserUpdateResponse
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import com.scommit.domain.user.usermedia.dto.UserMediaResponse
import com.scommit.global.e2e.ApiResponse
import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.bearer
import com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken
import com.scommit.global.e2e.E2ETestSupport.createUserAndLogin
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.login
import com.scommit.global.e2e.E2ETestSupport.loginRequest
import com.scommit.global.e2e.E2ETestSupport.signUp
import com.scommit.global.e2e.E2ETestSupport.signUpRequest
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
import com.scommit.global.security.jwt.AuthTokenProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.util.UriComponentsBuilder
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Comparator
import java.util.Date
import com.scommit.domain.media.media.entity.MediaType as DomainMediaType

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // 이 클래스 전용 DB 이름. 다른 @SpringBootTest 클래스들과 이름을 공유하면
    // 같은 JVM 안에서 같은 H2 인스턴스를 공유하게 되어 create-drop이 서로를 침범할 수 있다.
    properties = ["spring.datasource.url=jdbc:h2:mem:e2edb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
@Import(UserE2EFixtures::class)
class UserControllerE2ETest {
    // GET /api/users/search가 Spring Data의 Page<T>를 그대로 직렬화하는데(9-4장 Q4 참고),
    // Page는 인터페이스라 클라이언트에서 역직렬화할 구체 타입이 없다. Q4 결정대로
    // content/totalElements/size/number 네 필드만 최소로 검증하는 미러 레코드로 받는다.
    private data class PageResult<T>(
        val content: List<T>,
        val totalElements: Long,
        val size: Int,
        val number: Int,
    )

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authTokenProperties: AuthTokenProperties

    @Autowired
    private lateinit var followerCountFixture: UserE2EFixtures.FollowerCountFixture

    @Autowired
    private lateinit var searchPagingFixture: UserE2EFixtures.SearchPagingFixture

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    private fun deleteAccount(
        accessToken: String,
        password: String?,
    ): RestTestClient.ResponseSpec =
        client
            .method(HttpMethod.DELETE)
            .uri("/api/users")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(UserDeleteRequest(password))
            .exchange()

    private fun getMe(accessToken: String): RestTestClient.ResponseSpec =
        client
            .get()
            .uri("/api/users/me")
            .header("Authorization", bearer(accessToken))
            .exchange()

    private fun getUserProfile(userId: Long): RestTestClient.ResponseSpec =
        client.get().uri("/api/users/$userId").exchange()

    // 업로드/수정/삭제의 결과를 되짚어 확인할 때 공통으로 쓴다.
    private fun getMedia(userId: Long): RestTestClient.ResponseSpec =
        client.get().uri("/api/users/$userId/medias").exchange()

    private fun patchMe(
        accessToken: String,
        multipartBody: MultiValueMap<String, HttpEntity<*>>,
    ): RestTestClient.ResponseSpec =
        client
            .patch()
            .uri("/api/users/me")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(multipartBody)
            .exchange()

    // MultipartBodyBuilder(spring-web)는 클래스 정의 자체가 org.reactivestreams.Publisher를
    // 참조해서, reactive-streams가 클래스패스에 없는 이 프로젝트에서는 로드 시 NoClassDefFoundError가
    // 난다(webflux 의존성 추가 금지). 대신 FormHttpMessageConverter가 맵의 key를 part name으로,
    // HttpEntity의 헤더/본문을 그대로 파트 헤더/본문으로 쓰는 것을 이용해 직접 구성한다.
    private fun multipartRequestPart(
        nickname: String?,
        introduction: String?,
    ): MultiValueMap<String, HttpEntity<*>> =
        LinkedMultiValueMap<String, HttpEntity<*>>().apply {
            add("request", jsonPart(UserUpdateRequest(nickname, introduction)))
        }

    private fun jsonPart(value: Any): HttpEntity<Any> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        return HttpEntity(value, headers)
    }

    private fun filePart(
        content: ByteArray,
        filename: String,
        contentType: MediaType,
    ): HttpEntity<Resource> {
        val headers = HttpHeaders()
        headers.contentType = contentType
        val resource =
            object : ByteArrayResource(content) {
                override fun getFilename(): String = filename
            }
        return HttpEntity(resource, headers)
    }

    // 6-9/6-10/6-12에서 공통으로 쓰는 프로필 이미지 업로드. UpdateMe에서 쓰는 것과 같은
    // LinkedMultiValueMap<String, HttpEntity<?>> 조립 방식(filePart)을 그대로 재사용한다.
    private fun mediaFilePart(
        content: ByteArray,
        filename: String,
        contentType: MediaType,
    ): MultiValueMap<String, HttpEntity<*>> =
        LinkedMultiValueMap<String, HttpEntity<*>>().apply {
            add("file", filePart(content, filename, contentType))
        }

    private fun uploadMedia(
        accessToken: String,
        multipartBody: MultiValueMap<String, HttpEntity<*>>,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/users/me/medias")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(multipartBody)
            .exchange()

    private fun updatePassword(
        accessToken: String,
        currentPassword: String?,
        newPassword: String,
    ): RestTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/users/me/password")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(UserPasswordUpdateRequest(currentPassword, newPassword))
            .exchange()

    // SearchUsers와 시나리오 3에서 함께 쓴다.
    private fun searchUsers(
        keyword: String?,
        page: Int?,
        size: Int?,
    ): RestTestClient.ResponseSpec {
        val builder = UriComponentsBuilder.fromPath("/api/users/search")
        keyword?.let { builder.queryParam("keyword", it) }
        page?.let { builder.queryParam("page", it) }
        size?.let { builder.queryParam("size", it) }
        return client.get().uri(builder.build().toUriString()).exchange()
    }

    // JwtProvider는 만료된 토큰을 만드는 공개 API가 없어, 같은 서명 키로 직접 발급한다.
    private fun expiredAccessToken(
        userId: Long,
        email: String,
        nickname: String,
        role: UserRole,
    ): String {
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(authTokenProperties.accessToken.secretKey()))
        val issuedAt = Date(System.currentTimeMillis() - Duration.ofMinutes(31).toMillis())
        val expiration = Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis())
        return Jwts
            .builder()
            .claim("id", userId)
            .claim("email", email)
            .claim("nickname", nickname)
            .claim("role", role.name)
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(key)
            .compact()
    }

    @Nested
    @DisplayName("POST /api/users/signup — 회원가입")
    inner class Signup {
        @Test
        @DisplayName("1. 성공하면 201과 유저 정보를 반환하고 DB에 반영된다")
        fun signUp_success_returns201AndPersistsUser() {
            val email = uniqueEmail()
            val nickname = uniqueNickname()

            val result = signUp(client, email, DEFAULT_PASSWORD, nickname)

            assertThat(result.resultCode).isEqualTo("201-1")
            assertThat(result.data.id).isNotNull()
            assertThat(result.data.email).isEqualTo(email)
            assertThat(result.data.nickname).isEqualTo(nickname)
            assertThat(result.data.createdAt).isNotNull()

            val saved = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
            assertThat(saved.id).isEqualTo(result.data.id)
            assertThat(saved.role).isEqualTo(UserRole.USER)
            assertThat(saved.refreshToken).isNotBlank()
            assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, saved.password)).isTrue()
        }

        @Test
        @DisplayName("2. 이메일이 중복되면 409-1을 반환한다")
        fun signUp_duplicateEmail_returns409_1() {
            val email = uniqueEmail()
            signUp(client, email, DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                signUpRequest(client, SignupRequest(email, DEFAULT_PASSWORD, uniqueNickname())),
                HttpStatus.CONFLICT,
                "409-1",
            )
        }

        @Test
        @DisplayName("3. 닉네임이 중복되면 409-3을 반환한다")
        fun signUp_duplicateNickname_returns409_3() {
            val nickname = uniqueNickname()
            signUp(client, uniqueEmail(), DEFAULT_PASSWORD, nickname)

            expectResultCode(
                signUpRequest(client, SignupRequest(uniqueEmail(), DEFAULT_PASSWORD, nickname)),
                HttpStatus.CONFLICT,
                "409-3",
            )
        }

        @Test
        @DisplayName("4. 이메일이 없으면 400-1을 반환한다")
        fun signUp_missingEmail_returns400_1() {
            expectResultCode(
                signUpRequest(client, SignupRequest(null, DEFAULT_PASSWORD, uniqueNickname())),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("5. 이메일 형식이 올바르지 않으면 400-1을 반환한다")
        fun signUp_invalidEmailFormat_returns400_1() {
            expectResultCode(
                signUpRequest(client, SignupRequest("not-an-email", DEFAULT_PASSWORD, uniqueNickname())),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("6. 비밀번호가 6자 미만이면 400-1을 반환한다")
        fun signUp_passwordTooShort_returns400_1() {
            expectResultCode(
                signUpRequest(client, SignupRequest(uniqueEmail(), "12345", uniqueNickname())),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("7. 닉네임이 2자 미만이면 400-1을 반환한다")
        fun signUp_nicknameTooShort_returns400_1() {
            expectResultCode(
                signUpRequest(client, SignupRequest(uniqueEmail(), DEFAULT_PASSWORD, "n")),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("8. 닉네임이 21자면 400-1을 반환한다")
        fun signUp_nicknameTooLong_returns400_1() {
            expectResultCode(
                signUpRequest(client, SignupRequest(uniqueEmail(), DEFAULT_PASSWORD, "n".repeat(21))),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("9. JSON 형식이 올바르지 않으면 400-1과 파싱 실패 메시지를 반환한다")
        fun signUp_malformedJson_returns400_1WithParseErrorMessage() {
            // 본문이 SignupRequest가 아니라 깨진 문자열이라 signUpRequest 헬퍼를 쓸 수 없다.
            client
                .post()
                .uri("/api/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ \"email\": ")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("400-1")
                    assertThat(body.msg).isEqualTo("올바른 JSON 요청 형식이 아닙니다.")
                }
        }
    }

    @Nested
    @DisplayName("POST /api/users/login — 로그인")
    inner class Login {
        // FIXME: SecurityHelper.setCookie가 모든 쿠키에 Secure=true를 강제하는데 이 테스트는 평문
        // HTTP로 요청한다. 브라우저였다면 저장되지 않았을 쿠키라서, Set-Cookie 헤더가 내려왔다는
        // 것까지만 확인할 수 있고 쿠키 기반 인증 흐름 자체는 재현할 수 없다(그래서 인증은 Bearer
        // 헤더만 쓴다). 상세: docs/user-e2e-known-issues.md #5
        @Test
        @DisplayName("1. 성공하면 200과 토큰·유저 정보를 반환하고 쿠키를 내려준다")
        @Suppress("ForbiddenComment")
        fun login_success_returns200WithTokensAndCookies() {
            val email = uniqueEmail()
            val nickname = uniqueNickname()
            signUp(client, email, DEFAULT_PASSWORD, nickname)

            loginRequest(client, email, DEFAULT_PASSWORD)
                .expectStatus()
                .isOk()
                .expectCookie()
                .exists("accessToken")
                .expectCookie()
                .exists("refreshToken")
                .expectBody<ApiResponse<LoginResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.accessToken).isNotBlank()
                    assertThat(body.data.refreshToken).isNotBlank()
                    assertThat(body.data.expiresIn).isEqualTo(1800)
                    assertThat(body.data.user.email).isEqualTo(email)
                    assertThat(body.data.user.nickname).isEqualTo(nickname)
                    assertThat(body.data.user.role).isEqualTo(UserRole.USER)
                }
        }

        // FIXME: 현재 동작은 401-2(ErrorCode.INVALID_CREDENTIALS)다. 기대와 다르다고 볼 근거는
        // 목 기반 UserControllerTest가 이 케이스를 401-1로 어서션한다는 점인데, 이는 UserService를
        // Mockito로 목 처리해 강제로 UNAUTHORIZED(401-1)를 던지게 만든 결과일 뿐 실제 구현과
        // 다르다. E2E는 실제 구현(401-2)을 정답으로 고정한다. 상세: docs/user-e2e-known-issues.md #1
        @Test
        @DisplayName("2. 존재하지 않는 이메일이면 401-2를 반환한다")
        @Suppress("ForbiddenComment")
        fun login_emailNotFound_returns401_2() {
            expectResultCode(
                loginRequest(client, uniqueEmail(), DEFAULT_PASSWORD),
                HttpStatus.UNAUTHORIZED,
                "401-2",
            )
        }

        // FIXME: docs/user-e2e-known-issues.md #1 참고 — 목 검증(401-1)과 실제 구현(401-2)이 다르다.
        @Test
        @DisplayName("3. 비밀번호가 일치하지 않으면 401-2를 반환한다")
        @Suppress("ForbiddenComment")
        fun login_wrongPassword_returns401_2() {
            val email = uniqueEmail()
            signUp(client, email, DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                loginRequest(client, email, "wrong-password"),
                HttpStatus.UNAUTHORIZED,
                "401-2",
            )
        }

        // FIXME: docs/user-e2e-known-issues.md #1 참고 — 목 검증(401-1)과 실제 구현(401-2)이 다르다.
        @Test
        @DisplayName("4. 탈퇴(soft delete)한 계정이면 401-2를 반환한다")
        @Suppress("ForbiddenComment")
        fun login_softDeletedAccount_returns401_2() {
            val email = uniqueEmail()
            val accessToken = createUserAndGetAccessToken(client, email, DEFAULT_PASSWORD, uniqueNickname())

            deleteAccount(accessToken, DEFAULT_PASSWORD).expectStatus().isOk()

            expectResultCode(
                loginRequest(client, email, DEFAULT_PASSWORD),
                HttpStatus.UNAUTHORIZED,
                "401-2",
            )
        }

        @Test
        @DisplayName("5. 이메일 형식이 올바르지 않으면 400-1을 반환한다")
        fun login_invalidEmailFormat_returns400_1() {
            expectResultCode(
                loginRequest(client, "not-an-email", DEFAULT_PASSWORD),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("6. 비밀번호가 없으면 400-1을 반환한다")
        fun login_missingPassword_returns400_1() {
            expectResultCode(
                loginRequest(client, uniqueEmail(), null),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }
    }

    @Nested
    @DisplayName("POST /api/users/logout — 로그아웃")
    inner class Logout {
        @Test
        @DisplayName("1. 성공하면 200을 반환하고 refreshToken을 무효화하며 쿠키를 삭제한다")
        fun logout_success_returns200AndInvalidatesRefreshToken() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val originalRefreshToken = session.refreshToken
            val userId = session.user.id

            val response =
                client
                    .post()
                    .uri("/api/users/logout")
                    .header("Authorization", bearer(session.accessToken))
                    .exchange()
                    .expectCookie()
                    .maxAge("accessToken", Duration.ZERO)
                    .expectCookie()
                    .maxAge("refreshToken", Duration.ZERO)
            expectResultCode(response, HttpStatus.OK, "200-1")

            val updated = checkNotNull(userRepository.findByIdOrNull(userId))
            assertThat(updated.refreshToken).isNotBlank()
            assertThat(updated.refreshToken).isNotEqualTo(originalRefreshToken)
        }

        @Test
        @DisplayName("2. 토큰이 없으면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun logout_noToken_returns401_1() {
            client
                .post()
                .uri("/api/users/logout")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("401-1")
                    assertThat(body.msg).isEqualTo("로그인 후 이용해주세요.")
                }
        }

        @Test
        @DisplayName("3. Authorization 헤더가 Bearer 형식이 아니면 401-2를 반환한다 (JwtFilter가 직접 응답)")
        fun logout_nonBearerAuthorizationHeader_returns401_2() {
            // 이 경로는 GlobalExceptionHandler가 아니라 JwtFilter.doFilterInternal의
            // catch (SecurityException) 블록이 response.getWriter()로 직접 응답을 작성한다.
            // Content-Type과 resultCode/msg/data 구조가 나머지 응답들과 동일한지 함께 확인한다.
            client
                .post()
                .uri("/api/users/logout")
                .header("Authorization", "Token xxx")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("401-2")
                    assertThat(body.msg).isEqualTo("Authorization 헤더가 Bearer 형식이 아닙니다.")
                    assertThat(body.data).isNull()
                }
        }

        // FIXME: 서명/파싱이 불가능한 토큰은 JwtFilter가 예외를 던지지 않고 조용히 익명 요청으로
        // 폴백시킨다. 최종적으로 SecurityConfig의 AuthenticationEntryPoint가 401-1을 응답하며,
        // ErrorCode.TOKEN_INVALID(401-4)는 사용되지 않는다. 상세: docs/user-e2e-known-issues.md #2
        @Test
        @DisplayName("4. 깨진 토큰 문자열이면 401-1을 반환한다")
        @Suppress("ForbiddenComment")
        fun logout_malformedTokenString_returns401_1() {
            val response =
                client
                    .post()
                    .uri("/api/users/logout")
                    .header("Authorization", "Bearer not-a-valid-jwt")
                    .exchange()

            expectResultCode(response, HttpStatus.UNAUTHORIZED, "401-1")
        }
    }

    @Nested
    @DisplayName("DELETE /api/users — 회원탈퇴")
    inner class DeleteAccount {
        // FIXME: UserService.deleteUser가 findById(deletedAt 미필터)를 사용해서, 이미 탈퇴한 계정도
        // 만료 전 accessToken을 그대로 들고 있으면 다시 탈퇴시킬 수 있고 응답도 여전히 200이다.
        // 상세: docs/user-e2e-known-issues.md #6
        @Test
        @DisplayName("1. 성공하면 200을 반환하고, 이후 같은 계정 로그인은 401-2가 되며, 같은 accessToken으로 재탈퇴해도 200이 된다")
        @Suppress("ForbiddenComment")
        fun deleteAccount_success_returns200_thenLoginFails_andReDeleteStillReturns200() {
            val email = uniqueEmail()
            val session = createUserAndLogin(client, email, DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val userId = session.user.id

            val response =
                deleteAccount(accessToken, DEFAULT_PASSWORD)
                    .expectCookie()
                    .maxAge("accessToken", Duration.ZERO)
                    .expectCookie()
                    .maxAge("refreshToken", Duration.ZERO)
            expectResultCode(response, HttpStatus.OK, "200-1")

            val deleted = checkNotNull(userRepository.findByIdOrNull(userId))
            assertThat(deleted.deletedAt).isNotNull()

            expectResultCode(
                loginRequest(client, email, DEFAULT_PASSWORD),
                HttpStatus.UNAUTHORIZED,
                "401-2",
            )

            // Q8: 재탈퇴 — deletedAt이 이미 세팅된 계정인데도 200이 그대로 난다.
            expectResultCode(deleteAccount(accessToken, DEFAULT_PASSWORD), HttpStatus.OK, "200-1")
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun deleteAccount_unauthenticated_returns401_1() {
            val response =
                client
                    .method(HttpMethod.DELETE)
                    .uri("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(UserDeleteRequest(DEFAULT_PASSWORD))
                    .exchange()

            expectResultCode(response, HttpStatus.UNAUTHORIZED, "401-1")
        }

        @Test
        @DisplayName("3. 비밀번호가 일치하지 않으면 400-2를 반환한다")
        fun deleteAccount_wrongPassword_returns400_2() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(deleteAccount(accessToken, "wrong-password"), HttpStatus.BAD_REQUEST, "400-2")
        }

        @Test
        @DisplayName("4. 비밀번호가 없으면 400-1을 반환한다")
        fun deleteAccount_missingPassword_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(deleteAccount(accessToken, null), HttpStatus.BAD_REQUEST, "400-1")
        }
    }

    @Nested
    @DisplayName("GET /api/users/me — 내 정보 조회")
    inner class GetMe {
        @Test
        @DisplayName("1. 성공하면 200과 내 프로필 정보를 반환한다")
        fun getMe_success_returns200WithProfile() {
            val email = uniqueEmail()
            val nickname = uniqueNickname()
            val accessToken = createUserAndGetAccessToken(client, email, DEFAULT_PASSWORD, nickname)

            getMe(accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMeResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.email).isEqualTo(email)
                    assertThat(body.data.profile.nickname).isEqualTo(nickname)
                    assertThat(body.data.profile.introduction).isNull()
                    assertThat(body.data.profile.profileImageUrl).isNull()
                }
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun getMe_unauthenticated_returns401_1() {
            expectResultCode(
                client.get().uri("/api/users/me").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )
        }

        // FIXME: 만료된 액세스 토큰은 JwtFilter가 조용히 리프레시로 폴백을 시도하고, 리프레시 토큰이
        // 없으면 익명 요청으로 넘어가 최종적으로 AuthenticationEntryPoint가 401-1을 응답한다.
        // ErrorCode.TOKEN_EXPIRED(401-3)는 사용되지 않는다. 상세: docs/user-e2e-known-issues.md #2
        @Test
        @DisplayName("3. 만료된 토큰(리프레시 없음)이면 401-1을 반환한다")
        @Suppress("ForbiddenComment")
        fun getMe_expiredAccessToken_returns401_1() {
            val email = uniqueEmail()
            val nickname = uniqueNickname()
            val signUpResult = signUp(client, email, DEFAULT_PASSWORD, nickname)
            val expiredToken = expiredAccessToken(signUpResult.data.id, email, nickname, UserRole.USER)

            expectResultCode(getMe(expiredToken), HttpStatus.UNAUTHORIZED, "401-1")
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me — 내 정보 수정")
    inner class UpdateMe {
        // FIXME: request part 자체를 빼고 보내면 MissingServletRequestPartException 전용 핸들러가
        // 없어서 400이 아니라 500-1로 응답된다. 상세: docs/user-e2e-known-issues.md #3
        @Test
        @DisplayName("1. 성공하면 닉네임·소개글이 반영된 200을 반환한다 (request part 누락 시 500-1도 확인)")
        @Suppress("ForbiddenComment")
        fun updateMe_success_updatesProfile_andMissingRequestPartReturns500() {
            val email = uniqueEmail()
            val accessToken = createUserAndGetAccessToken(client, email, DEFAULT_PASSWORD, uniqueNickname())

            val emptyBody: MultiValueMap<String, HttpEntity<*>> = LinkedMultiValueMap()
            expectResultCode(patchMe(accessToken, emptyBody), HttpStatus.INTERNAL_SERVER_ERROR, "500-1")

            val newNickname = uniqueNickname()
            val newIntroduction = "e2e updated introduction"
            patchMe(accessToken, multipartRequestPart(newNickname, newIntroduction))
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserUpdateResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.profile.nickname).isEqualTo(newNickname)
                    assertThat(body.data.profile.introduction).isEqualTo(newIntroduction)
                    assertThat(body.data.profile.profileImageUrl).isNull()
                }

            val updated = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
            assertThat(updated.nickname).isEqualTo(newNickname)
            assertThat(updated.introduction).isEqualTo(newIntroduction)
        }

        @Test
        @DisplayName("2. 프로필 이미지를 함께 업로드하면 200과 profileImageUrl을 반환하고, 그 URL이 실제로 조회된다")
        fun updateMe_successWithProfileImage_returns200WithImageUrl() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val userId = session.user.id

            val multipartBody: MultiValueMap<String, HttpEntity<*>> =
                LinkedMultiValueMap<String, HttpEntity<*>>().apply {
                    add("request", jsonPart(UserUpdateRequest(null, "with image")))
                    add("profileImage", filePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                }

            var uploadedUrl: String? = null
            patchMe(accessToken, multipartBody)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserUpdateResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.profile.profileImageUrl).isNotBlank()
                    assertThat(body.data.profile.introduction).isEqualTo("with image")
                    uploadedUrl = body.data.profile.profileImageUrl
                }

            // 응답에 실린 URL이 실제로 저장된 미디어인지 되짚어 확인한다.
            // (PATCH도 POST /me/medias와 같은 UserMediaService.uploadMedia를 타므로 동일한 URL이어야 한다)
            getMedia(userId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).isNotNull()
                    assertThat(body.data.url).isEqualTo(uploadedUrl)
                    assertThat(body.data.userId).isEqualTo(userId)
                }

            // 내 정보 조회에도 같은 URL이 실린다.
            getMe(accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMeResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.profile.profileImageUrl).isEqualTo(uploadedUrl)
                }
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        fun updateMe_unauthenticated_returns401_1() {
            val response =
                client
                    .patch()
                    .uri("/api/users/me")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartRequestPart(uniqueNickname(), null))
                    .exchange()

            expectResultCode(response, HttpStatus.UNAUTHORIZED, "401-1")
        }

        @Test
        @DisplayName("4. 다른 유저의 닉네임으로 변경하면 409-3을 반환한다")
        fun updateMe_duplicateNickname_returns409_3() {
            val takenNickname = uniqueNickname()
            signUp(client, uniqueEmail(), DEFAULT_PASSWORD, takenNickname)

            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                patchMe(accessToken, multipartRequestPart(takenNickname, null)),
                HttpStatus.CONFLICT,
                "409-3",
            )
        }

        @Test
        @DisplayName("5. 닉네임이 2자 미만이면 400-1을 반환한다")
        fun updateMe_nicknameTooShort_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                patchMe(accessToken, multipartRequestPart("n", null)),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("6. 닉네임이 공백만으로 이루어지면 400-1을 반환한다")
        fun updateMe_nicknameBlank_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                patchMe(accessToken, multipartRequestPart("  ", null)),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("7. 소개글이 101자면 400-1을 반환한다")
        fun updateMe_introductionTooLong_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                patchMe(accessToken, multipartRequestPart(null, "a".repeat(101))),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }
    }

    @Nested
    @DisplayName("PUT /api/users/me/password — 비밀번호 변경")
    inner class UpdatePassword {
        @Test
        @DisplayName("1. 성공하면 200과 새 토큰을 반환하고, 새 accessToken으로 GET /me가 200이 된다")
        fun updatePassword_success_returns200WithNewTokens_andNewAccessTokenCanGetMe() {
            val email = uniqueEmail()
            val accessToken = createUserAndGetAccessToken(client, email, DEFAULT_PASSWORD, uniqueNickname())
            val newPassword = "newpassword123"

            val beforeChange = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
            val refreshTokenBeforeChange = beforeChange.refreshToken

            var newAccessToken: String? = null
            updatePassword(accessToken, DEFAULT_PASSWORD, newPassword)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserPasswordUpdateResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.accessToken).isNotBlank()
                    assertThat(body.data.refreshToken).isNotBlank()
                    assertThat(body.data.expiresIn).isEqualTo(1800)
                    newAccessToken = body.data.accessToken
                }

            val afterChange = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow()
            assertThat(afterChange.refreshToken).isNotEqualTo(refreshTokenBeforeChange)

            getMe(requireNotNull(newAccessToken))
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMeResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.email).isEqualTo(email)
                }
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun updatePassword_unauthenticated_returns401_1() {
            val response =
                client
                    .put()
                    .uri("/api/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(UserPasswordUpdateRequest(DEFAULT_PASSWORD, "newpassword123"))
                    .exchange()

            expectResultCode(response, HttpStatus.UNAUTHORIZED, "401-1")
        }

        @Test
        @DisplayName("3. 현재 비밀번호가 일치하지 않으면 400-2를 반환한다")
        fun updatePassword_wrongCurrentPassword_returns400_2() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                updatePassword(accessToken, "wrong-password", "newpassword123"),
                HttpStatus.BAD_REQUEST,
                "400-2",
            )
        }

        @Test
        @DisplayName("4. 새 비밀번호가 6자 미만이면 400-1을 반환한다")
        fun updatePassword_newPasswordTooShort_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                updatePassword(accessToken, DEFAULT_PASSWORD, "123"),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }

        @Test
        @DisplayName("5. 현재 비밀번호가 없으면 400-1을 반환한다")
        fun updatePassword_missingCurrentPassword_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                updatePassword(accessToken, null, "newpassword123"),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id} — 유저 프로필 조회")
    inner class GetUserProfile {
        @Test
        @DisplayName("1. 성공(비로그인)이면 200과 followerCount·프로필을 반환한다")
        fun getUserProfile_anonymous_returns200WithFollowerCountAndProfile() {
            getUserProfile(followerCountFixture.creatorId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserProfileResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(followerCountFixture.creatorId)
                    assertThat(body.data.followerCount).isEqualTo(followerCountFixture.followerCount)
                    assertThat(body.data.profile).isNotNull()
                }
        }

        @Test
        @DisplayName("2. 성공(로그인 상태)이면 200과 비로그인 때와 같은 내용을 반환한다")
        fun getUserProfile_authenticated_returnsSameContentAsAnonymous() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            // 케이스 1(비로그인)과 같은 값을 확인해서, 인증 여부가 응답 내용을 바꾸지 않는다는 것까지 고정한다.
            client
                .get()
                .uri("/api/users/${followerCountFixture.creatorId}")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserProfileResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(followerCountFixture.creatorId)
                    assertThat(body.data.followerCount).isEqualTo(followerCountFixture.followerCount)
                    assertThat(body.data.profile).isNotNull()
                    assertThat(body.data.profile.nickname).isNotBlank()
                }
        }

        @Test
        @DisplayName("3. 존재하지 않는 id면 404-2를 반환한다")
        fun getUserProfile_nonExistentId_returns404_2() {
            expectResultCode(getUserProfile(NON_EXISTENT_USER_ID), HttpStatus.NOT_FOUND, "404-2")
        }

        @Test
        @DisplayName("4. 탈퇴한 유저 id면 404-2를 반환한다")
        fun getUserProfile_softDeletedUserId_returns404_2() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            deleteAccount(session.accessToken, DEFAULT_PASSWORD).expectStatus().isOk()

            expectResultCode(getUserProfile(session.user.id), HttpStatus.NOT_FOUND, "404-2")
        }

        // FIXME: SecurityConfig의 permitAll이 GET /api/users/{id:\d+}로 숫자 id만 허용해서,
        // 비숫자 경로는 이 규칙에 매칭되지 않고 /api/** → authenticated 규칙으로 떨어진다.
        // 존재하지 않는 리소스(404)나 타입 불일치(400)가 아니라 401이 응답된다.
        // 상세: docs/user-e2e-known-issues.md #4
        @Test
        @DisplayName("5. 비숫자 id를 비로그인으로 호출하면 401-1을 반환한다 (정규식 미매칭)")
        @Suppress("ForbiddenComment")
        fun getUserProfile_nonNumericId_unauthenticated_returns401_1() {
            expectResultCode(
                client.get().uri("/api/users/abc").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )
        }
    }

    @Nested
    @DisplayName("POST /api/users/me/medias — 프로필 이미지 생성")
    inner class UploadMedia {
        @Test
        @DisplayName("1. 성공하면 201과 업로드된 이미지 URL·mediaType을 반환하고, 그 URL이 실제로 조회된다")
        fun uploadMedia_success_returns201WithUrlAndImageType() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val userId = session.user.id

            var uploadedUrl: String? = null
            uploadMedia(session.accessToken, mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("201-1")
                    assertThat(body.data.url).isNotBlank()
                    assertThat(body.data.userId).isEqualTo(userId)
                    assertThat(body.data.mediaType).isEqualTo(DomainMediaType.IMAGE)
                    uploadedUrl = body.data.url
                }

            // 응답만 그럴듯한 게 아니라 실제로 저장되었는지 조회로 확인한다.
            getMedia(userId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).isNotNull()
                    assertThat(body.data.url).isEqualTo(uploadedUrl)
                }
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun uploadMedia_unauthenticated_returns401_1() {
            val response =
                client
                    .post()
                    .uri("/api/users/me/medias")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                    .exchange()

            expectResultCode(response, HttpStatus.UNAUTHORIZED, "401-1")
        }

        @Test
        @DisplayName("3. 빈 파일이면 400-4를 반환한다")
        fun uploadMedia_emptyFile_returns400_4() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                uploadMedia(accessToken, mediaFilePart(ByteArray(0), "empty.png", MediaType.IMAGE_PNG)),
                HttpStatus.BAD_REQUEST,
                "400-4",
            )
        }

        @Test
        @DisplayName("4. text/plain 파일이면 415-1을 반환한다")
        fun uploadMedia_unsupportedFileType_returns415_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                uploadMedia(accessToken, mediaFilePart(PNG_BYTES, "file.txt", MediaType.TEXT_PLAIN)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "415-1",
            )
        }
    }

    @Nested
    @DisplayName("GET /api/users/{id}/medias — 프로필 이미지 조회")
    inner class GetMedia {
        @Test
        @DisplayName("1. 미디어가 있으면 200과 이미지 URL을 반환한다")
        fun getMedia_withMedia_returns200WithUrl() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val userId = session.user.id

            uploadMedia(session.accessToken, mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                .expectStatus()
                .isCreated()

            getMedia(userId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.url).isNotBlank()
                    assertThat(body.data.userId).isEqualTo(userId)
                }
        }

        @Test
        @DisplayName("2. 미디어가 없으면 200과 data=null을 반환한다 (404 아님)")
        fun getMedia_withoutMedia_returns200WithNullData() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            getMedia(session.user.id)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isNull()
                }
        }

        @Test
        @DisplayName("3. 존재하지 않는 유저면 404-2를 반환한다")
        fun getMedia_nonExistentUser_returns404_2() {
            expectResultCode(getMedia(NON_EXISTENT_USER_ID), HttpStatus.NOT_FOUND, "404-2")
        }
    }

    @Nested
    @DisplayName("GET /api/users/search — 유저 검색")
    inner class SearchUsers {
        @Test
        @DisplayName("1. 키워드가 매칭되면 200과 기대 닉네임을 포함한 content를 반환한다")
        fun searchUsers_matchingKeyword_returns200WithExpectedNicknames() {
            searchUsers(searchPagingFixture.nicknamePrefix, null, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<UserSearchResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.totalElements).isEqualTo(searchPagingFixture.userIds.size.toLong())
                    assertThat(body.data.content)
                        .extracting<String>(UserSearchResponse::nickname)
                        .contains("${searchPagingFixture.nicknamePrefix}1")
                }
        }

        @Test
        @DisplayName("2. keyword를 지정하지 않으면 200과 빈 Page를 반환한다")
        fun searchUsers_missingKeyword_returns200WithEmptyPage() {
            searchUsers(null, null, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<UserSearchResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).isEmpty()
                    assertThat(body.data.totalElements).isZero()
                }
        }

        @Test
        @DisplayName("3. 매칭되는 유저가 없는 키워드면 200과 빈 Page를 반환한다")
        fun searchUsers_noMatchKeyword_returns200WithEmptyPage() {
            // 픽스처 접두사 자체에 존재하지 않는 접미사를 붙여, 다른 테스트가 회원가입으로 만든
            // 계정과 겹치지 않으면서도 빈 결과가 나오도록 한다.
            searchUsers("${searchPagingFixture.nicknamePrefix}-no-match-xyz", null, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<UserSearchResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).isEmpty()
                    assertThat(body.data.totalElements).isZero()
                }
        }

        @Test
        @DisplayName("4. page·size를 지정하면 페이징이 반영된다")
        fun searchUsers_withPageAndSize_reflectsPaging() {
            searchUsers(searchPagingFixture.nicknamePrefix, 0, 2)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<UserSearchResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).hasSize(2)
                    assertThat(body.data.totalElements).isEqualTo(searchPagingFixture.userIds.size.toLong())
                    assertThat(body.data.size).isEqualTo(2)
                    assertThat(body.data.number).isZero()
                }
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me/medias — 프로필 이미지 삭제")
    inner class DeleteMedia {
        private fun deleteProfileMedia(accessToken: String): RestTestClient.ResponseSpec =
            client
                .method(HttpMethod.DELETE)
                .uri("/api/users/me/medias")
                .header("Authorization", bearer(accessToken))
                .exchange()

        @Test
        @DisplayName("1. 성공하면 200을 반환하고, 이후 조회하면 data=null이 된다")
        fun deleteMedia_success_returns200_andMediaIsGone() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val userId = session.user.id

            uploadMedia(accessToken, mediaFilePart(PNG_BYTES, "profile.png", MediaType.IMAGE_PNG))
                .expectStatus()
                .isCreated()
            getMedia(userId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).isNotNull()
                }

            expectResultCode(deleteProfileMedia(accessToken), HttpStatus.OK, "200-1")

            // 삭제라는 부작용 자체를 확인한다. 미디어가 없는 유저는 404가 아니라
            // 200 + data=null 이 계약이다(GetMedia 2번 케이스와 동일).
            getMedia(userId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isNull()
                }
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun deleteMedia_unauthenticated_returns401_1() {
            expectResultCode(
                client.method(HttpMethod.DELETE).uri("/api/users/me/medias").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )
        }

        @Test
        @DisplayName("3. 미디어가 없으면 404-7을 반환한다 (404-2와 다름)")
        fun deleteMedia_noMedia_returns404_7() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(deleteProfileMedia(accessToken), HttpStatus.NOT_FOUND, "404-7")
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    inner class Scenarios {
        @Test
        @DisplayName("1. 회원가입 → 로그인 → GET /me → PATCH /me → GET /{id}에 수정 내용이 반영된다")
        fun signUpLoginUpdateProfile_isReflectedInPublicProfile() {
            val email = uniqueEmail()
            val nickname = uniqueNickname()

            val session = createUserAndLogin(client, email, DEFAULT_PASSWORD, nickname)
            val userId = session.user.id
            val accessToken = session.accessToken
            assertThat(userId).isNotNull()

            // 수정 전 상태를 GET /me로 확인한다.
            getMe(accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMeResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(userId)
                    assertThat(body.data.email).isEqualTo(email)
                    assertThat(body.data.profile.nickname).isEqualTo(nickname)
                    assertThat(body.data.profile.introduction).isNull()
                }

            val newNickname = uniqueNickname()
            val newIntroduction = "시나리오에서 바꾼 소개글"
            patchMe(accessToken, multipartRequestPart(newNickname, newIntroduction))
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserUpdateResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(userId)
                    assertThat(body.data.profile.nickname).isEqualTo(newNickname)
                    assertThat(body.data.profile.introduction).isEqualTo(newIntroduction)
                }

            // 같은 토큰으로 다시 조회해도 바뀐 값이 보인다.
            getMe(accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMeResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.profile.nickname).isEqualTo(newNickname)
                    assertThat(body.data.profile.introduction).isEqualTo(newIntroduction)
                }

            // 공개 프로필(비로그인 조회)에도 같은 값이 반영된다.
            getUserProfile(userId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserProfileResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(userId)
                    assertThat(body.data.profile.nickname).isEqualTo(newNickname)
                    assertThat(body.data.profile.introduction).isEqualTo(newIntroduction)
                }
        }

        @Test
        @DisplayName("2. 로그인 → 비밀번호 변경 후, 옛 비밀번호 로그인은 401-2가 되고 새 비밀번호 로그인은 성공한다")
        fun changePassword_invalidatesOldPassword_andNewPasswordWorks() {
            val email = uniqueEmail()
            val accessToken = createUserAndGetAccessToken(client, email, DEFAULT_PASSWORD, uniqueNickname())
            val newPassword = "newpassword123"

            expectResultCode(
                updatePassword(accessToken, DEFAULT_PASSWORD, newPassword),
                HttpStatus.OK,
                "200-1",
            )

            // 옛 비밀번호로는 더 이상 로그인할 수 없다.
            expectResultCode(
                loginRequest(client, email, DEFAULT_PASSWORD),
                HttpStatus.UNAUTHORIZED,
                "401-2",
            )

            // 새 비밀번호로는 로그인되고, 그 토큰으로 GET /me도 통과한다.
            val reLogin = login(client, email, newPassword)
            assertThat(reLogin.resultCode).isEqualTo("200-1")
            assertThat(reLogin.data.accessToken).isNotBlank()
            assertThat(reLogin.data.user.email).isEqualTo(email)

            getMe(reLogin.data.accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<UserMeResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.email).isEqualTo(email)
                }
        }

        @Test
        @DisplayName("3. 회원가입 → 탈퇴하면 로그인 401-2, GET /{id} 404-2, 검색 결과에서도 빠진다")
        fun deleteAccount_blocksLogin_hidesProfile_andExcludesFromSearch() {
            val email = uniqueEmail()
            // 이 시나리오에서만 만들어지는 닉네임이라, 그대로 검색 키워드로 쓰면
            // 다른 테스트가 만든 계정과 겹치지 않는다.
            val nickname = uniqueNickname()

            val session = createUserAndLogin(client, email, DEFAULT_PASSWORD, nickname)
            val userId = session.user.id

            // 탈퇴 전에는 프로필도 보이고 검색에도 잡힌다.
            getUserProfile(userId).expectStatus().isOk()
            searchUsers(nickname, null, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<UserSearchResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.totalElements).isEqualTo(1L)
                    assertThat(body.data.content)
                        .extracting<Long>(UserSearchResponse::id)
                        .containsExactly(userId)
                }

            deleteAccount(session.accessToken, DEFAULT_PASSWORD).expectStatus().isOk()

            expectResultCode(
                loginRequest(client, email, DEFAULT_PASSWORD),
                HttpStatus.UNAUTHORIZED,
                "401-2",
            )
            expectResultCode(getUserProfile(userId), HttpStatus.NOT_FOUND, "404-2")

            searchUsers(nickname, null, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<UserSearchResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).isEmpty()
                    assertThat(body.data.totalElements).isZero()
                }
        }
    }

    companion object {
        private const val DEFAULT_PASSWORD = "password123"
        private const val NON_EXISTENT_USER_ID = 999_999_999L
        private val PNG_BYTES =
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

        @JvmStatic
        @AfterAll
        fun cleanUpUploadedFiles() {
            val uploadDir = Path.of("build", "test-uploads")
            if (!Files.exists(uploadDir)) return

            Files.walk(uploadDir).use { walk ->
                walk
                    .filter { path -> path != uploadDir }
                    .sorted(Comparator.reverseOrder())
                    .forEach { path ->
                        try {
                            Files.deleteIfExists(path)
                        } catch (e: IOException) {
                            throw UncheckedIOException(e)
                        }
                    }
            }
        }
    }
}
