package com.scommit.domain.post.post.controller

// Post 컨트롤러 E2E — CRUD, 목록/검색, 접근 제어(PRIVATE/PAID), 미디어
// 컨벤션: User/Comment/Like/Bookmark E2E 선례를 따른다(문서화된 규약 파일은 아직 없음).
//
// 결정사항 (변경 금지)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient(spring-test, 서블릿 계열).
//   Mock / MockMvc / @MockitoBean / webflux 의존성 일절 사용 금지, 실제 HTTP 요청만.
// - DB: 이 클래스 전용 H2 in-memory(postE2edb). 다른 @SpringBootTest 와 datasource.url을
//   공유하면 같은 JVM 안에서 create-drop이 서로의 스키마를 침범할 수 있다.
// - 픽스처: 전량 실제 API 조립. @TestConfiguration을 만들지 않는다.
// - 응답 본문은 RsData가 아니라 ApiResponse 미러 레코드로 받는다.
// - 미인증(401-1)은 AuthenticationEntryPoint와 컨트롤러의 BusinessException(UNAUTHORIZED)이
//   같은 코드를 쓰므로 msg까지 검증한다. SecurityConfig가 먼저 잘라내므로
//   언제나 EntryPoint의 "로그인 후 이용해주세요."가 나온다.
// - Like/Bookmark는 별도 컨트롤러(LikeControllerE2ETest/BookmarkControllerE2ETest, likedb/bookmarkdb)
//   담당이라 이 클래스에서 다루지 않는다.

import com.scommit.domain.media.media.repository.MediaRepository
import com.scommit.domain.post.post.dto.PostCreateRequest
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.dto.PostUpdateRequest
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.post.postmedia.dto.PostMediaResponse
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.repository.PostMediaRepository
import com.scommit.global.e2e.ApiResponse
import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.bearer
import com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken
import com.scommit.global.e2e.E2ETestSupport.createUserAndLogin
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Comparator
import java.util.UUID
import com.scommit.domain.media.media.entity.Media as MediaEntity

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:postE2edb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
class PostControllerE2ETest {
    // 썸네일 업로드용 최소 PNG 바이트(UserControllerE2ETest/SeriesControllerE2ETest의 PNG_BYTES와 동일 선례).
    // 서버는 MultipartFile.getContentType()만 보고 형식을 판단하므로 실제 이미지로서 유효할 필요는 없다.

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var postMediaRepository: PostMediaRepository

    @Autowired
    private lateinit var mediaRepository: MediaRepository

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    /** 응답에 드러나지 않는 부작용(소프트 삭제, 연관 행 잔존 등)을 DB에서 직접 되짚기 위한 조회. */
    private fun findPost(postId: Long): Post = checkNotNull(postRepository.findByIdOrNull(postId))

    /** GET /api/posts 는 Slice를 그대로 노출한다(PostController.getPosts 확인,
     *  SeriesControllerE2ETest의 SliceResult(D-6)와 동일 패턴). */
    private data class SliceResult<T>(
        val content: List<T>,
        val size: Int,
        val number: Int,
        val first: Boolean,
        val last: Boolean,
    )

    /** GET /api/posts/me, /users/{userId} 는 Page를 그대로 노출한다(PostController 확인).
     *  Page는 인터페이스라 역직렬화할 구체 타입이 없어 미러 레코드로 받는다(규약 3장). */
    private data class PageResult<T>(
        val content: List<T>,
        val totalElements: Long,
        val size: Int,
        val number: Int,
    )

    private fun newUserToken(): String =
        createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

