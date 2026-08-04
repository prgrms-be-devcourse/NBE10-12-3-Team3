package com.scommit.domain.series.series.controller

// 배치 1 — 시리즈 CRUD 코어 (#1 생성, #9 단건 조회, #10 수정, #11 삭제)
// 계획서: docs/plans/series-e2e-plan.md (E-3 배치 1), 규약: docs/e2e-test-convention.md
//
// 결정사항 (변경 금지 — 규약 문서의 요약)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient(spring-test, 서블릿 계열).
//   Mock / MockMvc / @MockitoBean / webflux 의존성 일절 사용 금지, 실제 HTTP 요청만.
// - DB: 이 클래스 전용 H2 in-memory(seriesE2edb). 다른 @SpringBootTest 와 datasource.url을
//   공유하면 같은 JVM 안에서 create-drop이 서로의 스키마를 침범할 수 있다(규약 4장).
// - 픽스처: 전량 실제 API 조립. @TestConfiguration 을 만들지 않는다(계획서 C-1).
//   시리즈/유저 생성 헬퍼는 아직 이 클래스에서만 쓰이므로 private 헬퍼로 둔다(계획서 C-2).
// - 응답 본문은 RsData 가 아니라 ApiResponse 미러 레코드로 받는다(규약 3장).
// - 미인증(401-1)은 AuthenticationEntryPoint 와 컨트롤러의 BusinessException(UNAUTHORIZED)이
//   같은 코드를 쓰므로 msg 까지 검증한다(규약 6장, 계획서 A-2). 실제로는 SecurityConfig 가
//   먼저 잘라내므로 언제나 EntryPoint 의 "로그인 후 이용해주세요." 가 나온다.