    private fun createPostRequest(
        accessToken: String,
        title: String?,
        body: String,
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/posts")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(PostCreateRequest(null, title, body, publishStatus, accessLevel))
            .exchange()

    /** 생성 성공을 전제로 게시글 id를 돌려준다. 다른 API의 대상 게시글을 만들 때 쓴다. */
    private fun createPost(
        accessToken: String,
        title: String,
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
    ): Long =
        checkNotNull(
            createPostRequest(accessToken, title, "본문 내용", publishStatus, accessLevel)
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostResponse>>()
                .returnResult()
                .responseBody,
        ).data().id()

    private fun getPost(
        id: Any,
        accessToken: String?,
    ): RestTestClient.ResponseSpec {
        val request = client.get().uri("/api/posts/$id")
        return (accessToken?.let { request.header("Authorization", bearer(it)) } ?: request).exchange()
    }

    private fun updatePostRequest(
        accessToken: String,
        id: Any,
        title: String,
        body: String,
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
    ): RestTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/posts/$id")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(PostUpdateRequest(null, title, body, publishStatus, accessLevel))
            .exchange()

    private fun deletePostRequest(
        accessToken: String,
        id: Any,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/posts/$id")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** 규약 2장: MultipartBodyBuilder는 reactive-streams 미의존 클래스패스에서
     *  NoClassDefFoundError가 나서 쓸 수 없다. FormHttpMessageConverter가 맵의 key를
     *  part 이름으로 쓴다는 점을 이용해 직접 조립한다. */
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

    private fun uploadMediaRequest(
        accessToken: String,
        postId: Long,
        type: PostMediaType,
        bytes: ByteArray,
        filename: String,
        contentType: MediaType?,
    ): RestTestClient.ResponseSpec {
        val body: MultiValueMap<String, HttpEntity<*>> =
            LinkedMultiValueMap<String, HttpEntity<*>>().apply {
                add("file", filePart(bytes, filename, contentType))
            }

        return client
            .post()
            .uri("/api/posts/$postId/medias?type=$type")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .exchange()
    }

    /** 미인증 401은 두 지점에서 날 수 있어 msg까지 본다(규약 6장). */
    private fun expectEntryPointUnauthorized(response: RestTestClient.ResponseSpec) {
        response
            .expectStatus()
            .isUnauthorized()
            .expectBody(ApiResponse.VOID_BODY)
            .value { body ->
                checkNotNull(body)
                assertThat(body.resultCode()).isEqualTo("401-1")
                assertThat(body.msg()).isEqualTo("로그인 후 이용해주세요.")
            }
    }

    // ───────────────────────── POST /api/posts ─────────────────────────

    @Nested
    @DisplayName("POST /api/posts — 게시글 생성")
    inner class CreatePost {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun createPost_unauthenticated_returns401_1() {
            expectEntryPointUnauthorized(
                client
                    .post()
                    .uri("/api/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PostCreateRequest(null, "미인증 글", "본문", PublishStatus.PUBLIC, PostAccessLevel.FREE))
                    .exchange(),
            )
        }

        @Test
        @DisplayName("2. 성공하면 201과 게시글 정보를 반환하고, GET /{id}로 다시 조회된다")
        fun createPost_success_returns201AndIsRetrievable() {
            val accessToken = newUserToken()

            var createdId: Long? = null
            createPostRequest(accessToken, "e2e 생성 글", "e2e 생성 본문", PublishStatus.PUBLIC, PostAccessLevel.FREE)
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("201-1")
                    assertThat(body.msg()).isEqualTo("게시글이 생성되었습니다.")
                    assertThat(body.data().id()).isNotNull()
                    assertThat(body.data().title()).isEqualTo("e2e 생성 글")
                    assertThat(body.data().body()).isEqualTo("e2e 생성 본문")
                    assertThat(body.data().publishStatus()).isEqualTo(PublishStatus.PUBLIC)
                    assertThat(body.data().accessLevel()).isEqualTo(PostAccessLevel.FREE)
                    assertThat(body.data().viewCount()).isZero()
                    assertThat(body.data().likeCount()).isZero()
                    assertThat(body.data().isLiked).isFalse()
                    assertThat(body.data().isBookmarked).isFalse()
                    createdId = body.data().id()
                }

            // 생성이라는 부작용 자체를 DB에서 되짚어 확인한다.
            val saved = findPost(requireNotNull(createdId))
            assertThat(saved.title).isEqualTo("e2e 생성 글")
            assertThat(saved.deletedAt).isNull()

            // 응답만 그럴듯한 게 아니라 실제로 저장되었는지 후속 조회로도 확인한다.
            getPost(requireNotNull(createdId), accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().title()).isEqualTo("e2e 생성 글")
                }
        }

        @Test
        @DisplayName("3. 임시저장(DRAFT)으로 생성할 수 있다")
        fun createPost_draft_success() {
            val accessToken = newUserToken()

            createPostRequest(accessToken, "임시저장 글", "작성중", PublishStatus.DRAFT, PostAccessLevel.FREE)
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().publishStatus()).isEqualTo(PublishStatus.DRAFT)
                }
        }