import com.scommit.domain.media.media.repository.MediaRepository
import com.scommit.domain.post.post.dto.PostCreateRequest
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.series.series.dto.SeriesCreateRequest
import com.scommit.domain.series.series.dto.SeriesListResponse
import com.scommit.domain.series.series.dto.SeriesResponse
import com.scommit.domain.series.series.dto.SeriesUpdateRequest
import com.scommit.domain.series.series.repository.SeriesRepository
import com.scommit.domain.series.seriesmedia.dto.SeriesMediaResponse
import com.scommit.domain.series.seriesmedia.repository.SeriesMediaRepository
import com.scommit.domain.user.user.dto.UserDeleteRequest
import com.scommit.global.dto.PageResponse
import com.scommit.global.e2e.ApiResponse
import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.bearer
import com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken
import com.scommit.global.e2e.E2ETestSupport.createUserAndLogin
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.UUID
import com.scommit.domain.media.media.entity.MediaType as DomainMediaType

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:seriesE2edb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
class SeriesControllerE2ETest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @Autowired
    private lateinit var seriesRepository: SeriesRepository

    @Autowired
    private lateinit var seriesMediaRepository: SeriesMediaRepository

    @Autowired
    private lateinit var mediaRepository: MediaRepository

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    private fun newUserToken(): String =
        createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

    private fun createSeriesRequest(
        accessToken: String,
        title: String?,
        body: String?,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/series")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(SeriesCreateRequest(title, body))
            .exchange()

    /** 생성 성공을 전제로 시리즈 id를 돌려준다. 다른 API의 대상 시리즈를 만들 때 쓴다. */
    private fun createSeries(
        accessToken: String,
        title: String,
        body: String?,
    ): Long =
        checkNotNull(
            createSeriesRequest(accessToken, title, body)
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SeriesResponse>>()
                .returnResult()
                .responseBody,
        ).data.id

    private fun getSeries(id: Any): RestTestClient.ResponseSpec = client.get().uri("/api/series/$id").exchange()

    private fun updateSeriesRequest(
        accessToken: String,
        id: Any,
        title: String?,
        body: String?,
    ): RestTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/series/$id")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(SeriesUpdateRequest(title, body))
            .exchange()

    private fun deleteSeriesRequest(
        accessToken: String,
        id: Any,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/series/$id")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** 미인증 401은 두 지점에서 날 수 있어 msg 까지 본다(규약 6장, 계획서 A-2). */
    private fun expectEntryPointUnauthorized(response: RestTestClient.ResponseSpec) {
        response
            .expectStatus()
            .isUnauthorized()
            .expectBody(ApiResponse.VOID_BODY)
            .value { body ->
                checkNotNull(body)
                assertThat(body.resultCode).isEqualTo("401-1")
                assertThat(body.msg).isEqualTo("로그인 후 이용해주세요.")
            }
    }

    // ── 배치 2: 목록·검색 조회 (#2, #3, #4, #5) ─────────────────────────────

    /** #2 GET /api/series 는 Slice를 PageResponse로 감싸지 않고 그대로 직렬화한다(D-6). */
    private data class SliceResult<T>(
        val content: List<T>,
        val size: Int,
        val number: Int,
        val first: Boolean,
        val last: Boolean,
    )

    private fun getSeriesListRequest(queryString: String): RestTestClient.ResponseSpec =
        client.get().uri("/api/series$queryString").exchange()

    private fun searchSeriesRequest(queryString: String): RestTestClient.ResponseSpec =
        client.get().uri("/api/series/search$queryString").exchange()

    private fun getUserSeriesListRequest(
        userId: Any,
        queryString: String,
    ): RestTestClient.ResponseSpec = client.get().uri("/api/series/users/$userId$queryString").exchange()

    private fun getMySeriesListRequest(
        accessToken: String,
        queryString: String,
    ): RestTestClient.ResponseSpec =
        client
            .get()
            .uri("/api/series/me$queryString")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** UserControllerE2ETest에도 같은 모양의 private 헬퍼가 있다 — 계획서 C-2와 동일하게, 지금은
     *  각 클래스 안에 둔다(규약 10장: 실제 중복이 쌓이면 그때 E2ETestSupport로 승격). */
    private fun deleteAccount(
        accessToken: String,
        password: String,
    ): RestTestClient.ResponseSpec =
        client
            .method(HttpMethod.DELETE)
            .uri("/api/users")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(UserDeleteRequest(password))
            .exchange()

    // ── 배치 3: 썸네일 미디어 (#12, #13, #14) ─────────────────────────────

    /** 규약 2장: MultipartBodyBuilder는 reactive-streams 미의존 클래스패스에서 NoClassDefFoundError가
     *  나서 쓸 수 없다. FormHttpMessageConverter가 맵의 key를 part 이름으로 쓰는 것을 이용해 직접 조립한다.
     *  contentType이 null이면 part에 Content-Type 헤더 자체가 안 붙는다(9번 케이스용). */
    private fun filePart(
        content: ByteArray,
        filename: String,
        contentType: MediaType?,
    ): HttpEntity<Resource> {
        val headers = HttpHeaders()
        contentType?.let { headers.contentType = it }
        val resource =
            object : ByteArrayResource(content) {
                override fun getFilename(): String = filename
            }
        return HttpEntity(resource, headers)
    }

    private fun mediaFilePart(
        content: ByteArray,
        filename: String,
        contentType: MediaType?,
    ): MultiValueMap<String, HttpEntity<*>> =
        LinkedMultiValueMap<String, HttpEntity<*>>().apply {
            add("file", filePart(content, filename, contentType))
        }

    private fun uploadMediaRequest(
        accessToken: String,
        seriesId: Any,
        multipartBody: MultiValueMap<String, HttpEntity<*>>,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/series/$seriesId/medias")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(multipartBody)
            .exchange()

    private fun getMediaRequest(seriesId: Any): RestTestClient.ResponseSpec =
        client.get().uri("/api/series/$seriesId/medias").exchange()

    private fun deleteMediaRequest(
        accessToken: String,
        seriesId: Any,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/series/$seriesId/medias")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** 업로드 성공을 전제로 응답을 돌려준다. 다른 API의 "썸네일이 이미 있는 시리즈" 사전 상태를 만들 때 쓴다. */
    private fun uploadSeriesMedia(
        accessToken: String,
        seriesId: Long,
        content: ByteArray,
        filename: String,
    ): SeriesMediaResponse =
        checkNotNull(
            uploadMediaRequest(accessToken, seriesId, mediaFilePart(content, filename, MediaType.IMAGE_PNG))
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .returnResult()
                .responseBody,
        ).data

    /** SeriesMediaResponse.url (예: "series/&lt;uuid&gt;_thumbnail.png")로부터 실제 디스크 경로를 만든다.
     *  SERIES_UPLOAD_DIR 주석 참고 — "series/" 접두사를 뗀 나머지가 실제 저장 파일명이다. */
    private fun uploadedFilePath(url: String): Path = SERIES_UPLOAD_DIR.resolve(url.removePrefix("series/"))

    // ── 배치 4: 포스트 연관 조작 + 시나리오 (#6, #7, #8) ─────────────────────

    /** 생성 성공을 전제로 포스트 id를 돌려준다. seriesId는 null로 두고 시리즈 편입은 #7로 한다
     *  (계획서 C-1: "시리즈에 속한 포스트"는 POST /api/posts + POST /api/series/{id}/posts/{postId}). */
    private fun createPost(
        accessToken: String,
        title: String,
        body: String = "$title 본문",
        publishStatus: PublishStatus = PublishStatus.PUBLIC,
        accessLevel: PostAccessLevel = PostAccessLevel.FREE,
    ): Long =
        checkNotNull(
            client
                .post()
                .uri("/api/posts")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(PostCreateRequest(null, title, body, publishStatus, accessLevel))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostResponse>>()
                .returnResult()
                .responseBody,
        ).data.id

    /** 시리즈에 편입된 포스트를 만든다. 두 API를 잇는 픽스처라 각 단계의 성공을 확인하고 넘어간다. */
    private fun createPostInSeries(
        accessToken: String,
        seriesId: Long,
        title: String,
    ): Long {
        val postId = createPost(accessToken, title)
        expectResultCode(addPostToSeriesRequest(accessToken, seriesId, postId), HttpStatus.OK, "200-1")
        return postId
    }

    private fun deletePostRequest(
        accessToken: String,
        postId: Any,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/posts/$postId")
            .header("Authorization", bearer(accessToken))
            .exchange()

    private fun getPostRequest(postId: Any): RestTestClient.ResponseSpec =
        client.get().uri("/api/posts/$postId").exchange()

    /** accessToken 이 주어지면 인증 헤더를 붙이고, 없으면 비로그인으로 요청한다. */
    private fun getSeriesPostsRequest(
        seriesId: Any,
        accessToken: String? = null,
    ): RestTestClient.ResponseSpec {
        val request = client.get().uri("/api/series/$seriesId/posts")
        return (accessToken?.let { request.header("Authorization", bearer(it)) } ?: request).exchange()
    }

    private fun addPostToSeriesRequest(
        accessToken: String,
        seriesId: Any,
        postId: Any,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/series/$seriesId/posts/$postId")
            .header("Authorization", bearer(accessToken))
            .exchange()

    private fun removePostFromSeriesRequest(
        accessToken: String,
        seriesId: Any,
        postId: Any,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/series/$seriesId/posts/$postId")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** 시리즈에 담긴 포스트 id들. 순서는 보장되지 않으므로(Q3, known-issues #6) 집합으로만 쓴다. */
    private fun seriesPostIds(seriesId: Any): List<Long> =
        checkNotNull(
            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .returnResult()
                .responseBody,
        ).data.map(PostListResponse::id)

    /** 키 집합 검증용 — DTO로 받으면 "응답에만 있고 DTO에 없는 키"를 볼 수 없다(계획서 C-3). */
    private fun firstSeriesPostNode(response: RestTestClient.ResponseSpec): JsonNode {
        val raw =
            checkNotNull(
                response
                    .expectStatus()
                    .isOk()
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody,
            )
        val data = ObjectMapper().readTree(raw).get("data")
        assertThat(data.size()).isEqualTo(1)
        return data.get(0)
    }

    private fun likePost(
        accessToken: String,
        postId: Long,
    ) {
        expectResultCode(
            client
                .post()
                .uri("/api/posts/$postId/likes")
                .header("Authorization", bearer(accessToken))
                .exchange(),
            HttpStatus.CREATED,
            "201-1",
        )
    }

    private fun bookmarkPost(
        accessToken: String,
        postId: Long,
    ) {
        expectResultCode(
            client
                .post()
                .uri("/api/posts/$postId/bookmarks")
                .header("Authorization", bearer(accessToken))
                .exchange(),
            HttpStatus.CREATED,
            "201-1",
        )
    }

    /** #4 GET /users/{userId} 응답에서 해당 시리즈 한 건을 뽑는다(시나리오의 postCount/thumbnailUrl 확인용). */
    private fun userSeriesListEntry(
        userId: Long,
        seriesId: Long,
    ): SeriesListResponse =
        checkNotNull(
            getUserSeriesListRequest(userId, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .returnResult()
                .responseBody,
        ).data
            .content
            .find { series -> series.id == seriesId }
            ?: throw AssertionError("유저 시리즈 목록에 시리즈 $seriesId 가 없다")

    @Nested
    @DisplayName("POST /api/series — 시리즈 생성")
    inner class CreateSeries {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun createSeries_unauthenticated_returns401_1() {
            expectEntryPointUnauthorized(
                client
                    .post()
                    .uri("/api/series")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(SeriesCreateRequest("미인증 시리즈", "본문"))
                    .exchange(),
            )
        }

        @Test
        @DisplayName("2. 성공하면 201과 시리즈 정보를 반환하고, GET /{id}로 다시 조회된다")
        fun createSeries_success_returns201AndIsRetrievable() {
            val email = uniqueEmail()
            val nickname = uniqueNickname()
            val accessToken = createUserAndGetAccessToken(client, email, DEFAULT_PASSWORD, nickname)

            var createdId: Long? = null
            createSeriesRequest(accessToken, "e2e 생성 시리즈", "e2e 생성 본문")
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("201-1")
                    assertThat(body.data.id).isNotNull()
                    assertThat(body.data.nickname).isEqualTo(nickname)
                    assertThat(body.data.title).isEqualTo("e2e 생성 시리즈")
                    assertThat(body.data.body).isEqualTo("e2e 생성 본문")
                    assertThat(body.data.createdAt).isNotNull()
                    assertThat(body.data.updatedAt).isNotNull()
                    createdId = body.data.id
                }

            // 응답만 그럴듯한 게 아니라 실제로 저장되었는지 후속 조회로 확인한다.
            getSeries(requireNotNull(createdId))
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(createdId)
                    assertThat(body.data.title).isEqualTo("e2e 생성 시리즈")
                    assertThat(body.data.body).isEqualTo("e2e 생성 본문")
                    assertThat(body.data.nickname).isEqualTo(nickname)
                }
        }

        @Test
        @DisplayName("3. body가 null이어도 201로 생성되고 body는 null로 저장된다")
        fun createSeries_nullBody_returns201WithNullBody() {
            val accessToken = newUserToken()

            var createdId: Long? = null
            createSeriesRequest(accessToken, "본문 없는 시리즈", null)
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("201-1")
                    assertThat(body.data.title).isEqualTo("본문 없는 시리즈")
                    assertThat(body.data.body).isNull()
                    createdId = body.data.id
                }

            getSeries(requireNotNull(createdId))
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.body).isNull()
                }
        }

        @Test
        @DisplayName("4. title이 없으면 400-1과 \"제목은 필수입니다.\"를 반환한다")
        fun createSeries_missingTitle_returns400_1() {
            val accessToken = newUserToken()

            createSeriesRequest(accessToken, null, "본문")
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("400-1")
                    assertThat(body.msg).isEqualTo("제목은 필수입니다.")
                }
        }

        @Test
        @DisplayName("5. title이 공백만으로 이루어지면 400-1과 \"제목은 필수입니다.\"를 반환한다")
        fun createSeries_blankTitle_returns400_1() {
            val accessToken = newUserToken()

            createSeriesRequest(accessToken, "   ", "본문")
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("400-1")
                    assertThat(body.msg).isEqualTo("제목은 필수입니다.")
                }
        }

        @Test
        @DisplayName("6. JSON 형식이 올바르지 않으면 400-1과 파싱 실패 메시지를 반환한다")
        fun createSeries_malformedJson_returns400_1WithParseErrorMessage() {
            val accessToken = newUserToken()

            // 본문이 SeriesCreateRequest가 아니라 깨진 문자열이라 createSeriesRequest 헬퍼를 쓸 수 없다.
            // 같은 400-1이지만 검증 실패(4·5번)와 달리 파싱 실패라서 msg로 구분한다(규약 6장).
            client
                .post()
                .uri("/api/series")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ \"title\": ")
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
    @DisplayName("GET /api/series/{id} — 시리즈 상세 조회")
    inner class GetSeries {
        @Test
        @DisplayName("1. 비로그인으로 조회해도 200과 시리즈 정보를 반환한다")
        fun getSeries_anonymous_returns200WithSeries() {
            val nickname = uniqueNickname()
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, nickname)
            val seriesId = createSeries(accessToken, "비로그인 조회 시리즈", "비로그인 조회 본문")

            getSeries(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(seriesId)
                    assertThat(body.data.nickname).isEqualTo(nickname)
                    assertThat(body.data.title).isEqualTo("비로그인 조회 시리즈")
                    assertThat(body.data.body).isEqualTo("비로그인 조회 본문")
                    assertThat(body.data.createdAt).isNotNull()
                    assertThat(body.data.updatedAt).isNotNull()
                }
        }

        @Test
        @DisplayName("2. 로그인한 다른 유저가 조회해도 200을 반환한다 (상세 조회에 소유자 제한이 없다)")
        fun getSeries_otherUser_returns200() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "타인이 조회할 시리즈", "본문")

            val otherToken = newUserToken()

            client
                .get()
                .uri("/api/series/$seriesId")
                .header("Authorization", bearer(otherToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(seriesId)
                    assertThat(body.data.title).isEqualTo("타인이 조회할 시리즈")
                }
        }

        @Test
        @DisplayName("3. 존재하지 않는 id면 404-5를 반환한다")
        fun getSeries_nonExistentId_returns404_5() {
            expectResultCode(getSeries(NON_EXISTENT_SERIES_ID), HttpStatus.NOT_FOUND, "404-5")
        }

        @Test
        @DisplayName("4. 삭제된 시리즈면 404-5를 반환한다")
        fun getSeries_softDeletedSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제될 시리즈", "본문")

            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(getSeries(seriesId), HttpStatus.NOT_FOUND, "404-5")
        }

        // FIXME: SecurityConfig의 permitAll이 GET /api/series/{id:\d+}로 숫자 id만 허용해서,
        // 비숫자 경로는 이 규칙에 매칭되지 않고 /api/** → authenticated 규칙으로 떨어진다.
        // 컨트롤러에 닿기 전에 시큐리티 단계에서 잘려 400/404가 아니라 401이 응답된다.
        // user 도메인과 완전히 같은 구조다. 상세: docs/user-e2e-known-issues.md #4
        @Test
        @DisplayName("5. 비숫자 id를 비로그인으로 호출하면 401-1을 반환한다 (정규식 미매칭)")
        @Suppress("ForbiddenComment")
        fun getSeries_nonNumericId_unauthenticated_returns401_1() {
            expectEntryPointUnauthorized(getSeries("abc"))
        }

        @Test
        @DisplayName("6. 비숫자 id를 로그인 상태로 호출하면 400-1을 반환한다")
        fun getSeries_nonNumericId_authenticated_returns400_1() {
            val accessToken = newUserToken()

            // 5번과 같은 요청인데 인증만 붙였다. 시큐리티를 통과하면 컨트롤러 바인딩까지 가서
            // MethodArgumentTypeMismatchException → 400-1 이 된다.
            expectResultCode(
                client
                    .get()
                    .uri("/api/series/abc")
                    .header("Authorization", bearer(accessToken))
                    .exchange(),
                HttpStatus.BAD_REQUEST,
                "400-1",
            )
        }
    }

    @Nested
    @DisplayName("PUT /api/series/{id} — 시리즈 수정")
    inner class UpdateSeries {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun updateSeries_unauthenticated_returns401_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "미인증 수정 대상", "본문")

            expectEntryPointUnauthorized(
                client
                    .put()
                    .uri("/api/series/$seriesId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(SeriesUpdateRequest("수정된 제목", "수정된 본문"))
                    .exchange(),
            )
        }

        @Test
        @DisplayName("2. 소유자가 수정하면 200과 수정된 내용을 반환하고, GET /{id}에도 반영된다")
        fun updateSeries_owner_returns200AndIsReflected() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "수정 전 제목", "수정 전 본문")

            updateSeriesRequest(accessToken, seriesId, "수정 후 제목", "수정 후 본문")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(seriesId)
                    assertThat(body.data.title).isEqualTo("수정 후 제목")
                    assertThat(body.data.body).isEqualTo("수정 후 본문")
                }

            getSeries(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.title).isEqualTo("수정 후 제목")
                    assertThat(body.data.body).isEqualTo("수정 후 본문")
                }
        }

        @Test
        @DisplayName("3. body를 null로 수정하면 실제로 null이 된다 (null 무시가 아니다)")
        fun updateSeries_nullBody_clearsBody() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "본문 지울 시리즈", "지워질 본문")

            // Series.update()에는 null 가드가 없다. User.update()가 null을 무시하는 것과 반대라서
            // 같은 도메인 안에서도 수정 시맨틱이 다르다 — 실제 동작을 그대로 고정한다.
            updateSeriesRequest(accessToken, seriesId, "본문 지운 시리즈", null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.title).isEqualTo("본문 지운 시리즈")
                    assertThat(body.data.body).isNull()
                }

            getSeries(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.body).isNull()
                }
        }

        @Test
        @DisplayName("4. 타인의 시리즈를 수정하면 403-1을 반환한다")
        fun updateSeries_otherUsersSeries_returns403_1() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "남의 시리즈", "본문")

            val otherToken = newUserToken()

            expectResultCode(
                updateSeriesRequest(otherToken, seriesId, "빼앗은 제목", "빼앗은 본문"),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            // 실패한 요청이 아무것도 바꾸지 않았는지 확인한다.
            getSeries(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.title).isEqualTo("남의 시리즈")
                }
        }

        @Test
        @DisplayName("5. 존재하지 않는 id면 404-5를 반환한다")
        fun updateSeries_nonExistentId_returns404_5() {
            val accessToken = newUserToken()

            expectResultCode(
                updateSeriesRequest(accessToken, NON_EXISTENT_SERIES_ID, "제목", "본문"),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("6. 삭제된 시리즈면 404-5를 반환한다")
        fun updateSeries_softDeletedSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 수정할 시리즈", "본문")

            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(
                updateSeriesRequest(accessToken, seriesId, "수정 제목", "수정 본문"),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("7. title이 공백만으로 이루어지면 400-1과 \"제목은 필수입니다.\"를 반환한다")
        fun updateSeries_blankTitle_returns400_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "공백 제목으로 바꿀 시리즈", "본문")

            updateSeriesRequest(accessToken, seriesId, "   ", "본문")
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("400-1")
                    assertThat(body.msg).isEqualTo("제목은 필수입니다.")
                }
        }

        @Test
        @DisplayName("8. title이 없으면 400-1과 \"제목은 필수입니다.\"를 반환한다")
        fun updateSeries_missingTitle_returns400_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "제목 없이 수정할 시리즈", "본문")

            updateSeriesRequest(accessToken, seriesId, null, "본문")
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("400-1")
                    assertThat(body.msg).isEqualTo("제목은 필수입니다.")
                }
        }

        @Test
        @DisplayName("9. 수정에 성공하면 updatedAt이 createdAt 이상이면서 수정 전 updatedAt 이상으로 갱신된다")
        fun updateSeries_owner_updatesUpdatedAt() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "updatedAt 검증용 시리즈", "본문")

            val before =
                checkNotNull(
                    getSeries(seriesId)
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<SeriesResponse>>()
                        .returnResult()
                        .responseBody,
                ).data

            // 등호(>=)로 비교해 같은 밀리초에 생성·수정되어도 흔들리지 않게 한다.
            updateSeriesRequest(accessToken, seriesId, "updatedAt 검증용 시리즈 수정", "수정 본문")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.updatedAt).isAfterOrEqualTo(before.createdAt)
                    assertThat(body.data.updatedAt).isAfterOrEqualTo(before.updatedAt)
                }
        }
    }

    @Nested
    @DisplayName("DELETE /api/series/{id} — 시리즈 삭제")
    inner class DeleteSeries {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun deleteSeries_unauthenticated_returns401_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "미인증 삭제 대상", "본문")

            expectEntryPointUnauthorized(
                client.delete().uri("/api/series/$seriesId").exchange(),
            )

            // 삭제되지 않았는지 확인한다.
            getSeries(seriesId).expectStatus().isOk()
        }

        @Test
        @DisplayName("2. 소유자가 삭제하면 200을 반환하고, 이후 GET /{id}는 404-5가 된다")
        fun deleteSeries_owner_returns200_andSeriesIsGone() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제할 시리즈", "본문")

            expectResultCode(deleteSeriesRequest(accessToken, seriesId), HttpStatus.OK, "200-1")

            expectResultCode(getSeries(seriesId), HttpStatus.NOT_FOUND, "404-5")
        }

        @Test
        @DisplayName("3. 타인의 시리즈를 삭제하면 403-1을 반환한다")
        fun deleteSeries_otherUsersSeries_returns403_1() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "남의 삭제 대상 시리즈", "본문")

            val otherToken = newUserToken()

            expectResultCode(deleteSeriesRequest(otherToken, seriesId), HttpStatus.FORBIDDEN, "403-1")

            // 실패한 요청이 시리즈를 지우지 않았는지 확인한다.
            getSeries(seriesId).expectStatus().isOk()
        }

        @Test
        @DisplayName("4. 존재하지 않는 id면 404-5를 반환한다")
        fun deleteSeries_nonExistentId_returns404_5() {
            val accessToken = newUserToken()

            expectResultCode(
                deleteSeriesRequest(accessToken, NON_EXISTENT_SERIES_ID),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("5. 이미 삭제한 시리즈를 다시 삭제하면 404-5를 반환한다")
        fun deleteSeries_alreadyDeleted_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "두 번 삭제할 시리즈", "본문")

            expectResultCode(deleteSeriesRequest(accessToken, seriesId), HttpStatus.OK, "200-1")

            // soft delete된 시리즈는 findByIdAndDeletedAtIsNull에 걸리지 않아 소유자여도 404-5다.
            // series/user 도메인 간 재삭제 응답이 다른 것에 대해서는 규약 11장 참고.
            expectResultCode(deleteSeriesRequest(accessToken, seriesId), HttpStatus.NOT_FOUND, "404-5")
        }

        // FIXME: SeriesService.deleteSeries()는 시리즈에 속한 Post만 series_id를 null로 떼어내고
        // SeriesMedia는 손대지 않는다. Series에 SeriesMedia 역방향 컬렉션도, cascade/orphanRemoval
        // 설정도 없어서 시리즈를 soft delete해도 series_media 행, media 행, 디스크 파일이 그대로 남는다.
        // 이후 GET /{id}/medias는 시리즈가 없다며 404-5를 주므로 API로는 접근할 수 없는 고아 데이터가 된다.
        // 상세: docs/series-e2e-known-issues.md #2
        @Test
        @DisplayName("6. 삭제해도 썸네일(series_media/media 행, 파일)은 정리되지 않고 그대로 남는다 (D-2, FIXME)")
        @Suppress("ForbiddenComment")
        fun deleteSeries_owner_leavesThumbnailOrphaned() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "썸네일 고아 확인용 시리즈", "본문")
            val uploaded = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "thumbnail.png")
            val mediaCountBeforeDelete = mediaRepository.count()

            expectResultCode(deleteSeriesRequest(accessToken, seriesId), HttpStatus.OK, "200-1")

            val series = checkNotNull(seriesRepository.findByIdOrNull(seriesId))
            assertThat(series.deletedAt).isNotNull()

            val seriesMediaAfterDelete = seriesMediaRepository.findBySeries(series)
            assertThat(seriesMediaAfterDelete).isPresent()
            assertThat(seriesMediaAfterDelete.get().id).isEqualTo(uploaded.id)
            assertThat(mediaRepository.count()).isEqualTo(mediaCountBeforeDelete)
            assertThat(Files.exists(uploadedFilePath(requireNotNull(uploaded.url)))).isTrue()
        }
    }

    @Nested
    @DisplayName("GET /api/series — 시리즈 전체 조회")
    inner class GetSeriesList {
        @Test
        @DisplayName("1. 비로그인으로 조회해도 200과 내가 만든 시리즈 id가 목록에 포함된다")
        fun getSeriesList_anonymous_returns200WithCreatedIdIncluded() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "전체 목록 포함 확인 시리즈", "본문")

            getSeriesListRequest("")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .contains(seriesId)
                }
        }

        @Test
        @DisplayName("2. 응답이 PageResponse가 아니라 Slice 원본 스키마 그대로 노출된다 (D-6)")
        fun getSeriesList_exposesRawSliceSchema() {
            newUserToken()

            val raw =
                checkNotNull(
                    getSeriesListRequest("?size=1")
                        .expectStatus()
                        .isOk()
                        .expectBody(String::class.java)
                        .returnResult()
                        .responseBody,
                )

            val data = ObjectMapper().readTree(raw).get("data")
            // Slice 원본 필드 — PageResponse의 pageNumber/totalElements/isLast 등과는 이름이 다르다.
            assertThat(data.has("content")).isTrue()
            assertThat(data.has("first")).isTrue()
            assertThat(data.has("last")).isTrue()
            assertThat(data.has("number")).isTrue()
            assertThat(data.has("size")).isTrue()
            assertThat(data.has("totalElements")).isFalse()
            assertThat(data.has("pageNumber")).isFalse()
        }

        @Test
        @DisplayName("3. 다른 유저가 만든 시리즈도 함께 노출된다 (개인화 없음)")
        fun getSeriesList_includesOtherUsersSeries() {
            val ownerAToken = newUserToken()
            val seriesAId = createSeries(ownerAToken, "A의 전체목록 시리즈", "본문")
            val ownerBToken = newUserToken()
            val seriesBId = createSeries(ownerBToken, "B의 전체목록 시리즈", "본문")

            getSeriesListRequest("")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .contains(seriesAId, seriesBId)
                }
        }

        @Test
        @DisplayName("4. size·page 파라미터가 반영된다")
        fun getSeriesList_respectsSizeAndPage() {
            val accessToken = newUserToken()
            val olderId = createSeries(accessToken, "size page 이전 시리즈", "본문")
            val newerId = createSeries(accessToken, "size page 이후 시리즈", "본문")

            // 기본 정렬은 id DESC이므로 size=1 첫 페이지에는 방금 만든(가장 큰 id) 시리즈만 담긴다.
            getSeriesListRequest("?size=1&page=0")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.size).isEqualTo(1)
                    assertThat(body.data.number).isEqualTo(0)
                    assertThat(body.data.content).extracting<Long>(SeriesListResponse::id).containsExactly(newerId)
                }

            getSeriesListRequest("?size=1&page=1")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.number).isEqualTo(1)
                    assertThat(body.data.content).extracting<Long>(SeriesListResponse::id).containsExactly(olderId)
                }
        }

        @Test
        @DisplayName("5. soft delete된 시리즈는 목록에서 제외된다")
        fun getSeriesList_excludesSoftDeletedSeries() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 목록에서 빠질 시리즈", "본문")
            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            getSeriesListRequest("")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .doesNotContain(seriesId)
                }
        }

        // FIXME: postCount는 SeriesListResponse 프로젝션 전용 필드라 Series 엔티티에 없다. Spring Data가
        // Pageable의 Sort를 JPQL에 "order by s.postCount ..."로 그대로 덧붙이고, Hibernate가 이를 파싱하다
        // 실패해 GlobalExceptionHandler의 포괄 Exception 핸들러로 떨어진다. 상세: docs/series-e2e-known-issues.md #4
        @Test
        @DisplayName("6. 존재하지 않는 정렬 프로퍼티(postCount)를 쓰면 실제 동작을 그대로 고정한다 (Q2)")
        @Suppress("ForbiddenComment")
        fun getSeriesList_invalidSortProperty_returns500_1() {
            expectResultCode(getSeriesListRequest("?sort=postCount,desc"), HttpStatus.INTERNAL_SERVER_ERROR, "500-1")
        }
    }

    @Nested
    @DisplayName("GET /api/series/search — 시리즈 검색")
    inner class SearchSeries {
        @Test
        @DisplayName("1. 유일 키워드로 검색하면 200-1과 PageResponse 필드를 반환한다")
        fun searchSeries_uniqueKeyword_returns200WithPageResponse() {
            val accessToken = newUserToken()
            val keyword = uniqueSearchKeyword()
            val seriesId = createSeries(accessToken, keyword, "본문")

            searchSeriesRequest("?keyword=$keyword")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.pageNumber).isEqualTo(0)
                    assertThat(body.data.pageSize).isEqualTo(10)
                    assertThat(body.data.totalElements).isEqualTo(1)
                    assertThat(body.data.totalPages).isEqualTo(1)
                    assertThat(body.data.isLast).isTrue()
                    assertThat(body.data.content).extracting<Long>(SeriesListResponse::id).containsExactly(seriesId)
                }
        }

        @Test
        @DisplayName("2. 제목에 키워드가 부분 일치해도 검색된다")
        fun searchSeries_partialMatch_returnsMatchingSeries() {
            val accessToken = newUserToken()
            val keyword = uniqueSearchKeyword()
            val seriesId = createSeries(accessToken, "접두$keyword" + "접미", "본문")

            searchSeriesRequest("?keyword=$keyword")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .containsExactly(seriesId)
                }
        }

        @Test
        @DisplayName("3. 일치하는 결과가 없으면 200-1과 빈 content를 반환한다")
        fun searchSeries_noMatch_returns200WithEmptyContent() {
            searchSeriesRequest("?keyword=${uniqueSearchKeyword()}")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).isEmpty()
                    assertThat(body.data.totalElements).isZero()
                }
        }

        @Test
        @DisplayName("4. keyword가 빈 문자열이면 400-1을 반환한다")
        fun searchSeries_emptyKeyword_returns400_1() {
            expectResultCode(searchSeriesRequest("?keyword="), HttpStatus.BAD_REQUEST, "400-1")
        }

        @Test
        @DisplayName("5. keyword가 공백만이면 400-1을 반환한다")
        fun searchSeries_blankKeyword_returns400_1() {
            // "%20"을 문자열 그대로 넘기면 RestTestClient의 uri(String) 템플릿 인코딩이 '%'까지
            // 다시 인코딩해 서버에는 리터럴 "%20" 세 글자가 도착한다(공백이 아니라 isBlank()==false).
            // 실제 공백 한 칸을 넣어야 uri(String)이 한 번만 인코딩해 서버에 진짜 공백으로 도착한다.
            expectResultCode(searchSeriesRequest("?keyword= "), HttpStatus.BAD_REQUEST, "400-1")
        }

        // FIXME: keyword는 @RequestParam(required=true)이고 기본값이 없다. 파라미터 누락 시
        // MissingServletRequestParameterException이 발생하는데 GlobalExceptionHandler는 이 예외를
        // 명시적으로 잡지 않아 포괄 Exception 핸들러로 떨어져 500-1이 된다. 상세: docs/series-e2e-known-issues.md #4
        @Test
        @DisplayName("6. keyword 파라미터 자체가 없으면 실제 동작을 그대로 고정한다 (D-4, FIXME)")
        @Suppress("ForbiddenComment")
        fun searchSeries_missingKeyword_returns500_1() {
            expectResultCode(searchSeriesRequest(""), HttpStatus.INTERNAL_SERVER_ERROR, "500-1")
        }

        @Test
        @DisplayName("7. soft delete된 시리즈는 검색 결과에서 제외된다")
        fun searchSeries_excludesSoftDeletedSeries() {
            val accessToken = newUserToken()
            val keyword = uniqueSearchKeyword()
            val seriesId = createSeries(accessToken, keyword, "본문")
            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            searchSeriesRequest("?keyword=$keyword")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content).isEmpty()
                }
        }

        @Test
        @DisplayName("8. size=1로 페이징하면 totalPages/isLast가 정확히 계산된다")
        fun searchSeries_pagingWithSizeOne_computesTotalPagesAndIsLast() {
            val accessToken = newUserToken()
            val keyword = uniqueSearchKeyword()
            val olderId = createSeries(accessToken, "$keyword 첫번째", "본문")
            val newerId = createSeries(accessToken, "$keyword 두번째", "본문")

            searchSeriesRequest("?keyword=$keyword&size=1&page=0")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content).extracting<Long>(SeriesListResponse::id).containsExactly(newerId)
                    assertThat(body.data.totalElements).isEqualTo(2)
                    assertThat(body.data.totalPages).isEqualTo(2)
                    assertThat(body.data.isLast).isFalse()
                }

            searchSeriesRequest("?keyword=$keyword&size=1&page=1")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content).extracting<Long>(SeriesListResponse::id).containsExactly(olderId)
                    assertThat(body.data.isLast).isTrue()
                }
        }
    }

    @Nested
    @DisplayName("GET /api/series/users/{userId} — 특정 유저 시리즈 조회")
    inner class GetUserSeriesList {
        @Test
        @DisplayName("1. 비로그인으로 조회해도 200과 해당 유저의 시리즈만 반환한다")
        fun getUserSeriesList_anonymous_returnsOnlyThatUsersSeries() {
            val userA = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val seriesA1 = createSeries(userA.accessToken, "A의 유저별 목록 시리즈1", "본문")
            val seriesA2 = createSeries(userA.accessToken, "A의 유저별 목록 시리즈2", "본문")

            val tokenB = newUserToken()
            createSeries(tokenB, "B의 유저별 목록 시리즈", "본문")

            getUserSeriesListRequest(userA.user.id, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .containsExactlyInAnyOrder(seriesA1, seriesA2)
                }
        }

        @Test
        @DisplayName("2. 시리즈가 없는 유저를 조회하면 200과 빈 content를 반환한다")
        fun getUserSeriesList_userWithNoSeries_returnsEmptyContent() {
            val user = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            getUserSeriesListRequest(user.user.id, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).isEmpty()
                    assertThat(body.data.totalElements).isZero()
                }
        }

        // getUserSeriesList는 시리즈를 user.id로만 필터링하고 유저 존재 여부를 확인하지 않는다.
        // 존재하지 않는 userId도 404가 아니라 빈 목록의 200-1로 응답한다.
        @Test
        @DisplayName("3. 존재하지 않는 userId를 조회하면 실제 동작을 그대로 고정한다 (유저 존재 검사 없음)")
        fun getUserSeriesList_nonExistentUserId_returns200WithEmptyContent() {
            getUserSeriesListRequest(NON_EXISTENT_USER_ID, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content).isEmpty()
                }
        }

        // findByUserIdWithPostCount는 User.deletedAt을 필터링하지 않는다. 탈퇴 후에도
        // 그 유저가 만든 시리즈는 이 API로 그대로 조회된다.
        @Test
        @DisplayName("4. 탈퇴한 유저의 시리즈를 조회하면 실제 동작을 그대로 고정한다")
        fun getUserSeriesList_withdrawnUser_stillReturnsTheirSeries() {
            val user = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val seriesId = createSeries(user.accessToken, "탈퇴 전 만든 시리즈", "본문")

            expectResultCode(deleteAccount(user.accessToken, DEFAULT_PASSWORD), HttpStatus.OK, "200-1")

            getUserSeriesListRequest(user.user.id, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .contains(seriesId)
                }
        }

        @Test
        @DisplayName("5. soft delete된 시리즈는 목록에서 제외된다")
        fun getUserSeriesList_excludesSoftDeletedSeries() {
            val user = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val seriesId = createSeries(user.accessToken, "유저별 목록에서 삭제될 시리즈", "본문")
            deleteSeriesRequest(user.accessToken, seriesId).expectStatus().isOk()

            getUserSeriesListRequest(user.user.id, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .doesNotContain(seriesId)
                }
        }
    }

    @Nested
    @DisplayName("GET /api/series/me — 내 시리즈 조회")
    inner class GetMySeriesList {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun getMySeriesList_unauthenticated_returns401_1() {
            expectEntryPointUnauthorized(client.get().uri("/api/series/me").exchange())
        }

        @Test
        @DisplayName("2. 성공하면 200과 내 시리즈만 포함된 목록을 반환한다")
        fun getMySeriesList_success_returnsOnlyMySeries() {
            val accessToken = newUserToken()
            val seriesId1 = createSeries(accessToken, "내 시리즈 목록1", "본문")
            val seriesId2 = createSeries(accessToken, "내 시리즈 목록2", "본문")

            getMySeriesListRequest(accessToken, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .containsExactlyInAnyOrder(seriesId1, seriesId2)
                }
        }

        @Test
        @DisplayName("3. 타인의 시리즈는 포함되지 않는다")
        fun getMySeriesList_excludesOtherUsersSeries() {
            val otherToken = newUserToken()
            val otherSeriesId = createSeries(otherToken, "타인의 시리즈", "본문")

            val myToken = newUserToken()

            getMySeriesListRequest(myToken, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .doesNotContain(otherSeriesId)
                }
        }

        @Test
        @DisplayName("4. 시리즈가 0개면 200과 빈 content를 반환한다")
        fun getMySeriesList_noSeries_returnsEmptyContent() {
            val accessToken = newUserToken()

            getMySeriesListRequest(accessToken, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content).isEmpty()
                    assertThat(body.data.totalElements).isZero()
                }
        }

        @Test
        @DisplayName("5. soft delete된 시리즈는 목록에서 제외된다")
        fun getMySeriesList_excludesSoftDeletedSeries() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "내 목록에서 삭제될 시리즈", "본문")
            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            getMySeriesListRequest(accessToken, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .doesNotContain(seriesId)
                }
        }
    }

    @Nested
    @DisplayName("POST /api/series/{id}/medias — 시리즈 썸네일 생성")
    inner class UploadMedia {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun uploadMedia_unauthenticated_returns401_1() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "미인증 썸네일 대상", "본문")

            val response =
                client
                    .post()
                    .uri("/api/series/$seriesId/medias")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(mediaFilePart(PNG_BYTES, "thumbnail.png", MediaType.IMAGE_PNG))
                    .exchange()

            expectEntryPointUnauthorized(response)
        }

        @Test
        @DisplayName("2. 성공하면 201과 SeriesMediaResponse를 반환하고, GET /{id}/medias로 다시 조회된다")
        fun uploadMedia_success_returns201AndIsRetrievable() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "썸네일 생성 대상", "본문")

            var uploadedUrl: String? = null
            uploadMediaRequest(accessToken, seriesId, mediaFilePart(PNG_BYTES, "thumbnail.png", MediaType.IMAGE_PNG))
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("201-1")
                    assertThat(body.data.id).isNotNull()
                    assertThat(body.data.seriesId).isEqualTo(seriesId)
                    assertThat(body.data.url).isNotBlank()
                    assertThat(body.data.mediaType).isEqualTo(DomainMediaType.IMAGE)
                    uploadedUrl = body.data.url
                }

            // 응답만 그럴듯한 게 아니라 실제로 디스크에 저장되었는지 확인한다.
            assertThat(Files.exists(uploadedFilePath(requireNotNull(uploadedUrl)))).isTrue()

            getMediaRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).isNotNull()
                    assertThat(body.data.url).isEqualTo(uploadedUrl)
                }
        }

        @Test
        @DisplayName("3. 재업로드하면 SeriesMedia id는 유지된 채 url만 바뀌고, 구 파일은 삭제된다")
        fun uploadMedia_reupload_replacesMediaAndDeletesOldFile() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "썸네일 교체 대상", "본문")

            val first = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "first.png")
            val firstFilePath = uploadedFilePath(requireNotNull(first.url))
            assertThat(Files.exists(firstFilePath)).isTrue()

            uploadMediaRequest(accessToken, seriesId, mediaFilePart(PNG_BYTES, "second.png", MediaType.IMAGE_PNG))
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("201-1")
                    assertThat(body.data.id).isEqualTo(first.id)
                    assertThat(body.data.url).isNotEqualTo(first.url)
                }

            assertThat(Files.exists(firstFilePath)).isFalse()
        }

        @Test
        @DisplayName("4. 타인의 시리즈에 업로드하면 403-1을 반환한다")
        fun uploadMedia_otherUsersSeries_returns403_1() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "남의 시리즈 썸네일 대상", "본문")

            val otherToken = newUserToken()

            expectResultCode(
                uploadMediaRequest(
                    otherToken,
                    seriesId,
                    mediaFilePart(PNG_BYTES, "thumbnail.png", MediaType.IMAGE_PNG),
                ),
                HttpStatus.FORBIDDEN,
                "403-1",
            )
        }

        @Test
        @DisplayName("5. 존재하지 않는 시리즈면 404-5를 반환한다")
        fun uploadMedia_nonExistentSeries_returns404_5() {
            val accessToken = newUserToken()

            expectResultCode(
                uploadMediaRequest(
                    accessToken,
                    NON_EXISTENT_SERIES_ID,
                    mediaFilePart(PNG_BYTES, "thumbnail.png", MediaType.IMAGE_PNG),
                ),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("6. 삭제된 시리즈면 404-5를 반환한다")
        fun uploadMedia_softDeletedSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 업로드할 시리즈", "본문")
            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(
                uploadMediaRequest(
                    accessToken,
                    seriesId,
                    mediaFilePart(PNG_BYTES, "thumbnail.png", MediaType.IMAGE_PNG),
                ),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("7. 빈 파일이면 400-4를 반환한다")
        fun uploadMedia_emptyFile_returns400_4() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "빈 파일 업로드 대상", "본문")

            expectResultCode(
                uploadMediaRequest(
                    accessToken,
                    seriesId,
                    mediaFilePart(ByteArray(0), "empty.png", MediaType.IMAGE_PNG),
                ),
                HttpStatus.BAD_REQUEST,
                "400-4",
            )
        }

        @Test
        @DisplayName("8. text/plain 파일이면 415-1을 반환한다")
        fun uploadMedia_unsupportedFileType_returns415_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "지원 안 하는 형식 업로드 대상", "본문")

            expectResultCode(
                uploadMediaRequest(accessToken, seriesId, mediaFilePart(PNG_BYTES, "file.txt", MediaType.TEXT_PLAIN)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "415-1",
            )
        }

        @Test
        @DisplayName("9. part에 Content-Type이 없으면 415-1을 반환한다")
        fun uploadMedia_missingPartContentType_returns415_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "Content-Type 없는 파일 업로드 대상", "본문")

            // 파일명에 확장자가 있으면(예: "thumbnail.png") 클라이언트의 FormHttpMessageConverter가
            // Content-Type 헤더를 명시하지 않아도 파일명으로부터 MediaType을 추론해 붙여 버려
            // "Content-Type 없음"이 재현되지 않는다(실행해서 확인 — 이 경우 서버는 201을 응답한다).
            // 확장자가 없는 파일명을 써야 클라이언트가 추론에 실패해 Content-Type 헤더 자체가 빠지고,
            // 그 결과 getContentType()이 null이 되어 LocalMediaService.getMediaType(null)이
            // UNSUPPORTED_FILE_TYPE(415-1)을 던진다.
            expectResultCode(
                uploadMediaRequest(accessToken, seriesId, mediaFilePart(PNG_BYTES, "thumbnail_no_ext", null)),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "415-1",
            )
        }

        // FIXME: file은 @RequestPart(필수)라 파트 자체가 없으면 MissingServletRequestPartException이
        // 발생하는데, GlobalExceptionHandler가 이 예외를 명시적으로 잡지 않아 포괄 Exception 핸들러로
        // 떨어져 500-1이 된다. user 도메인의 PATCH /api/users/me request part 누락과 동일한 구조다(D-7,
        // 신규 등재 안 함). 상세: docs/user-e2e-known-issues.md #3
        @Test
        @DisplayName("10. file part 자체가 없으면 실제 동작을 그대로 고정한다")
        @Suppress("ForbiddenComment")
        fun uploadMedia_missingFilePart_returns500_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "file part 누락 대상", "본문")

            expectResultCode(
                uploadMediaRequest(accessToken, seriesId, LinkedMultiValueMap()),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "500-1",
            )
        }
    }

    @Nested
    @DisplayName("GET /api/series/{id}/medias — 시리즈 썸네일 조회")
    inner class GetMedia {
        @Test
        @DisplayName("1. 비로그인으로 조회해도 200과 썸네일 정보를 반환한다")
        fun getMedia_anonymous_returns200WithMedia() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "비로그인 썸네일 조회 대상", "본문")
            val uploaded = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "thumbnail.png")

            getMediaRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(uploaded.id)
                    assertThat(body.data.seriesId).isEqualTo(seriesId)
                    assertThat(body.data.url).isEqualTo(uploaded.url)
                    assertThat(body.data.mediaType).isEqualTo(DomainMediaType.IMAGE)
                }
        }

        @Test
        @DisplayName("2. 타인 시리즈의 썸네일도 200을 반환한다 (조회에 소유자 제한이 없다)")
        fun getMedia_otherUsersSeries_returns200() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "타인이 조회할 썸네일 대상", "본문")
            uploadSeriesMedia(ownerToken, seriesId, PNG_BYTES, "thumbnail.png")

            val otherToken = newUserToken()

            client
                .get()
                .uri("/api/series/$seriesId/medias")
                .header("Authorization", bearer(otherToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isNotNull()
                }
        }

        // FIXME: SeriesMediaService.getMedia()는 orElse(null)로 끝내고 컨트롤러가 그 null을 그대로
        // 200-1로 감싼다. 반면 같은 상황에서 DELETE /{id}/medias는 404-7(MEDIA_NOT_FOUND)을 던진다 —
        // 한 리소스의 "없음"이 메서드에 따라 200과 404로 갈린다. 상세: docs/series-e2e-known-issues.md #3
        @Test
        @DisplayName("3. 썸네일이 없으면 실제 동작을 그대로 고정한다 (200 + data:null)")
        @Suppress("ForbiddenComment")
        fun getMedia_withoutMedia_returns200WithNullData() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "썸네일 없는 시리즈", "본문")

            getMediaRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isNull()
                }
        }

        @Test
        @DisplayName("4. 존재하지 않는 시리즈면 404-5를 반환한다")
        fun getMedia_nonExistentSeries_returns404_5() {
            expectResultCode(getMediaRequest(NON_EXISTENT_SERIES_ID), HttpStatus.NOT_FOUND, "404-5")
        }

        @Test
        @DisplayName("5. 삭제된 시리즈면 404-5를 반환한다")
        fun getMedia_softDeletedSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 썸네일 조회할 시리즈", "본문")
            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(getMediaRequest(seriesId), HttpStatus.NOT_FOUND, "404-5")
        }
    }

    @Nested
    @DisplayName("DELETE /api/series/{id}/medias — 시리즈 썸네일 삭제")
    inner class DeleteMedia {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun deleteMedia_unauthenticated_returns401_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "미인증 썸네일 삭제 대상", "본문")
            uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "thumbnail.png")

            expectEntryPointUnauthorized(
                client.delete().uri("/api/series/$seriesId/medias").exchange(),
            )
        }

        @Test
        @DisplayName("2. 소유자가 삭제하면 200을 반환하고, GET은 data:null이 되며 파일도 삭제된다")
        fun deleteMedia_owner_returns200_andMediaAndFileAreGone() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "썸네일 삭제 대상", "본문")
            val uploaded = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "thumbnail.png")
            val filePath = uploadedFilePath(requireNotNull(uploaded.url))
            assertThat(Files.exists(filePath)).isTrue()

            expectResultCode(deleteMediaRequest(accessToken, seriesId), HttpStatus.OK, "200-1")

            getMediaRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).isNull()
                }
            assertThat(Files.exists(filePath)).isFalse()
        }

        @Test
        @DisplayName("3. 타인의 시리즈 썸네일을 삭제하면 403-1을 반환한다")
        fun deleteMedia_otherUsersSeries_returns403_1() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "남의 썸네일 삭제 대상", "본문")
            uploadSeriesMedia(ownerToken, seriesId, PNG_BYTES, "thumbnail.png")

            val otherToken = newUserToken()

            expectResultCode(deleteMediaRequest(otherToken, seriesId), HttpStatus.FORBIDDEN, "403-1")
        }

        @Test
        @DisplayName("4. 썸네일이 없으면 404-7을 반환한다")
        fun deleteMedia_withoutMedia_returns404_7() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "썸네일 없이 삭제 시도할 시리즈", "본문")

            expectResultCode(deleteMediaRequest(accessToken, seriesId), HttpStatus.NOT_FOUND, "404-7")
        }

        @Test
        @DisplayName("5. 존재하지 않는 시리즈면 404-5를 반환한다")
        fun deleteMedia_nonExistentSeries_returns404_5() {
            val accessToken = newUserToken()

            expectResultCode(
                deleteMediaRequest(accessToken, NON_EXISTENT_SERIES_ID),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("6. 삭제된 시리즈면 404-5를 반환한다")
        fun deleteMedia_softDeletedSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 썸네일 삭제 시도할 시리즈", "본문")
            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(deleteMediaRequest(accessToken, seriesId), HttpStatus.NOT_FOUND, "404-5")
        }

        @Test
        @DisplayName("7. 이미 삭제한 썸네일을 다시 삭제하면 404-7을 반환한다")
        fun deleteMedia_alreadyDeleted_returns404_7() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "두 번 삭제할 썸네일 대상", "본문")
            uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "thumbnail.png")

            expectResultCode(deleteMediaRequest(accessToken, seriesId), HttpStatus.OK, "200-1")
            expectResultCode(deleteMediaRequest(accessToken, seriesId), HttpStatus.NOT_FOUND, "404-7")
        }
    }

    @Nested
    @DisplayName("POST /api/series/{id}/posts/{postId} — 시리즈에 포스트 추가")
    inner class AddPostToSeries {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun addPostToSeries_unauthenticated_returns401_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "미인증 포스트 추가 대상 시리즈", "본문")
            val postId = createPost(accessToken, "미인증 포스트 추가 대상 포스트")

            expectEntryPointUnauthorized(
                client.post().uri("/api/series/$seriesId/posts/$postId").exchange(),
            )

            // 실패한 요청이 포스트를 편입시키지 않았는지 확인한다.
            assertThat(seriesPostIds(seriesId)).isEmpty()
        }

        @Test
        @DisplayName("2. 소유자가 자기 포스트를 추가하면 200-1이고 GET /{id}/posts에 나타난다")
        fun addPostToSeries_success_returns200_1AndPostAppearsInSeries() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "포스트 추가 대상 시리즈", "본문")
            val postId = createPost(accessToken, "추가될 포스트")

            assertThat(seriesPostIds(seriesId)).isEmpty()

            expectResultCode(addPostToSeriesRequest(accessToken, seriesId, postId), HttpStatus.OK, "200-1")

            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).hasSize(1)
                    assertThat(body.data[0].id).isEqualTo(postId)
                    assertThat(body.data[0].seriesId).isEqualTo(seriesId)
                }
        }

        @Test
        @DisplayName("3. 같은 포스트를 두 번 추가해도 200-1이고 목록에 한 번만 담긴다 (멱등)")
        fun addPostToSeries_duplicate_isIdempotent() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "중복 추가 대상 시리즈", "본문")
            val postId = createPost(accessToken, "중복 추가될 포스트")

            expectResultCode(addPostToSeriesRequest(accessToken, seriesId, postId), HttpStatus.OK, "200-1")
            assertThat(seriesPostIds(seriesId)).containsExactly(postId)

            // Post.series를 덮어쓰기만 하므로 같은 값을 다시 써도 아무 일도 일어나지 않는다.
            expectResultCode(addPostToSeriesRequest(accessToken, seriesId, postId), HttpStatus.OK, "200-1")
            assertThat(seriesPostIds(seriesId)).containsExactly(postId)
        }

        @Test
        @DisplayName("4. 다른 시리즈에 속한 포스트를 추가하면 이전 시리즈에서 조용히 빠져나온다")
        fun addPostToSeries_movesPostFromPreviousSeries() {
            val accessToken = newUserToken()
            val oldSeriesId = createSeries(accessToken, "포스트를 잃을 시리즈", "본문")
            val newSeriesId = createSeries(accessToken, "포스트를 얻을 시리즈", "본문")
            val postId = createPostInSeries(accessToken, oldSeriesId, "이사 갈 포스트")

            assertThat(seriesPostIds(oldSeriesId)).containsExactly(postId)
            assertThat(seriesPostIds(newSeriesId)).isEmpty()

            // Post.series는 단일 참조라 "추가"가 곧 "이동"이다. 이전 시리즈 소유자에게 알리는 것도,
            // 확인을 요구하는 것도 없다(B-2 #7).
            expectResultCode(addPostToSeriesRequest(accessToken, newSeriesId, postId), HttpStatus.OK, "200-1")

            assertThat(seriesPostIds(oldSeriesId)).isEmpty()
            assertThat(seriesPostIds(newSeriesId)).containsExactly(postId)
        }

        @Test
        @DisplayName("5. 타인의 포스트를 내 시리즈에 추가하면 403-1을 반환한다")
        fun addPostToSeries_otherUsersPost_returns403_1() {
            val myToken = newUserToken()
            val seriesId = createSeries(myToken, "내 시리즈", "본문")

            val otherToken = newUserToken()
            val otherPostId = createPost(otherToken, "타인의 포스트")

            expectResultCode(
                addPostToSeriesRequest(myToken, seriesId, otherPostId),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            assertThat(seriesPostIds(seriesId)).isEmpty()
        }

        @Test
        @DisplayName("6. 내 포스트를 타인의 시리즈에 추가하면 403-1을 반환한다")
        fun addPostToSeries_otherUsersSeries_returns403_1() {
            val ownerToken = newUserToken()
            val otherSeriesId = createSeries(ownerToken, "타인의 시리즈", "본문")

            val myToken = newUserToken()
            val myPostId = createPost(myToken, "내 포스트")

            // 포스트 소유자 검사(통과) 다음에 시리즈 소유자 검사에서 걸린다.
            expectResultCode(
                addPostToSeriesRequest(myToken, otherSeriesId, myPostId),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            assertThat(seriesPostIds(otherSeriesId)).isEmpty()
        }

        @Test
        @DisplayName("7. 존재하지 않는 postId면 404-3을 반환한다")
        fun addPostToSeries_nonExistentPostId_returns404_3() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "없는 포스트를 추가할 시리즈", "본문")

            expectResultCode(
                addPostToSeriesRequest(accessToken, seriesId, NON_EXISTENT_POST_ID),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("8. 존재하지 않는 seriesId면 404-5를 반환한다")
        fun addPostToSeries_nonExistentSeriesId_returns404_5() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "없는 시리즈에 추가될 포스트")

            // 포스트 조회·소유자 검사를 먼저 통과해야 시리즈 조회까지 도달한다.
            expectResultCode(
                addPostToSeriesRequest(accessToken, NON_EXISTENT_SERIES_ID, postId),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("9. soft delete된 포스트면 404-3을 반환한다")
        fun addPostToSeries_softDeletedPost_returns404_3() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제된 포스트를 추가할 시리즈", "본문")
            val postId = createPost(accessToken, "삭제될 포스트")

            expectResultCode(deletePostRequest(accessToken, postId), HttpStatus.OK, "200-1")

            expectResultCode(
                addPostToSeriesRequest(accessToken, seriesId, postId),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("10. soft delete된 시리즈면 404-5를 반환한다")
        fun addPostToSeries_softDeletedSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 포스트를 추가할 시리즈", "본문")
            val postId = createPost(accessToken, "삭제된 시리즈에 추가될 포스트")

            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(
                addPostToSeriesRequest(accessToken, seriesId, postId),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }
    }

    @Nested
    @DisplayName("DELETE /api/series/{id}/posts/{postId} — 시리즈에서 포스트 제거")
    inner class RemovePostFromSeries {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun removePostFromSeries_unauthenticated_returns401_1() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "미인증 포스트 제거 대상 시리즈", "본문")
            val postId = createPostInSeries(accessToken, seriesId, "미인증 제거 대상 포스트")

            expectEntryPointUnauthorized(
                client.delete().uri("/api/series/$seriesId/posts/$postId").exchange(),
            )

            assertThat(seriesPostIds(seriesId)).containsExactly(postId)
        }

        @Test
        @DisplayName("2. 시리즈 소유자가 제거하면 200-1이고 목록에서 빠지되 포스트 자체는 남는다")
        fun removePostFromSeries_owner_returns200_1AndPostSurvives() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "포스트 제거 대상 시리즈", "본문")
            val postId = createPostInSeries(accessToken, seriesId, "제거될 포스트")

            assertThat(seriesPostIds(seriesId)).containsExactly(postId)

            expectResultCode(removePostFromSeriesRequest(accessToken, seriesId, postId), HttpStatus.OK, "200-1")

            assertThat(seriesPostIds(seriesId)).isEmpty()

            // "제거"는 포스트 삭제가 아니라 series_id를 null로 만드는 것이다(B-2 #8).
            getPostRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(postId)
                    assertThat(body.data.seriesId as Long?).isNull()
                }
        }

        @Test
        @DisplayName("3. 어느 시리즈에도 속하지 않은 포스트를 제거하면 404-5를 반환한다")
        fun removePostFromSeries_postWithoutSeries_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "관계 없는 포스트를 제거할 시리즈", "본문")
            val postId = createPost(accessToken, "어느 시리즈에도 없는 포스트")

            // 포스트는 존재하지만 post.getSeries()가 null이라 SERIES_NOT_FOUND가 된다(404-3이 아니다).
            expectResultCode(
                removePostFromSeriesRequest(accessToken, seriesId, postId),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }

        @Test
        @DisplayName("4. 포스트가 속한 시리즈와 다른 seriesId를 넘기면 404-5를 반환한다")
        fun removePostFromSeries_mismatchedSeriesId_returns404_5() {
            val accessToken = newUserToken()
            val realSeriesId = createSeries(accessToken, "포스트가 실제로 속한 시리즈", "본문")
            val otherSeriesId = createSeries(accessToken, "엉뚱하게 넘길 시리즈", "본문")
            val postId = createPostInSeries(accessToken, realSeriesId, "시리즈 불일치 확인용 포스트")

            expectResultCode(
                removePostFromSeriesRequest(accessToken, otherSeriesId, postId),
                HttpStatus.NOT_FOUND,
                "404-5",
            )

            // 실패한 요청이 원래 시리즈의 관계를 건드리지 않았는지 확인한다.
            assertThat(seriesPostIds(realSeriesId)).containsExactly(postId)
        }

        @Test
        @DisplayName("5. 타인의 시리즈에서 포스트를 제거하면 403-1을 반환한다")
        fun removePostFromSeries_otherUsersSeries_returns403_1() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "타인의 포스트 제거 대상 시리즈", "본문")
            val postId = createPostInSeries(ownerToken, seriesId, "타인 시리즈의 포스트")

            val otherToken = newUserToken()

            // removePostFromSeries는 시리즈 소유자만 검사한다(포스트 소유자는 보지 않는다 — 계획서 D-8,
            // Post E2E 이월 항목). 여기서는 시리즈 소유자 검사가 걸리는 경로만 고정한다.
            expectResultCode(
                removePostFromSeriesRequest(otherToken, seriesId, postId),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            assertThat(seriesPostIds(seriesId)).containsExactly(postId)
        }

        @Test
        @DisplayName("6. 존재하지 않는 postId면 404-3을 반환한다")
        fun removePostFromSeries_nonExistentPostId_returns404_3() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "없는 포스트를 제거할 시리즈", "본문")

            expectResultCode(
                removePostFromSeriesRequest(accessToken, seriesId, NON_EXISTENT_POST_ID),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("7. soft delete된 포스트면 404-3을 반환한다")
        fun removePostFromSeries_softDeletedPost_returns404_3() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제된 포스트를 제거할 시리즈", "본문")
            val postId = createPostInSeries(accessToken, seriesId, "삭제 후 제거할 포스트")

            expectResultCode(deletePostRequest(accessToken, postId), HttpStatus.OK, "200-1")

            expectResultCode(
                removePostFromSeriesRequest(accessToken, seriesId, postId),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("8. 이미 제거한 포스트를 다시 제거하면 404-5를 반환한다")
        fun removePostFromSeries_alreadyRemoved_returns404_5() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "두 번 제거할 시리즈", "본문")
            val postId = createPostInSeries(accessToken, seriesId, "두 번 제거될 포스트")

            expectResultCode(removePostFromSeriesRequest(accessToken, seriesId, postId), HttpStatus.OK, "200-1")

            // 두 번째 호출에서는 post.getSeries()가 이미 null이라 3번 케이스와 같은 경로를 탄다.
            expectResultCode(
                removePostFromSeriesRequest(accessToken, seriesId, postId),
                HttpStatus.NOT_FOUND,
                "404-5",
            )
        }
    }

    @Nested
    @DisplayName("GET /api/series/{id}/posts — 시리즈 내 게시글 목록 조회")
    inner class GetSeriesPosts {
        @Test
        @DisplayName("1. 비로그인으로 조회해도 200-1과 시리즈의 모든 포스트 id가 반환된다 (순서 미단언)")
        fun getSeriesPosts_anonymous_returns200WithAllPostIds() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "포스트 목록 조회 대상 시리즈", "본문")
            val firstPostId = createPostInSeries(accessToken, seriesId, "목록 조회 포스트1")
            val secondPostId = createPostInSeries(accessToken, seriesId, "목록 조회 포스트2")

            // 리포지토리에 Sort/@OrderBy가 없어 순서 보장이 없다(Q3). 집합으로만 비교한다.
            // 상세: docs/series-e2e-known-issues.md #6
            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data)
                        .extracting<Long>(PostListResponse::id)
                        .containsExactlyInAnyOrder(firstPostId, secondPostId)
                }
        }

        @Test
        @DisplayName("2. 비로그인·비소유자 응답의 JSON 키가 B-4의 13개와 정확히 일치한다")
        fun getSeriesPosts_anonymous_exposesExactlyThirteenKeys() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "노출 키 확인용 시리즈", "본문")
            createPostInSeries(ownerToken, seriesId, "노출 키 확인용 포스트")

            // DTO로 역직렬화하면 "응답에는 있는데 DTO에 없는 키"를 볼 수 없다. 원문 JSON의 키 집합을
            // 그대로 비교해서 초과 노출이 생기면 이 테스트가 먼저 깨지게 한다(계획서 B-4, C-3).
            // Jackson 3(tools.jackson)에서 계획서가 적은 fieldNames()는 propertyNames()로 이름이 바뀌었다.
            val post = firstSeriesPostNode(getSeriesPostsRequest(seriesId))

            assertThat(post.propertyNames())
                .containsExactlyInAnyOrderElementsOf(POST_LIST_RESPONSE_KEYS)
        }

        @Test
        @DisplayName("3. 비로그인·비소유자 응답에 body 등 비노출 필드가 존재하지 않는다")
        fun getSeriesPosts_anonymous_omitsUnexposedFields() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "비노출 필드 확인용 시리즈", "본문")
            createPostInSeries(ownerToken, seriesId, "비노출 필드 확인용 포스트")

            val post = firstSeriesPostNode(getSeriesPostsRequest(seriesId))

            // 목록 DTO에는 본문이 아예 없다 — 상세 조회(PostResponse)에서만 나가고, 거기서 PRIVATE/PAID
            // 보호가 이뤄진다(계획서 B-4 대조군).
            assertThat(post.has("body")).isFalse()
            assertThat(post.has("updatedAt")).isFalse()
            assertThat(post.has("deletedAt")).isFalse()
            assertThat(post.has("medias")).isFalse()
            assertThat(post.has("isLocked")).isFalse()
            // User 엔티티에서는 userId/nickname만 뽑아 쓴다.
            assertThat(post.has("email")).isFalse()
            assertThat(post.has("password")).isFalse()
            assertThat(post.has("introduction")).isFalse()
            assertThat(post.has("role")).isFalse()
            assertThat(post.has("refreshToken")).isFalse()
        }

        @Test
        @DisplayName("4. 포스트가 없는 시리즈면 200-1과 빈 배열을 반환한다")
        fun getSeriesPosts_emptySeries_returns200WithEmptyList() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "포스트 없는 시리즈", "본문")

            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isEmpty()
                }
        }

        @Test
        @DisplayName("5. soft delete된 포스트는 목록에서 제외된다")
        fun getSeriesPosts_excludesSoftDeletedPost() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "포스트가 삭제될 시리즈", "본문")
            val survivingPostId = createPostInSeries(accessToken, seriesId, "살아남을 포스트")
            val deletedPostId = createPostInSeries(accessToken, seriesId, "삭제될 포스트")

            assertThat(seriesPostIds(seriesId)).containsExactlyInAnyOrder(survivingPostId, deletedPostId)

            expectResultCode(deletePostRequest(accessToken, deletedPostId), HttpStatus.OK, "200-1")

            assertThat(seriesPostIds(seriesId)).containsExactly(survivingPostId)
        }

        @Test
        @DisplayName("6. PRIVATE 포스트도 비로그인에게 노출되고 publishStatus가 PRIVATE로 나간다 (정상 사양)")
        fun getSeriesPosts_exposesPrivatePostToAnonymous() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "PRIVATE 포스트 시리즈", "본문")
            val postId =
                createPost(
                    ownerToken,
                    "PRIVATE 포스트",
                    "숨겨야 할 본문",
                    PublishStatus.PRIVATE,
                    PostAccessLevel.FREE,
                )
            expectResultCode(addPostToSeriesRequest(ownerToken, seriesId, postId), HttpStatus.OK, "200-1")

            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).hasSize(1)
                    assertThat(body.data[0].id).isEqualTo(postId)
                    assertThat(body.data[0].title).isEqualTo("PRIVATE 포스트")
                    assertThat(body.data[0].publishStatus).isEqualTo(PublishStatus.PRIVATE)
                }

            // 대조군: 본문 보호는 상세 조회가 담당한다. 목록은 애초에 body를 싣지 않아서 안전하다(B-4).
            expectResultCode(getPostRequest(postId), HttpStatus.FORBIDDEN, "403-1")
        }

        @Test
        @DisplayName("7. DRAFT 포스트도 비로그인에게 노출되고 publishStatus가 DRAFT로 나간다 (정상 사양)")
        fun getSeriesPosts_exposesDraftPostToAnonymous() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "DRAFT 포스트 시리즈", "본문")
            val postId =
                createPost(
                    ownerToken,
                    "DRAFT 포스트",
                    "작성 중인 본문",
                    PublishStatus.DRAFT,
                    PostAccessLevel.FREE,
                )
            expectResultCode(addPostToSeriesRequest(ownerToken, seriesId, postId), HttpStatus.OK, "200-1")

            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).hasSize(1)
                    assertThat(body.data[0].id).isEqualTo(postId)
                    assertThat(body.data[0].publishStatus).isEqualTo(PublishStatus.DRAFT)
                }
        }

        @Test
        @DisplayName("8. PAID 포스트도 노출되고 accessLevel이 PAID로 나가며 body는 여전히 없다 (정상 사양)")
        fun getSeriesPosts_exposesPaidPostWithoutBody() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "PAID 포스트 시리즈", "본문")
            val postId =
                createPost(
                    ownerToken,
                    "PAID 포스트",
                    "유료 본문",
                    PublishStatus.PUBLIC,
                    PostAccessLevel.PAID,
                )
            expectResultCode(addPostToSeriesRequest(ownerToken, seriesId, postId), HttpStatus.OK, "200-1")

            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).hasSize(1)
                    assertThat(body.data[0].id).isEqualTo(postId)
                    assertThat(body.data[0].accessLevel).isEqualTo(PostAccessLevel.PAID)
                }

            val post = firstSeriesPostNode(getSeriesPostsRequest(seriesId))
            assertThat(post.has("body")).isFalse()
            assertThat(post.get("accessLevel").asString()).isEqualTo("PAID")
        }

        // FIXME: PostService.getPostsBySeriesId()는 seriesRepository를 아예 참조하지 않고
        // postRepository.findBySeriesIdAndDeletedAtIsNull(seriesId)만 호출한다. 그래서 같은 id에 대해
        // GET /api/series/{id}는 404-5인데 /posts는 200 + 빈 배열이 되고, 클라이언트는 "글이 없는
        // 시리즈"와 "없는 시리즈"를 구분할 수 없다. 상세: docs/series-e2e-known-issues.md #1
        @Test
        @DisplayName("9. 존재하지 않는 시리즈여도 실제 동작을 그대로 고정한다 (200 + 빈 배열)")
        @Suppress("ForbiddenComment")
        fun getSeriesPosts_nonExistentSeries_returns200WithEmptyList() {
            // 같은 id에 대해 상세 조회는 404-5라는 점을 나란히 고정한다.
            expectResultCode(getSeries(NON_EXISTENT_SERIES_ID), HttpStatus.NOT_FOUND, "404-5")

            getSeriesPostsRequest(NON_EXISTENT_SERIES_ID)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isEmpty()
                }
        }

        // FIXME: 9번과 같은 원인이다. 시리즈를 삭제하면 그 시리즈의 살아있는 포스트는 series_id가
        // null로 떨어져 나가므로(B-3) 목록은 비어 보이지만, 시리즈가 없다는 사실 자체는 알려 주지 않는다.
        // 상세: docs/series-e2e-known-issues.md #1
        @Test
        @DisplayName("10. soft delete된 시리즈여도 실제 동작을 그대로 고정한다 (200 + 빈 배열)")
        @Suppress("ForbiddenComment")
        fun getSeriesPosts_softDeletedSeries_returns200WithEmptyList() {
            val accessToken = newUserToken()
            val seriesId = createSeries(accessToken, "삭제 후 포스트 목록을 조회할 시리즈", "본문")
            val postId = createPostInSeries(accessToken, seriesId, "시리즈와 함께 떨어져 나올 포스트")

            assertThat(seriesPostIds(seriesId)).containsExactly(postId)

            deleteSeriesRequest(accessToken, seriesId).expectStatus().isOk()

            expectResultCode(getSeries(seriesId), HttpStatus.NOT_FOUND, "404-5")

            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).isEmpty()
                }
        }

        @Test
        @DisplayName("11. 비로그인이면 좋아요·북마크된 포스트여도 isLiked/isBookmarked가 false다")
        fun getSeriesPosts_anonymous_isLikedAndIsBookmarkedAreAlwaysFalse() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "좋아요·북마크 확인용 시리즈(비로그인)", "본문")
            val postId = createPostInSeries(ownerToken, seriesId, "좋아요·북마크될 포스트")

            likePost(ownerToken, postId)
            bookmarkPost(ownerToken, postId)

            // actor == null 이면 isLiked/isBookmarked는 조회조차 하지 않고 항상 false다(B-4).
            // 반면 집계 카운트는 그대로 노출된다.
            getSeriesPostsRequest(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data).hasSize(1)
                    assertThat(body.data[0].isLiked).isFalse()
                    assertThat(body.data[0].isBookmarked).isFalse()
                    assertThat(body.data[0].likeCount).isEqualTo(1L)
                    assertThat(body.data[0].bookmarkCount).isEqualTo(1L)
                }
        }

        @Test
        @DisplayName("12. 로그인 상태로 좋아요·북마크한 포스트를 조회하면 isLiked/isBookmarked가 true다")
        fun getSeriesPosts_authenticatedWithLikeAndBookmark_returnsTrue() {
            val ownerToken = newUserToken()
            val seriesId = createSeries(ownerToken, "좋아요·북마크 확인용 시리즈(로그인)", "본문")
            val postId = createPostInSeries(ownerToken, seriesId, "내가 좋아요·북마크할 포스트")

            // 좋아요/북마크 전에는 로그인해도 false다.
            getSeriesPostsRequest(seriesId, ownerToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data[0].isLiked).isFalse()
                    assertThat(body.data[0].isBookmarked).isFalse()
                }

            likePost(ownerToken, postId)
            bookmarkPost(ownerToken, postId)

            getSeriesPostsRequest(seriesId, ownerToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data).hasSize(1)
                    assertThat(body.data[0].id).isEqualTo(postId)
                    assertThat(body.data[0].isLiked).isTrue()
                    assertThat(body.data[0].isBookmarked).isTrue()
                }

            // 같은 포스트를 다른 유저가 보면 false다 — 개인화 값이 요청자 기준인지 확인한다.
            val otherToken = newUserToken()
            getSeriesPostsRequest(seriesId, otherToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data[0].isLiked).isFalse()
                    assertThat(body.data[0].isBookmarked).isFalse()
                }
        }
    }

    @Nested
    @DisplayName("Scenarios — 여러 API를 잇는 흐름")
    inner class Scenarios {
        @Test
        @DisplayName("1. 생성 → 썸네일 → 포스트 2개 추가가 GET /users/{userId}의 postCount·thumbnailUrl에 반영된다")
        fun scenario_createSeries_uploadThumbnail_addPosts_reflectedInUserSeriesList() {
            val user = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = user.accessToken
            val userId = user.user.id

            val seriesId = createSeries(accessToken, "시나리오1 시리즈", "본문")

            val justCreated = userSeriesListEntry(userId, seriesId)
            assertThat(justCreated.postCount).isZero()
            assertThat(justCreated.thumbnailUrl).isNull()

            val thumbnail = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "scenario1.png")

            val withThumbnail = userSeriesListEntry(userId, seriesId)
            assertThat(withThumbnail.thumbnailUrl).isEqualTo(thumbnail.url)
            assertThat(withThumbnail.postCount).isZero()

            val firstPostId = createPostInSeries(accessToken, seriesId, "시나리오1 포스트1")

            val withOnePost = userSeriesListEntry(userId, seriesId)
            assertThat(withOnePost.postCount).isEqualTo(1L)

            val secondPostId = createPostInSeries(accessToken, seriesId, "시나리오1 포스트2")

            val withTwoPosts = userSeriesListEntry(userId, seriesId)
            assertThat(withTwoPosts.postCount).isEqualTo(2L)
            assertThat(withTwoPosts.thumbnailUrl).isEqualTo(thumbnail.url)
            assertThat(seriesPostIds(seriesId)).containsExactlyInAnyOrder(firstPostId, secondPostId)
        }

        @Test
        @DisplayName("2. 시리즈를 삭제하면 포스트는 살아남고 seriesId만 null이 된다")
        fun scenario_deleteSeries_detachesPostsButKeepsThem() {
            val user = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = user.accessToken

            val seriesId = createSeries(accessToken, "시나리오2 시리즈", "본문")
            val postId = createPostInSeries(accessToken, seriesId, "시나리오2 포스트")

            getPostRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.seriesId).isEqualTo(seriesId)
                }
            assertThat(seriesPostIds(seriesId)).containsExactly(postId)

            expectResultCode(deleteSeriesRequest(accessToken, seriesId), HttpStatus.OK, "200-1")

            expectResultCode(getSeries(seriesId), HttpStatus.NOT_FOUND, "404-5")
            getUserSeriesListRequest(user.user.id, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .doesNotContain(seriesId)
                }

            // 포스트는 삭제되지 않고 시리즈에서 떨어져 나온다(B-3).
            getPostRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.data.id).isEqualTo(postId)
                    assertThat(body.data.seriesId as Long?).isNull()
                }
        }

        @Test
        @DisplayName("3. 두 유저는 서로의 시리즈를 보지도 고치지도 못한다")
        fun scenario_twoUsersAreIsolated() {
            val userA = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val userB = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            val seriesOfA = createSeries(userA.accessToken, "시나리오3 A의 시리즈", "A의 본문")

            // A의 목록에는 있고 B의 목록에는 없다.
            getMySeriesListRequest(userA.accessToken, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .contains(seriesOfA)
                }
            getMySeriesListRequest(userB.accessToken, "")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .doesNotContain(seriesOfA)
                }

            // B는 수정도 삭제도 못 한다.
            expectResultCode(
                updateSeriesRequest(userB.accessToken, seriesOfA, "B가 빼앗은 제목", "B의 본문"),
                HttpStatus.FORBIDDEN,
                "403-1",
            )
            expectResultCode(deleteSeriesRequest(userB.accessToken, seriesOfA), HttpStatus.FORBIDDEN, "403-1")

            // 두 번의 실패가 아무것도 바꾸지 않았는지 확인한다. 조회 자체는 누구에게나 열려 있다.
            getSeries(seriesOfA)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.title).isEqualTo("시나리오3 A의 시리즈")
                    assertThat(body.data.userId).isEqualTo(userA.user.id)
                }
        }

        @Test
        @DisplayName("4. 검색 → 상세 → 수정 → 재검색으로 바뀐 제목이 잡힌다")
        fun scenario_search_read_update_searchAgain() {
            val accessToken = newUserToken()
            val oldKeyword = uniqueSearchKeyword()
            val newKeyword = uniqueSearchKeyword()

            val seriesId = createSeries(accessToken, oldKeyword, "시나리오4 본문")

            searchSeriesRequest("?keyword=$oldKeyword")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .containsExactly(seriesId)
                }

            getSeries(seriesId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SeriesResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.title).isEqualTo(oldKeyword)
                }

            expectResultCode(
                updateSeriesRequest(accessToken, seriesId, newKeyword, "시나리오4 수정 본문"),
                HttpStatus.OK,
                "200-1",
            )

            // 새 제목으로는 잡히고
            searchSeriesRequest("?keyword=$newKeyword")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content)
                        .extracting<Long>(SeriesListResponse::id)
                        .containsExactly(seriesId)
                }

            // 옛 제목으로는 더 이상 잡히지 않는다.
            searchSeriesRequest("?keyword=$oldKeyword")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResponse<SeriesListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data.content).isEmpty()
                }
        }

        @Test
        @DisplayName("5. 썸네일을 교체하면 GET /users/{userId}의 thumbnailUrl도 새 url로 갱신된다")
        fun scenario_replaceThumbnail_updatesThumbnailUrlInUserSeriesList() {
            val user = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = user.accessToken
            val userId = user.user.id

            val seriesId = createSeries(accessToken, "시나리오5 시리즈", "본문")
            assertThat(userSeriesListEntry(userId, seriesId).thumbnailUrl).isNull()

            val first = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "scenario5-first.png")
            assertThat(userSeriesListEntry(userId, seriesId).thumbnailUrl).isEqualTo(first.url)
            assertThat(Files.exists(uploadedFilePath(requireNotNull(first.url)))).isTrue()

            val second = uploadSeriesMedia(accessToken, seriesId, PNG_BYTES, "scenario5-second.png")
            assertThat(second.id).isEqualTo(first.id)
            assertThat(second.url).isNotEqualTo(first.url)

            val afterReplace = userSeriesListEntry(userId, seriesId)
            assertThat(afterReplace.thumbnailUrl).isEqualTo(second.url)
            assertThat(afterReplace.thumbnailUrl).isNotEqualTo(first.url)
            assertThat(Files.exists(uploadedFilePath(requireNotNull(first.url)))).isFalse()
            assertThat(Files.exists(uploadedFilePath(requireNotNull(second.url)))).isTrue()
        }
    }

    companion object {
        private const val DEFAULT_PASSWORD = "password123"
        private const val NON_EXISTENT_SERIES_ID = 999_999_999L
        private const val NON_EXISTENT_USER_ID = 999_999_999L
        private const val NON_EXISTENT_POST_ID = 999_999_999L

        // 배치 3: 썸네일 업로드용 최소 PNG 바이트(UserControllerE2ETest의 PNG_BYTES와 동일 선례).
        // 서버는 MultipartFile.getContentType()만 보고 형식을 판단하므로 실제 이미지로서 유효할 필요는 없다.
        private val PNG_BYTES =
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

        // file.path(test 프로파일: "./build/test-uploads", 트레일링 슬래시 없음)에 category("series")가
        // 문자열로 그대로 이어붙어 실제로는 "build/test-uploadsseries/..."가 저장 경로가 된다
        // (계획서 C-3이 가정한 "build/test-uploads/series/"와 다르다 — 실행해서 확인한 실제 동작).
        private val SERIES_UPLOAD_DIR = Path.of("build", "test-uploadsseries")

        /** B-4 기준 — #6 응답이 노출해야 하는 JSON 키 13개. 초과/누락을 모두 이 상수로 고정한다. */
        private val POST_LIST_RESPONSE_KEYS =
            listOf(
                "id",
                "userId",
                "nickname",
                "seriesId",
                "title",
                "publishStatus",
                "accessLevel",
                "viewCount",
                "likeCount",
                "bookmarkCount",
                "isLiked",
                "isBookmarked",
                "createdAt",
            )

        private fun uniqueSearchKeyword(): String =
            "e2eSeriesSearch" +
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 10)

        @JvmStatic
        @AfterAll
        fun cleanUpUploadedFiles() {
            if (!Files.exists(SERIES_UPLOAD_DIR)) return

            Files.walk(SERIES_UPLOAD_DIR).use { walk ->
                walk
                    .filter { path -> path != SERIES_UPLOAD_DIR }
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