        // FIXME(#1): PostCreateRequest에는 @NotBlank 등 요청 검증이 없고 PostService.createPost()도
        // title을 직접 검사하지 않는다. Post 엔티티의 title 컬럼이 @Column(nullable = false)라
        // null title은 요청 단계가 아니라 INSERT 시점에 DB NOT NULL 제약 위반으로 걸리고,
        // 이 프로젝트엔 그 제약 위반 예외 전용 핸들러가 없어 GlobalExceptionHandler의 포괄
        // Exception 핸들러로 떨어져 400이 아니라 500-1이 된다.
        @Test
        @DisplayName("4. title이 없으면(null) 실제 동작을 그대로 고정한다 — 500-1이 난다")
        fun createPost_nullTitle_actualBehaviorReturns500_1() {
            val accessToken = newUserToken()

            createPostRequest(accessToken, null, "본문만 있음", PublishStatus.PUBLIC, PostAccessLevel.FREE)
                .expectStatus()
                .is5xxServerError()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("500-1")
                }
        }

        @Test
        @DisplayName("5. JSON 형식이 올바르지 않으면 400-1과 파싱 실패 메시지를 반환한다")
        fun createPost_malformedJson_returns400_1WithParseErrorMessage() {
            val accessToken = newUserToken()

            client
                .post()
                .uri("/api/posts")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ \"title\": ")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("400-1")
                    assertThat(body.msg()).isEqualTo("올바른 JSON 요청 형식이 아닙니다.")
                }
        }
    }

    // ───────────────────────── GET /api/posts/{id} ─────────────────────────

    @Nested
    @DisplayName("GET /api/posts/{id} — 게시글 상세 조회")
    inner class GetPost {
        @Test
        @DisplayName("1. 존재하지 않으면 404-3을 반환한다")
        fun getPost_notFound_returns404_3() {
            val accessToken = newUserToken()

            expectResultCode(getPost(NON_EXISTENT_POST_ID, accessToken), HttpStatus.NOT_FOUND, "404-3")
        }

        @Test
        @DisplayName("2. 성공할 때마다 조회수가 1씩 증가한다")
        fun getPost_success_increasesViewCountEachTime() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "조회 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            getPost(postId, accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().viewCount()).isEqualTo(1)
                }

            getPost(postId, accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().viewCount()).isEqualTo(2)
                }
        }

        @Test
        @DisplayName("3. PUBLIC/FREE 게시글은 비로그인으로도 조회 가능하다")
        fun getPost_publicFree_accessibleWithoutLogin() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "비로그인 조회 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            getPost(postId, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글 상세 정보입니다.")
                    assertThat(body.data().isLocked).isFalse()
                    assertThat(body.data().body()).isEqualTo("본문 내용")
                }
        }

        @Test
        @DisplayName("4. PRIVATE 게시글은 타인이 조회하면 403-1을 반환한다")
        fun getPost_private_otherUser_returns403_1() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "비공개 글", PublishStatus.PRIVATE, PostAccessLevel.FREE)

            val otherToken = newUserToken()
            expectResultCode(getPost(postId, otherToken), HttpStatus.FORBIDDEN, "403-1")
        }

        @Test
        @DisplayName("5. PRIVATE 게시글은 작성자 본인은 정상 조회 가능하다")
        fun getPost_private_owner_returns200() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "비공개 글", PublishStatus.PRIVATE, PostAccessLevel.FREE)

            getPost(postId, ownerToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().id()).isEqualTo(postId)
                }
        }

        @Test
        @DisplayName("6. PAID 게시글은 비멤버십 유저에게 잠금 처리(200 + isLocked=true, body 없음)된다")
        fun getPost_paid_nonMember_returnsLocked() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "유료 게시글", PublishStatus.PUBLIC, PostAccessLevel.PAID)

            val nonMemberToken = newUserToken()
            getPost(postId, nonMemberToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().isLocked).isTrue()
                    assertThat(body.data().body()).isNull()
                }
        }

        @Test
        @DisplayName("7. PAID 게시글은 멤버십 가입자에게는 잠금 해제된다")
        fun getPost_paid_member_returnsUnlocked() {
            val owner = signUpAndLoginHolder()
            val postId = createPost(owner.accessToken, "유료 게시글", PublishStatus.PUBLIC, PostAccessLevel.PAID)

            val memberToken = newUserToken()
            client
                .post()
                .uri("/api/subscriptions/membership/${owner.userId}")
                .header("Authorization", bearer(memberToken))
                .exchange()
                .expectStatus()
                .isOk()

            getPost(postId, memberToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().isLocked).isFalse()
                    assertThat(body.data().body()).isEqualTo("본문 내용")
                }
        }

        @Test
        @DisplayName("8. PAID 게시글은 작성자 본인에게는 구독 여부와 무관하게 항상 잠금 해제된다")
        fun getPost_paid_owner_alwaysUnlocked() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "내 유료 게시글", PublishStatus.PUBLIC, PostAccessLevel.PAID)

            getPost(postId, ownerToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().isLocked).isFalse()
                }
        }
    }

    // ───────────────────────── PUT /api/posts/{id} ─────────────────────────

    @Nested
    @DisplayName("PUT /api/posts/{id} — 게시글 수정")
    inner class UpdatePost {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun updatePost_unauthenticated_returns401_1() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "수정 대상", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            expectEntryPointUnauthorized(
                client
                    .put()
                    .uri("/api/posts/$postId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PostUpdateRequest(null, "무단 수정", "본문", PublishStatus.PUBLIC, PostAccessLevel.FREE))
                    .exchange(),
            )
        }

        @Test
        @DisplayName("2. 소유자가 수정하면 200과 수정된 내용을 반환하고, GET /{id}에도 반영된다")
        fun updatePost_owner_success_returns200AndIsReflected() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "수정 전 제목", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            updatePostRequest(accessToken, postId, "수정된 제목", "수정된 본문", PublishStatus.PUBLIC, PostAccessLevel.FREE)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글이 수정되었습니다.")
                    assertThat(body.data().title()).isEqualTo("수정된 제목")
                    assertThat(body.data().body()).isEqualTo("수정된 본문")
                }

            // 수정이라는 부작용을 DB와 후속 조회로 각각 되짚는다.
            assertThat(findPost(postId).title).isEqualTo("수정된 제목")

            getPost(postId, accessToken)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().title()).isEqualTo("수정된 제목")
                }
        }

        // Comment의 UpdateComment#2(FIXME#5)는 CommentService.updateComment가 flush 전에
        // new CommentResponse(comment)를 만들어서 응답에 갱신 전 updatedAt이 실리는 버그가 있다.
        // PostService.updatePost도 겉보기엔 똑같이 post.update() 직후 곧장 new PostResponse(...)를
        // 만들지만, 그 생성자 인자로 isLiked(id, actor)/isBookmarked(id, actor)를 호출한다 — 이 둘이
        // likeRepository/bookmarkRepository로 실제 쿼리를 날리는데, Hibernate가 기본 FlushMode.AUTO라
        // dirty 상태의 Post가 남아있는 채로 새 쿼리를 실행하면 그 전에 자동 flush를 해버린다.
        // 그 결과 @LastModifiedDate(updatedAt)가 이 자동 flush 시점에 채워지고, 그 다음에 실행되는
        // PostResponse 생성자가 이미 갱신된 값을 읽는다 — 의도한 설계가 아니라 우연히 버그를 피한
        // 것이지만, 실제 동작은 정확하다. 이 사실 자체를 회귀 방지용으로 고정해 둔다.
        @Test
        @DisplayName("3. 수정 응답의 updatedAt은 DB와 동일하게 갱신된 값이다 (Comment와 달리 이 버그가 없다)")
        fun updatePost_response_updatedAtMatchesDb() {
            val accessToken = newUserToken()

            val created =
                checkNotNull(
                    createPostRequest(accessToken, "수정 전 제목", "수정 전 본문", PublishStatus.PUBLIC, PostAccessLevel.FREE)
                        .expectStatus()
                        .isCreated()
                        .expectBody<ApiResponse<PostResponse>>()
                        .returnResult()
                        .responseBody,
                ).data()
            val postId = created.id()
            val createdUpdatedAt = created.updatedAt()

            val updated =
                checkNotNull(
                    updatePostRequest(
                        accessToken,
                        postId,
                        "수정된 제목",
                        "수정된 본문",
                        PublishStatus.PUBLIC,
                        PostAccessLevel.FREE,
                    ).expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<PostResponse>>()
                        .returnResult()
                        .responseBody,
                ).data()

            // 응답 값: 생성 시점보다 나중이고, 갱신 전 값 그대로가 아니다.
            assertThat(updated.updatedAt()).isAfter(createdUpdatedAt)

            // DB 값: 응답과 사실상 같은 시점이 실제로 반영돼 있다(JSON 직렬화의 초 이하 정밀도 손실
            // 감안해 100ms 이내 오차는 허용 — Comment의 스테일 버그처럼 수백 ms~초 단위로 어긋나는
            // 것과는 구분된다).
            val dbUpdatedAtAfter: LocalDateTime = findPost(postId).updatedAt
            assertThat(Duration.between(updated.updatedAt(), dbUpdatedAtAfter).abs())
                .isLessThan(Duration.ofMillis(100))

            // createdAt은 응답/DB 모두 그대로다. DB가 나노초보다 낮은 정밀도로 저장할 수 있어
            // 완전 일치 대신 오차범위로 비교한다(CommentControllerE2ETest와 동일한 이유).
            assertThat(updated.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS))
            assertThat(findPost(postId).createdAt).isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS))
        }

        @Test
        @DisplayName("4. 작성자가 아니면 403-1을 반환하고 내용이 바뀌지 않는다")
        fun updatePost_notOwner_returns403_1() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "수정 권한 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            val otherToken = newUserToken()
            expectResultCode(
                updatePostRequest(otherToken, postId, "남의 글 수정 시도", "내용", PublishStatus.PUBLIC, PostAccessLevel.FREE),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            assertThat(findPost(postId).title).isEqualTo("수정 권한 테스트")
        }

        @Test
        @DisplayName("5. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun updatePost_notFound_returns404_3() {
            val accessToken = newUserToken()

            expectResultCode(
                updatePostRequest(
                    accessToken,
                    NON_EXISTENT_POST_ID,
                    "제목",
                    "본문",
                    PublishStatus.PUBLIC,
                    PostAccessLevel.FREE,
                ),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("6. 소프트 삭제된 게시글이면 404-3을 반환하고 내용이 바뀌지 않는다")
        fun updatePost_softDeletedPost_returns404_3() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "삭제 후 수정 시도 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            deletePostRequest(accessToken, postId).expectStatus().isOk()

            expectResultCode(
                updatePostRequest(accessToken, postId, "수정 시도", "본문", PublishStatus.PUBLIC, PostAccessLevel.FREE),
                HttpStatus.NOT_FOUND,
                "404-3",
            )

            assertThat(findPost(postId).title).isEqualTo("삭제 후 수정 시도 테스트")
        }
    }

    // ───────────────────────── DELETE /api/posts/{id} ─────────────────────────

    @Nested
    @DisplayName("DELETE /api/posts/{id} — 게시글 삭제")
    inner class DeletePost {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun deletePost_unauthenticated_returns401_1() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "삭제 대상", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            expectEntryPointUnauthorized(client.delete().uri("/api/posts/$postId").exchange())
        }

        @Test
        @DisplayName("2. 소유자가 삭제하면 200-1을 반환하고, deletedAt이 세팅되며 이후 GET /{id}는 404-3이 된다")
        fun deletePost_owner_success_returns200AndSoftDeletes() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "삭제될 게시글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            deletePostRequest(accessToken, postId)
                .expectStatus()
                .isOk()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글이 삭제되었습니다.")
                    assertThat(body.data()).isNull()
                }

            // 하드 삭제가 아니라 소프트 삭제라는 부작용을 DB에서 직접 확인한다.
            val deleted = findPost(postId)
            assertThat(deleted.deletedAt).isNotNull()
            assertThat(deleted.title).isEqualTo("삭제될 게시글")

            getPost(postId, accessToken).expectStatus().isNotFound()
        }

        @Test
        @DisplayName("3. 작성자가 아니면 403-1을 반환하고 삭제되지 않는다")
        fun deletePost_notOwner_returns403_1() {
            val ownerToken = newUserToken()
            val postId = createPost(ownerToken, "삭제 권한 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            val otherToken = newUserToken()
            expectResultCode(deletePostRequest(otherToken, postId), HttpStatus.FORBIDDEN, "403-1")

            assertThat(findPost(postId).deletedAt).isNull()
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun deletePost_notFound_returns404_3() {
            val accessToken = newUserToken()

            expectResultCode(deletePostRequest(accessToken, NON_EXISTENT_POST_ID), HttpStatus.NOT_FOUND, "404-3")
        }

        @Test
        @DisplayName("5. 이미 삭제한 게시글을 다시 삭제하면 404-3을 반환한다")
        fun deletePost_alreadyDeleted_returns404_3() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "재삭제 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            deletePostRequest(accessToken, postId).expectStatus().isOk()
            val firstDeletedAt = findPost(postId).deletedAt

            expectResultCode(deletePostRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3")

            // 두 번째 요청이 deletedAt을 덮어쓰지 않는다.
            assertThat(findPost(postId).deletedAt).isEqualTo(firstDeletedAt)
        }

        // FIXME(#2): PostService.deletePost()는 post.softDelete()만 호출하고 PostMedia/Media는 건드리지
        // 않는다. SeriesControllerE2ETest.java의 series-e2e-known-issues.md #2(SeriesMedia/Media 고아)와
        // 정확히 같은 패턴이고, Post 자신의 하위 리소스(Like/Comment)에서도 같은 근본 원인(soft delete가
        // 연관 데이터를 정리하지 않음)이 LikeControllerE2ETest.java FIXME(#2), CommentControllerE2ETest.java
        // FIXME(#7)에 각각 별도로 문서화돼 있다 — 전부 PostService.deletePost()가 softDelete() 외에 아무것도
        // 안 하는 이 지점이 원인이다.
        // 남은 Media를 개별 DELETE API로 정리하는 것도 불가능하다 — PostMediaService.deleteMedia()가
        // post.getDeletedAt() != null이면 POST_NOT_FOUND(404-3)를 던져서, 삭제된 게시글에 딸린
        // 미디어는 API로 정리할 방법 자체가 없다.
        @Test
        @DisplayName("6. 삭제해도 썸네일 PostMedia/Media 행과 파일은 정리되지 않고 그대로 남는다")
        fun deletePost_doesNotCleanUpThumbnailRowsOrFile() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "썸네일 있는 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            val uploaded: ApiResponse<PostMediaResponse> =
                checkNotNull(
                    uploadMediaRequest(
                        accessToken,
                        postId,
                        PostMediaType.THUMBNAIL,
                        PNG_BYTES,
                        "thumb.png",
                        MediaType.IMAGE_PNG,
                    ).expectStatus()
                        .isCreated()
                        .expectBody<ApiResponse<PostMediaResponse>>()
                        .returnResult()
                        .responseBody,
                )
            val mediaId = uploaded.data().id()

            deletePostRequest(accessToken, postId).expectStatus().isOk()

            // API 응답만으로는 잔존 여부를 알 수 없다 — postMediaService가 postId 존재부터 확인해서
            // 삭제된 게시글의 미디어 목록/썸네일 조회·삭제가 전부 404-3으로 막히기 때문이다.
            // 실제로 행과 파일이 남아 있는지는 DB/파일시스템을 직접 봐야 확인된다.
            val orphan = checkNotNull(postMediaRepository.findByIdOrNull(mediaId))
            assertThat(orphan.post.id).isEqualTo(postId)

            val orphanMedia: MediaEntity = checkNotNull(mediaRepository.findByIdOrNull(orphan.media.id))
            // Media.url = "post/{uuid}_{원본파일명}" — LocalMediaService.uploadFile()이
            // file.path("./build/test-uploads") + url 을 이어붙여 저장하므로, "post/" 다음 부분이
            // POST_UPLOAD_DIR("build/test-uploadspost") 바로 아래의 실제 파일명이 된다.
            val storedFilename = orphanMedia.url.substring(orphanMedia.url.indexOf('/') + 1)
            assertThat(Files.exists(POST_UPLOAD_DIR.resolve(storedFilename))).isTrue()

            expectResultCode(
                client
                    .method(HttpMethod.DELETE)
                    .uri("/api/posts/$postId/medias/$mediaId")
                    .header("Authorization", bearer(accessToken))
                    .exchange(),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }
    }

    // ───────────────────────── GET /api/posts/me ─────────────────────────

    @Nested
    @DisplayName("GET /api/posts/me — 내 게시글 목록 조회")
    inner class GetMyPosts {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun getMyPosts_unauthenticated_returns401_1() {
            expectEntryPointUnauthorized(client.get().uri("/api/posts/me").exchange())
        }

        @Test
        @DisplayName("2. 성공하면 내가 쓴 글(DRAFT 포함)만 조회된다")
        fun getMyPosts_success_includesOwnDraftPosts() {
            val accessToken = newUserToken()
            createPost(accessToken, "내 글 1", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            createPost(accessToken, "내 글 2", PublishStatus.DRAFT, PostAccessLevel.FREE)

            val otherToken = newUserToken()
            createPost(otherToken, "남의 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts/me")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("내가 쓴 게시글 목록입니다.")
                    assertThat(body.data().content).hasSize(2)
                    assertThat(body.data().content).extracting("title").doesNotContain("남의 글")
                }
        }
    }

    // ───────────────────────── GET /api/posts/users/{userId} ─────────────────────────

    @Nested
    @DisplayName("GET /api/posts/users/{userId} — 특정 유저 게시글 목록 조회")
    inner class GetUserPosts {
        @Test
        @DisplayName("1. PUBLIC 게시글을 포함한다")
        fun getUserPosts_includesPublicPost() {
            val owner = signUpAndLoginHolder()
            createPost(owner.accessToken, "프로필 공개 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts/users/${owner.userId}")
                .header("Authorization", bearer(newUserToken()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("유저의 게시글 목록입니다.")
                    assertThat(body.data().content).extracting("title").contains("프로필 공개 글")
                }
        }

        // FIXME(#3): PostRepository.findByUserAndDeletedAtIsNull()에 publishStatus 필터가 없어
        // 다른 유저의 PRIVATE/DRAFT 게시글까지 그대로 노출된다.
        @Test
        @DisplayName("2. PRIVATE/DRAFT 게시글도 실제 동작을 그대로 고정한다 — 그대로 노출된다")
        fun getUserPosts_privateAndDraft_actualBehaviorExposesThem() {
            val owner = signUpAndLoginHolder()
            createPost(owner.accessToken, "공개 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            createPost(owner.accessToken, "비공개 글", PublishStatus.PRIVATE, PostAccessLevel.FREE)
            createPost(owner.accessToken, "임시저장 글", PublishStatus.DRAFT, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts/users/${owner.userId}")
                .header("Authorization", bearer(newUserToken()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().content).hasSize(3)
                }
        }
    }

    // ───────────────────────── GET /api/posts ─────────────────────────

    @Nested
    @DisplayName("GET /api/posts — 전체 게시글 목록 조회")
    inner class GetPosts {
        @Test
        @DisplayName("1. creatorId로 필터링하면 해당 유저의 PUBLIC 글이 조회된다")
        fun getPosts_filterByCreatorId_includesPublicPost() {
            val owner = signUpAndLoginHolder()
            createPost(owner.accessToken, "크리에이터 공개 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts?creatorId=${owner.userId}")
                .header("Authorization", bearer(newUserToken()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글 목록입니다.")
                    assertThat(body.data().content).extracting("title").contains("크리에이터 공개 글")
                }
        }

        // FIXME(#3): PostRepository.findSliceByUserAndDeletedAtIsNull()에도 publishStatus 필터가 없다.
        // 바로 위 GetUserPosts#2와 같은 근본 원인.
        @Test
        @DisplayName("2. creatorId 필터링도 PRIVATE/DRAFT를 실제 동작 그대로 노출한다")
        fun getPosts_filterByCreatorId_actualBehaviorExposesPrivateAndDraft() {
            val owner = signUpAndLoginHolder()
            createPost(owner.accessToken, "공개 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            createPost(owner.accessToken, "비공개 글", PublishStatus.PRIVATE, PostAccessLevel.FREE)
            createPost(owner.accessToken, "임시저장 글", PublishStatus.DRAFT, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts?creatorId=${owner.userId}")
                .header("Authorization", bearer(newUserToken()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<SliceResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().content).hasSize(3)
                }
        }

        // FIXME(#5): 이 프로젝트에 Pageable 정렬 예외(PropertyReferenceException) 전용 핸들러가 없어
        // GlobalExceptionHandler의 포괄 Exception 핸들러로 떨어져 500-1이 된다.
        // 같은 근본 원인(클라이언트가 잘못 준 정렬/파라미터가 500으로 응답됨)이 BookmarkControllerE2ETest.java,
        // SeriesControllerE2ETest.java에도 각각 별도 케이스로 나타난다.
        @Test
        @DisplayName("3. 존재하지 않는 정렬 프로퍼티를 쓰면 실제 동작을 그대로 고정한다 — 500-1이 난다")
        fun getPosts_invalidSortProperty_actualBehaviorReturns500_1() {
            val accessToken = newUserToken()

            client
                .get()
                .uri("/api/posts?sort=notAField,desc")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .is5xxServerError()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("500-1")
                }
        }
    }

    // ───────────────────────── GET /api/posts/search ─────────────────────────

    @Nested
    @DisplayName("GET /api/posts/search — 게시글 검색")
    inner class SearchPosts {
        private fun uniqueSearchKeyword(): String =
            "e2ePostSearch" +
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 10)

        @Test
        @DisplayName("1. 유일 키워드로 검색하면 제목에 일치하는 PUBLIC 게시글만 반환한다")
        fun searchPosts_matchesKeyword_publicOnly() {
            val accessToken = newUserToken()
            val keyword = uniqueSearchKeyword()
            createPost(accessToken, keyword, PublishStatus.PUBLIC, PostAccessLevel.FREE)
            createPost(accessToken, "$keyword-private", PublishStatus.PRIVATE, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts/search?keyword=$keyword")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글 검색 결과입니다.")
                    assertThat(body.data().content).hasSize(1)
                    assertThat(body.data().content[0].title()).isEqualTo(keyword)
                }
        }

        @Test
        @DisplayName("2. keyword가 빈 문자열이면 200과 빈 목록을 반환한다 (Post는 required=false, default=\"\")")
        fun searchPosts_blankKeyword_returnsEmpty() {
            val accessToken = newUserToken()

            client
                .get()
                .uri("/api/posts/search?keyword=")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().content).isEmpty()
                }
        }

        // series의 동명 API(#4)는 keyword가 required=true라 파라미터 누락 시 500이 나지만,
        // Post는 @RequestParam(required=false, defaultValue="")라 같은 상황에서 정상 200이 난다.
        // 두 도메인이 같은 모양의 API를 다르게 설계한 사례 — 규약 문서에 공유할 가치가 있다.
        @Test
        @DisplayName("3. keyword 파라미터 자체가 없어도 200과 빈 목록을 반환한다")
        fun searchPosts_missingKeywordParam_returns200WithEmptyList() {
            val accessToken = newUserToken()

            client
                .get()
                .uri("/api/posts/search")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<PostListResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().content).isEmpty()
                }
        }
    }

    // ───────────────────────── 게시글 미디어 ─────────────────────────

    @Nested
    @DisplayName("게시글 미디어 (업로드/조회/삭제)")
    inner class Media {
        @Test
        @DisplayName("1. 미인증이면 401-1과 \"로그인 후 이용해주세요.\"를 반환한다")
        fun uploadMedia_unauthenticated_returns401_1() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "미디어 테스트 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            val body: MultiValueMap<String, HttpEntity<*>> =
                LinkedMultiValueMap<String, HttpEntity<*>>().apply {
                    add("file", filePart(PNG_BYTES, "thumb.png", MediaType.IMAGE_PNG))
                }

            expectEntryPointUnauthorized(
                client
                    .post()
                    .uri("/api/posts/$postId/medias?type=THUMBNAIL")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .exchange(),
            )
        }

        @Test
        @DisplayName("2. 성공하면 201과 PostMediaResponse를 반환하고, GET /medias로 다시 조회된다")
        fun uploadMedia_success_returns201AndIsRetrievable() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "미디어 테스트 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            var mediaId: Long? = null
            uploadMediaRequest(
                accessToken,
                postId,
                PostMediaType.THUMBNAIL,
                PNG_BYTES,
                "thumb.png",
                MediaType.IMAGE_PNG,
            ).expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("201-1")
                    assertThat(body.msg()).isEqualTo("게시글 미디어가 추가되었습니다.")
                    assertThat(body.data().type()).isEqualTo(PostMediaType.THUMBNAIL)
                    mediaId = body.data().id()
                }

            client
                .get()
                .uri("/api/posts/$postId/medias")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<List<PostMediaResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글 미디어 목록입니다.")
                    assertThat(body.data()).extracting("id").contains(mediaId)
                }
        }

        @Test
        @DisplayName("3. 썸네일을 재업로드하면 PostMedia id는 유지된 채 url만 바뀐다")
        fun uploadThumbnail_reupload_keepsIdChangesUrl() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "썸네일 재업로드 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            val first: ApiResponse<PostMediaResponse> =
                checkNotNull(
                    uploadMediaRequest(
                        accessToken,
                        postId,
                        PostMediaType.THUMBNAIL,
                        PNG_BYTES,
                        "first.png",
                        MediaType.IMAGE_PNG,
                    ).expectStatus()
                        .isCreated()
                        .expectBody<ApiResponse<PostMediaResponse>>()
                        .returnResult()
                        .responseBody,
                )

            uploadMediaRequest(
                accessToken,
                postId,
                PostMediaType.THUMBNAIL,
                PNG_BYTES,
                "second.png",
                MediaType.IMAGE_PNG,
            ).expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().id()).isEqualTo(first.data().id())
                    assertThat(body.data().url()).isNotEqualTo(first.data().url())
                }
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun uploadMedia_postNotFound_returns404_3() {
            val accessToken = newUserToken()

            expectResultCode(
                uploadMediaRequest(
                    accessToken,
                    NON_EXISTENT_POST_ID,
                    PostMediaType.THUMBNAIL,
                    PNG_BYTES,
                    "thumb.png",
                    MediaType.IMAGE_PNG,
                ),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        // PostMediaService.uploadMedia는 postRepository.findById + post.getDeletedAt() 수동 체크로
        // 삭제 여부를 판단한다 — Post 수정/조회의 findByIdAndDeletedAtIsNull 쿼리 필터와는 다른 코드
        // 경로라 별도로 검증한다.
        @Test
        @DisplayName("5. 소프트 삭제된 게시글이면 404-3을 반환한다")
        fun uploadMedia_softDeletedPost_returns404_3() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "삭제 후 업로드 시도 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            deletePostRequest(accessToken, postId).expectStatus().isOk()

            expectResultCode(
                uploadMediaRequest(
                    accessToken,
                    postId,
                    PostMediaType.THUMBNAIL,
                    PNG_BYTES,
                    "thumb.png",
                    MediaType.IMAGE_PNG,
                ),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("6. 빈 파일이면 400-4를 반환한다")
        fun uploadMedia_emptyFile_returns400_4() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "빈 파일 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            expectResultCode(
                uploadMediaRequest(
                    accessToken,
                    postId,
                    PostMediaType.THUMBNAIL,
                    ByteArray(0),
                    "empty.png",
                    MediaType.IMAGE_PNG,
                ),
                HttpStatus.BAD_REQUEST,
                "400-4",
            )
        }

        @Test
        @DisplayName("7. text/plain 파일이면 415-1을 반환한다")
        fun uploadMedia_unsupportedType_returns415_1() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "지원 안 하는 타입 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            uploadMediaRequest(
                accessToken,
                postId,
                PostMediaType.THUMBNAIL,
                "hello".toByteArray(),
                "hello.txt",
                MediaType.TEXT_PLAIN,
            ).expectStatus()
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("415-1")
                }
        }

        // FIXME(#4): PostMediaService.getThumbnail()이 orElse(null)로 끝내고 컨트롤러가 그 null을
        // 그대로 RsData에 담아 반환한다. 404가 아니라 200으로 응답한다.
        // SeriesControllerE2ETest의 SeriesMediaService.getMedia()와 동일 패턴.
        @Test
        @DisplayName("8. 게시글에 썸네일이 없으면 실제 동작을 그대로 고정한다 — 200 + data:null")
        fun getThumbnail_noThumbnail_actualBehaviorReturns200WithNullData() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "썸네일 없는 글", PublishStatus.PUBLIC, PostAccessLevel.FREE)

            client
                .get()
                .uri("/api/posts/$postId/medias/thumbnail")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostMediaResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글 썸네일입니다.")
                    assertThat(body.data()).isNull()
                }
        }

        @Test
        @DisplayName("9. 미디어 삭제 성공 - 이후 목록에서 사라진다")
        fun deleteMedia_success_removedFromList() {
            val accessToken = newUserToken()
            val postId = createPost(accessToken, "미디어 삭제 테스트", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            val mediaId =
                checkNotNull(
                    uploadMediaRequest(
                        accessToken,
                        postId,
                        PostMediaType.BODY,
                        PNG_BYTES,
                        "body.png",
                        MediaType.IMAGE_PNG,
                    ).expectStatus()
                        .isCreated()
                        .expectBody<ApiResponse<PostMediaResponse>>()
                        .returnResult()
                        .responseBody,
                ).data().id()

            client
                .method(HttpMethod.DELETE)
                .uri("/api/posts/$postId/medias/$mediaId")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("게시글 미디어가 삭제되었습니다.")
                }

            // PostMedia 행 자체가 지워졌는지(하드 삭제) DB로 직접 확인한다.
            assertThat(postMediaRepository.findByIdOrNull(mediaId)).isNull()

            client
                .get()
                .uri("/api/posts/$postId/medias")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectBody<ApiResponse<List<PostMediaResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data()).extracting("id").doesNotContain(mediaId)
                }
        }

        @Test
        @DisplayName("10. 다른 게시글 소속 미디어 id로 삭제 시도하면 404-7을 반환하고 삭제되지 않는다")
        fun deleteMedia_wrongPostId_returns404_7() {
            val accessToken = newUserToken()
            val postId1 = createPost(accessToken, "글1", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            val postId2 = createPost(accessToken, "글2", PublishStatus.PUBLIC, PostAccessLevel.FREE)
            val mediaIdOfPost1 =
                checkNotNull(
                    uploadMediaRequest(
                        accessToken,
                        postId1,
                        PostMediaType.THUMBNAIL,
                        PNG_BYTES,
                        "t.png",
                        MediaType.IMAGE_PNG,
                    ).expectBody<ApiResponse<PostMediaResponse>>()
                        .returnResult()
                        .responseBody,
                ).data().id()

            expectResultCode(
                client
                    .method(HttpMethod.DELETE)
                    .uri("/api/posts/$postId2/medias/$mediaIdOfPost1")
                    .header("Authorization", bearer(accessToken))
                    .exchange(),
                HttpStatus.NOT_FOUND,
                "404-7",
            )

            assertThat(postMediaRepository.findByIdOrNull(mediaIdOfPost1)).isNotNull()
        }
    }

    // ───────────────────────── 통합 시나리오 ─────────────────────────

    @Nested
    @DisplayName("Scenarios — 여러 API를 잇는 흐름")
    inner class Scenarios {
        @Test
        @DisplayName("1. 작성 → DRAFT 저장 → 수정 → PUBLIC 발행 → 목록/상세에 반영된다")
        fun draftThenPublish_reflectsInListAndDetail() {
            val accessToken = newUserToken()

            val postId = createPost(accessToken, "초안", PublishStatus.DRAFT, PostAccessLevel.FREE)

            updatePostRequest(accessToken, postId, "발행된 글", "완성된 본문", PublishStatus.PUBLIC, PostAccessLevel.FREE)
                .expectStatus()
                .isOk()

            getPost(postId, null)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().publishStatus()).isEqualTo(PublishStatus.PUBLIC)
                    assertThat(body.data().title()).isEqualTo("발행된 글")
                }
        }

        @Test
        @DisplayName("2. 두 유저는 서로의 PRIVATE 게시글을 보지도 고치지도 못한다")
        fun twoUsers_cannotSeeOrModifyEachOthersPrivatePosts() {
            val userAToken = newUserToken()
            val userBToken = newUserToken()

            val postIdA = createPost(userAToken, "A의 비밀글", PublishStatus.PRIVATE, PostAccessLevel.FREE)
            val postIdB = createPost(userBToken, "B의 비밀글", PublishStatus.PRIVATE, PostAccessLevel.FREE)

            getPost(postIdA, userBToken).expectStatus().isForbidden()
            getPost(postIdB, userAToken).expectStatus().isForbidden()

            updatePostRequest(userBToken, postIdA, "해킹 시도", "본문", PublishStatus.PUBLIC, PostAccessLevel.FREE)
                .expectStatus()
                .isForbidden()
            deletePostRequest(userAToken, postIdB).expectStatus().isForbidden()
        }
    }

    // ───────────────────────── 공통: 로그인 응답에서 userId까지 필요한 경우 ─────────────────────────

    private data class LoginResponseHolder(
        val accessToken: String,
        val userId: Long,
    )

    private fun signUpAndLoginHolder(): LoginResponseHolder {
        val loginResponse = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
        return LoginResponseHolder(loginResponse.accessToken(), loginResponse.user().id())
    }

    companion object {
        private const val DEFAULT_PASSWORD = "password123"
        private const val NON_EXISTENT_POST_ID = 999_999_999L
        private val PNG_BYTES =
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

        // file.path(test 프로파일: "./build/test-uploads", 트레일링 슬래시 없음)에 category("post")가
        // 문자열로 그대로 이어붙어 실제로는 "build/test-uploadspost/..."가 저장 경로가 된다
        // (LocalMediaService.uploadFile 확인 — SeriesControllerE2ETest의 SERIES_UPLOAD_DIR("build/test-uploadsseries")과
        // 같은 원인. 버그로 등재된 known-issue는 아니고 실행해서 확인한 실제 동작이다).
        private val POST_UPLOAD_DIR = Path.of("build", "test-uploadspost")

        @JvmStatic
        @AfterAll
        fun cleanUpUploadedFiles() {
            if (!Files.exists(POST_UPLOAD_DIR)) return

            Files.walk(POST_UPLOAD_DIR).use { walk ->
                walk
                    .filter { path -> path != POST_UPLOAD_DIR }
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
