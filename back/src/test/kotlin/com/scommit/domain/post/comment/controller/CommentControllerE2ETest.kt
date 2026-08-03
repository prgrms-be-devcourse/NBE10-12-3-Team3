package com.scommit.domain.post.comment.controller

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient. Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(commentdb). 다른 @SpringBootTest 와 spring.datasource.url 을
//   공유하면 같은 JVM 안에서 create-drop 이 서로의 스키마를 침범한다(컨벤션 4장).
// - 픽스처: 댓글의 선행 데이터는 User(회원가입 API) → Post(게시글 생성 API) 뿐이고,
//   소프트 삭제된 게시글/댓글도 각각의 삭제 API 로 만들 수 있다. 즉 리포지토리로 심어야만 하는
//   데이터가 하나도 없어서 CommentE2EFixtures(@TestConfiguration)를 만들지 않았다(컨벤션 5장 우선순위 1).
//   리포지토리는 "DB 반영 확인" 용도로만 주입한다.
// - @DirtiesContext / @Transactional 미사용. 테스트끼리는 각자 만든 게시글로 격리한다.

import com.scommit.domain.post.comment.dto.CommentCreateRequest
import com.scommit.domain.post.comment.dto.CommentResponse
import com.scommit.domain.post.comment.dto.CommentUpdateRequest
import com.scommit.domain.post.comment.entity.Comment
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.post.dto.PostCreateRequest
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // 이 클래스 전용 DB 이름(컨벤션 4장 표: commentdb).
    properties = ["spring.datasource.url=jdbc:h2:mem:commentdb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
class CommentControllerE2ETest {
    // GET /api/posts/{postId}/comments 가 Spring Data 의 Page<T> 를 그대로 직렬화하는데
    // Page 는 인터페이스라 클라이언트에서 역직렬화할 구체 타입이 없다. 검증에 쓰는 네 필드만
    // 담은 미러 레코드로 받는다(컨벤션 3장).
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
    private lateinit var commentRepository: CommentRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    // ---------- 공통 헬퍼 ----------

    /** 댓글의 선행 데이터인 게시글을 실제 API 로 만든다(컨벤션 5장). */
    private fun createPost(
        accessToken: String,
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
    ): Long =
        checkNotNull(
            client
                .post()
                .uri("/api/posts")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(PostCreateRequest(null, "댓글 E2E 게시글", "게시글 본문", publishStatus, accessLevel))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<PostResponse>>()
                .returnResult()
                .responseBody,
        ).data().id()

    private fun createPublicPost(accessToken: String): Long =
        createPost(accessToken, PublishStatus.PUBLIC, PostAccessLevel.FREE)

    private fun deletePost(
        accessToken: String,
        postId: Long,
    ) {
        client
            .delete()
            .uri("/api/posts/$postId")
            .header("Authorization", bearer(accessToken))
            .exchange()
            .expectStatus()
            .isOk()
    }

    private fun createCommentRequest(
        accessToken: String,
        postId: Long,
        body: String?,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/posts/$postId/comments")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(CommentCreateRequest(body))
            .exchange()

    /** 댓글 작성 성공을 전제로 응답 data 를 돌려준다. */
    private fun createComment(
        accessToken: String,
        postId: Long,
        body: String?,
    ): CommentResponse =
        checkNotNull(
            createCommentRequest(accessToken, postId, body)
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<CommentResponse>>()
                .returnResult()
                .responseBody,
        ).data()

    private fun getCommentsRequest(
        postId: Long,
        page: Int? = null,
        size: Int? = null,
    ): RestTestClient.ResponseSpec {
        val builder = UriComponentsBuilder.fromPath("/api/posts/$postId/comments")
        page?.let { builder.queryParam("page", it) }
        size?.let { builder.queryParam("size", it) }
        return client.get().uri(builder.build().toUriString()).exchange()
    }

    private fun updateCommentRequest(
        accessToken: String,
        postId: Long,
        commentId: Long,
        body: String,
    ): RestTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/posts/$postId/comments/$commentId")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body(CommentUpdateRequest(body))
            .exchange()

    private fun deleteCommentRequest(
        accessToken: String,
        postId: Long,
        commentId: Long,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/posts/$postId/comments/$commentId")
            .header("Authorization", bearer(accessToken))
            .exchange()

    private fun findComment(commentId: Long): Comment = checkNotNull(commentRepository.findByIdOrNull(commentId))

    /** 목록 API 를 거치지 않고 "이 댓글이 이 게시글에 달렸는지"를 DB 로 직접 확인한다. */
    private fun liveCommentIdsOf(postId: Long): List<Long> =
        commentRepository
            .findAllByPostIdAndDeletedAtIsNull(postId, PageRequest.of(0, 50))
            .content
            .map { checkNotNull(it.id) }

    @Nested
    @DisplayName("POST /api/posts/{postId}/comments — 댓글 작성")
    inner class CreateComment {
        @Test
        @DisplayName("1. 성공하면 201-1과 댓글 정보를 반환하고 DB에 반영된다")
        fun createComment_success_returns201AndPersistsComment() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken()
            val userId = session.user().id()
            val nickname = session.user().nickname()
            val postId = createPublicPost(accessToken)

            client
                .post()
                .uri("/api/posts/$postId/comments")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(CommentCreateRequest("첫 번째 댓글"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<CommentResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("201-1")
                    assertThat(body.msg()).isEqualTo("댓글이 작성되었습니다.")
                    assertThat(body.data().id()).isNotNull()
                    assertThat(body.data().postId()).isEqualTo(postId)
                    assertThat(body.data().userId()).isEqualTo(userId)
                    assertThat(body.data().nickname()).isEqualTo(nickname)
                    assertThat(body.data().body()).isEqualTo("첫 번째 댓글")
                    assertThat(body.data().createdAt()).isNotNull()
                    assertThat(body.data().updatedAt()).isNotNull()

                    // 생성이라는 부작용 자체를 DB에서 되짚어 확인한다.
                    val saved = findComment(body.data().id())
                    assertThat(saved.body).isEqualTo("첫 번째 댓글")
                    assertThat(saved.deletedAt).isNull()
                    assertThat(saved.post.id).isEqualTo(postId)
                    assertThat(saved.user.id).isEqualTo(userId)
                }
        }

        @Test
        @DisplayName("2. 타인의 게시글에도 댓글을 작성할 수 있다")
        fun createComment_onOtherUsersPost_returns201() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(authorToken)

            val commenter = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            val created = createComment(commenter.accessToken(), postId, "남의 글에 다는 댓글")

            assertThat(created.userId()).isEqualTo(commenter.user().id())
            assertThat(created.postId()).isEqualTo(postId)
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id())
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        fun createComment_unauthenticated_returns401_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            expectResultCode(
                client
                    .post()
                    .uri("/api/posts/$postId/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CommentCreateRequest("비로그인 댓글"))
                    .exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )

            assertThat(liveCommentIdsOf(postId)).isEmpty()
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun createComment_postNotFound_returns404_3() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                createCommentRequest(accessToken, NON_EXISTENT_POST_ID, "없는 글에 다는 댓글"),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("5. 소프트 삭제된 게시글이면 404-3을 반환한다")
        fun createComment_softDeletedPost_returns404_3() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            deletePost(accessToken, postId)

            expectResultCode(
                createCommentRequest(accessToken, postId, "삭제된 글에 다는 댓글"),
                HttpStatus.NOT_FOUND,
                "404-3",
            )

            assertThat(liveCommentIdsOf(postId)).isEmpty()
        }

        @Test
        @DisplayName("6. JSON 형식이 올바르지 않으면 400-1과 파싱 실패 메시지를 반환한다")
        fun createComment_malformedJson_returns400_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            client
                .post()
                .uri("/api/posts/$postId/comments")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{ \"body\": ")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    // 400-1 은 Bean Validation 실패와 JSON 파싱 실패가 공유하는 코드라 msg 까지 본다(컨벤션 6장).
                    assertThat(body.resultCode()).isEqualTo("400-1")
                    assertThat(body.msg()).isEqualTo("올바른 JSON 요청 형식이 아닙니다.")
                }
        }

        // FIXME(#6): CommentCreateRequest 에 Bean Validation 어노테이션이 없고 컨트롤러에도 @Valid 가 없어서
        // 본문 없는 댓글이 그대로 저장된다(Comment.body 는 nullable TEXT). 400-1 이 기대되는 지점이다.
        @Test
        @DisplayName("7. body가 null이어도 201-1로 빈 댓글이 만들어진다")
        fun createComment_nullBody_returns201WithNullBody() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            val created = createComment(accessToken, postId, null)

            assertThat(created.body()).isNull()
            assertThat(findComment(created.id()).body).isNull()
        }

        // FIXME(#3): CommentService.createComment 는 post.getDeletedAt() 만 확인하고 publishStatus 를 보지 않는다.
        // PostService.getPost 는 PRIVATE 게시글을 작성자 외에게 403-1 로 막는데, 댓글 작성은 뚫려 있다.
        // 게시글 본문을 볼 수 없는 사용자가 그 글에 댓글을 달 수 있는 상태다.
        @Test
        @DisplayName("8. 타인의 PRIVATE 게시글에도 댓글이 작성된다")
        fun createComment_onOthersPrivatePost_returns201() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPost(authorToken, PublishStatus.PRIVATE, PostAccessLevel.FREE)

            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            // 게시글 상세는 403-1 로 막힌다.
            expectResultCode(
                client
                    .get()
                    .uri("/api/posts/$postId")
                    .header("Authorization", bearer(otherToken))
                    .exchange(),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            // 그런데 댓글 작성은 통과한다.
            val created = createComment(otherToken, postId, "볼 수 없는 글에 다는 댓글")
            assertThat(created.postId()).isEqualTo(postId)
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id())
        }

        // FIXME(#4): 바로 위 케이스(#3)와 같은 원인. 아직 발행되지 않은(DRAFT) 게시글에도 제3자가 댓글을 달 수 있다.
        @Test
        @DisplayName("9. 타인의 DRAFT 게시글에도 댓글이 작성된다")
        fun createComment_onOthersDraftPost_returns201() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPost(authorToken, PublishStatus.DRAFT, PostAccessLevel.FREE)

            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            val created = createComment(otherToken, postId, "미발행 글에 다는 댓글")
            assertThat(created.postId()).isEqualTo(postId)
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id())
        }
    }

    @Nested
    @DisplayName("GET /api/posts/{postId}/comments — 댓글 목록 조회")
    inner class GetComments {
        @Test
        @DisplayName("1. 비로그인으로도 200-1과 작성 순 목록을 반환한다")
        fun getComments_anonymous_returns200AndCommentsInIdOrder() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(session.accessToken())

            val first = createComment(session.accessToken(), postId, "첫 댓글")
            val second = createComment(session.accessToken(), postId, "둘째 댓글")

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("댓글 목록입니다.")
                    assertThat(body.data().totalElements).isEqualTo(2L)
                    assertThat(body.data().size).isEqualTo(10) // @PageableDefault(size = 10)
                    assertThat(body.data().number).isZero()
                    // @PageableDefault(sort = "id") — 방향 지정이 없어 id 오름차순(= 작성 순)
                    assertThat(body.data().content)
                        .extracting<Long>(CommentResponse::id)
                        .containsExactly(first.id(), second.id())
                    assertThat(body.data().content)
                        .extracting<String>(CommentResponse::body)
                        .containsExactly("첫 댓글", "둘째 댓글")
                    assertThat(body.data().content[0].userId()).isEqualTo(session.user().id())
                    assertThat(body.data().content[0].nickname()).isEqualTo(session.user().nickname())
                }
        }

        @Test
        @DisplayName("2. 댓글이 없으면 200-1과 빈 페이지를 반환한다")
        fun getComments_noComments_returnsEmptyPage() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().content).isEmpty()
                    assertThat(body.data().totalElements).isZero()
                }
        }

        @Test
        @DisplayName("3. page/size 파라미터가 반영된다")
        fun getComments_withPaging_respectsPageAndSize() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            val first = createComment(accessToken, postId, "댓글1")
            val second = createComment(accessToken, postId, "댓글2")
            val third = createComment(accessToken, postId, "댓글3")

            getCommentsRequest(postId, 0, 2)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().totalElements).isEqualTo(3L)
                    assertThat(body.data().size).isEqualTo(2)
                    assertThat(body.data().number).isZero()
                    assertThat(body.data().content)
                        .extracting<Long>(CommentResponse::id)
                        .containsExactly(first.id(), second.id())
                }

            getCommentsRequest(postId, 1, 2)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().number).isEqualTo(1)
                    assertThat(body.data().content)
                        .extracting<Long>(CommentResponse::id)
                        .containsExactly(third.id())
                }
        }

        @Test
        @DisplayName("4. 삭제된 댓글은 목록에서 빠진다")
        fun getComments_excludesSoftDeletedComments() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            val kept = createComment(accessToken, postId, "남는 댓글")
            val removed = createComment(accessToken, postId, "지울 댓글")

            deleteCommentRequest(accessToken, postId, removed.id()).expectStatus().isOk()

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().totalElements).isEqualTo(1L)
                    assertThat(body.data().content)
                        .extracting<Long>(CommentResponse::id)
                        .containsExactly(kept.id())
                }
        }

        @Test
        @DisplayName("5. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun getComments_postNotFound_returns404_3() {
            expectResultCode(
                getCommentsRequest(NON_EXISTENT_POST_ID),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("6. 소프트 삭제된 게시글이면 404-3을 반환한다")
        fun getComments_softDeletedPost_returns404_3() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            createComment(accessToken, postId, "게시글이 지워질 댓글")
            deletePost(accessToken, postId)

            expectResultCode(getCommentsRequest(postId), HttpStatus.NOT_FOUND, "404-3")
        }

        // FIXME(#2): CommentService.getComments 는 publishStatus 를 보지 않는다. 게시글 상세(GET /api/posts/{id})는
        // PRIVATE 을 작성자 외에게 403-1 로 막고 SecurityConfig 도 이 경로를 비로그인에게 열어 두기 때문에,
        // 비공개 글에 달린 댓글 본문·작성자 닉네임이 익명 사용자에게 그대로 노출된다.
        @Test
        @DisplayName("7. 타인의 PRIVATE 게시글 댓글이 비로그인에게도 노출된다")
        fun getComments_onPrivatePost_isVisibleToAnonymous() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPost(author.accessToken(), PublishStatus.PRIVATE, PostAccessLevel.FREE)
            val comment = createComment(author.accessToken(), postId, "비공개 글의 댓글")

            // 비로그인 사용자에게 게시글 상세는 막혀 있다.
            expectResultCode(
                client.get().uri("/api/posts/$postId").exchange(),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            // 그런데 댓글 목록은 그대로 보인다.
            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().content)
                        .extracting<Long>(CommentResponse::id)
                        .containsExactly(comment.id())
                    assertThat(body.data().content[0].body()).isEqualTo("비공개 글의 댓글")
                    assertThat(body.data().content[0].nickname()).isEqualTo(author.user().nickname())
                }
        }
    }

    @Nested
    @DisplayName("PUT /api/posts/{postId}/comments/{id} — 댓글 수정")
    inner class UpdateComment {
        @Test
        @DisplayName("1. 성공하면 200-1을 반환하고 본문이 DB와 목록에 반영된다")
        fun updateComment_success_returns200AndPersistsNewBody() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken()
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "수정 전 본문")

            updateCommentRequest(accessToken, postId, created.id(), "수정 후 본문")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<CommentResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("댓글이 수정되었습니다.")
                    assertThat(body.data().id()).isEqualTo(created.id())
                    assertThat(body.data().postId()).isEqualTo(postId)
                    assertThat(body.data().userId()).isEqualTo(session.user().id())
                    assertThat(body.data().body()).isEqualTo("수정 후 본문")
                }

            // 수정이라는 부작용을 DB와 후속 조회로 각각 되짚는다.
            assertThat(findComment(created.id()).body).isEqualTo("수정 후 본문")

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().content)
                        .extracting<String>(CommentResponse::body)
                        .containsExactly("수정 후 본문")
                }
        }

        // FIXME(#5): CommentService.updateComment 가 flush 이전에 new CommentResponse(comment) 를 만든다.
        // @LastModifiedDate(updatedAt)는 트랜잭션 커밋 시점(flush)에 AuditingEntityListener 가 채우므로,
        // 응답에는 수정 전 값(= 생성 시각)이 실리고 DB에만 새 값이 들어간다. 클라이언트가 응답의
        // updatedAt 을 신뢰하면 "수정했는데 수정 시각이 그대로"인 화면이 나온다.
        @Test
        @DisplayName("2. 수정 응답의 updatedAt은 갱신 전 값이고, DB의 updatedAt만 갱신된다")
        fun updateComment_response_updatedAtIsStaleWhileDbIsRefreshed() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "수정 전 본문")

            val createdUpdatedAt = created.updatedAt()
            val dbUpdatedAtBefore = findComment(created.id()).updatedAt
            // DB는 나노초보다 낮은 정밀도(예: 마이크로초 반올림)로 저장하므로 완전 일치 대신 오차범위로 비교한다.
            assertThat(dbUpdatedAtBefore).isCloseTo(createdUpdatedAt, within(1, ChronoUnit.MICROS))

            val updated =
                checkNotNull(
                    updateCommentRequest(accessToken, postId, created.id(), "수정 후 본문")
                        .expectStatus()
                        .isOk()
                        .expectBody<ApiResponse<CommentResponse>>()
                        .returnResult()
                        .responseBody,
                ).data()

            // 응답 값: 갱신되지 않은 옛 updatedAt 이 그대로 실려 온다.
            // updateComment 가 findById 로 DB에서 다시 읽어온 값이라 마이크로초로 반올림돼 있다.
            assertThat(updated.updatedAt()).isCloseTo(createdUpdatedAt, within(1, ChronoUnit.MICROS))

            // DB 값: 실제로는 갱신되어 있다. 둘의 차이 자체를 고정해 둔다.
            val dbUpdatedAtAfter: LocalDateTime = findComment(created.id()).updatedAt
            assertThat(dbUpdatedAtAfter).isAfter(createdUpdatedAt)
            assertThat(dbUpdatedAtAfter).isNotEqualTo(updated.updatedAt())

            // createdAt 은 응답/DB 모두 그대로다.
            assertThat(updated.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS))
            assertThat(findComment(created.id()).createdAt).isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS))
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        fun updateComment_unauthenticated_returns401_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "원래 본문")

            expectResultCode(
                client
                    .put()
                    .uri("/api/posts/$postId/comments/${created.id()}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(CommentUpdateRequest("비로그인 수정"))
                    .exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )

            assertThat(findComment(created.id()).body).isEqualTo("원래 본문")
        }

        @Test
        @DisplayName("4. 타인의 댓글이면 403-1을 반환하고 본문이 바뀌지 않는다")
        fun updateComment_byOtherUser_returns403_1() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(authorToken)
            val created = createComment(authorToken, postId, "원래 본문")

            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                updateCommentRequest(otherToken, postId, created.id(), "남이 고친 본문"),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            assertThat(findComment(created.id()).body).isEqualTo("원래 본문")
        }

        @Test
        @DisplayName("5. 존재하지 않는 댓글이면 404-6을 반환한다")
        fun updateComment_commentNotFound_returns404_6() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            expectResultCode(
                updateCommentRequest(accessToken, postId, NON_EXISTENT_COMMENT_ID, "없는 댓글 수정"),
                HttpStatus.NOT_FOUND,
                "404-6",
            )
        }

        @Test
        @DisplayName("6. 이미 삭제된 댓글이면 404-6을 반환한다")
        fun updateComment_softDeletedComment_returns404_6() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "지울 댓글")
            deleteCommentRequest(accessToken, postId, created.id()).expectStatus().isOk()

            expectResultCode(
                updateCommentRequest(accessToken, postId, created.id(), "삭제된 댓글 수정"),
                HttpStatus.NOT_FOUND,
                "404-6",
            )

            assertThat(findComment(created.id()).body).isEqualTo("지울 댓글")
        }

        // FIXME(#1): CommentController.updateComment 가 @PathVariable Long postId 를 받기만 하고
        // CommentService.updateComment(actor, id, body) 에 넘기지 않는다. 댓글이 그 게시글에 속하는지
        // 검증하는 코드가 어디에도 없어서, 경로의 postId 가 사실상 장식이다.
        @Test
        @DisplayName("7. 댓글이 속하지 않은 다른 게시글 id를 경로에 넣어도 수정에 성공한다")
        fun updateComment_withUnrelatedPostIdInPath_stillSucceeds() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val unrelatedPostId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "원래 본문")

            updateCommentRequest(accessToken, unrelatedPostId, created.id(), "엉뚱한 경로로 고친 본문")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<CommentResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    // 응답의 postId 는 경로 값이 아니라 댓글이 실제로 속한 게시글 id 다.
                    assertThat(body.data().postId()).isEqualTo(postId)
                    assertThat(body.data().body()).isEqualTo("엉뚱한 경로로 고친 본문")
                }

            assertThat(findComment(created.id()).body).isEqualTo("엉뚱한 경로로 고친 본문")
            assertThat(liveCommentIdsOf(unrelatedPostId)).isEmpty()
        }

        // FIXME(#1): 바로 위 케이스와 같은 원인이지만 심각도가 더 크다. 경로의 postId 는 조회조차 되지 않으므로
        // 존재하지 않는 게시글 id 로도 내 댓글을 수정할 수 있다.
        // 게시글 존재 여부 검증이 댓글 수정 경로에는 아예 없다.
        @Test
        @DisplayName("8. 존재하지 않는 게시글 id를 경로에 넣어도 수정에 성공한다")
        fun updateComment_withNonExistentPostIdInPath_stillSucceeds() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "원래 본문")

            updateCommentRequest(accessToken, NON_EXISTENT_POST_ID, created.id(), "없는 게시글 경로로 고친 본문")
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<CommentResponse>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().postId()).isEqualTo(postId)
                    assertThat(body.data().body()).isEqualTo("없는 게시글 경로로 고친 본문")
                }

            assertThat(findComment(created.id()).body).isEqualTo("없는 게시글 경로로 고친 본문")
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/comments/{id} — 댓글 삭제")
    inner class DeleteComment {
        @Test
        @DisplayName("1. 성공하면 200-1을 반환하고 deletedAt이 세팅되며 목록에서 빠진다")
        fun deleteComment_success_returns200AndSoftDeletes() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "지울 댓글")

            deleteCommentRequest(accessToken, postId, created.id())
                .expectStatus()
                .isOk()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.msg()).isEqualTo("댓글이 삭제되었습니다.")
                    assertThat(body.data()).isNull()
                }

            // 하드 삭제가 아니라 소프트 삭제라는 부작용을 DB에서 직접 확인한다.
            val deleted = findComment(created.id())
            assertThat(deleted.deletedAt).isNotNull()
            assertThat(deleted.body).isEqualTo("지울 댓글")

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode()).isEqualTo("200-1")
                    assertThat(body.data().content).isEmpty()
                    assertThat(body.data().totalElements).isZero()
                }
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun deleteComment_unauthenticated_returns401_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "지켜질 댓글")

            expectResultCode(
                client.delete().uri("/api/posts/$postId/comments/${created.id()}").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )

            assertThat(findComment(created.id()).deletedAt).isNull()
        }

        @Test
        @DisplayName("3. 타인의 댓글이면 403-1을 반환하고 삭제되지 않는다")
        fun deleteComment_byOtherUser_returns403_1() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(authorToken)
            val created = createComment(authorToken, postId, "남이 못 지우는 댓글")

            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                deleteCommentRequest(otherToken, postId, created.id()),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            assertThat(findComment(created.id()).deletedAt).isNull()
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id())
        }

        @Test
        @DisplayName("4. 존재하지 않는 댓글이면 404-6을 반환한다")
        fun deleteComment_commentNotFound_returns404_6() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            expectResultCode(
                deleteCommentRequest(accessToken, postId, NON_EXISTENT_COMMENT_ID),
                HttpStatus.NOT_FOUND,
                "404-6",
            )
        }

        @Test
        @DisplayName("5. 이미 삭제된 댓글을 다시 삭제하면 404-6을 반환한다")
        fun deleteComment_alreadyDeleted_returns404_6() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "두 번 지울 댓글")

            deleteCommentRequest(accessToken, postId, created.id()).expectStatus().isOk()
            val firstDeletedAt = findComment(created.id()).deletedAt

            expectResultCode(
                deleteCommentRequest(accessToken, postId, created.id()),
                HttpStatus.NOT_FOUND,
                "404-6",
            )

            // 두 번째 요청이 deletedAt 을 덮어쓰지 않는다.
            assertThat(findComment(created.id()).deletedAt).isEqualTo(firstDeletedAt)
        }

        // FIXME(#1): CommentController.deleteComment 도 @PathVariable postId 를
        // CommentService.deleteComment(actor, id) 에 넘기지 않아 경로의 게시글 id 가 검증되지 않는다.
        @Test
        @DisplayName("6. 댓글이 속하지 않은 다른 게시글 id를 경로에 넣어도 삭제에 성공한다")
        fun deleteComment_withUnrelatedPostIdInPath_stillSucceeds() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val unrelatedPostId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "엉뚱한 경로로 지울 댓글")

            expectResultCode(
                deleteCommentRequest(accessToken, unrelatedPostId, created.id()),
                HttpStatus.OK,
                "200-1",
            )

            assertThat(findComment(created.id()).deletedAt).isNotNull()
            assertThat(liveCommentIdsOf(postId)).isEmpty()
        }

        // FIXME(#1): 바로 위 케이스와 같은 원인이지만 심각도가 더 크다. 존재하지도 않는 게시글 id 로
        // 댓글 삭제가 성사된다. 댓글 삭제 경로에는 게시글 존재 여부 검증이 아예 없다.
        @Test
        @DisplayName("7. 존재하지 않는 게시글 id를 경로에 넣어도 삭제에 성공한다")
        fun deleteComment_withNonExistentPostIdInPath_stillSucceeds() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "없는 게시글 경로로 지울 댓글")

            expectResultCode(
                deleteCommentRequest(accessToken, NON_EXISTENT_POST_ID, created.id()),
                HttpStatus.OK,
                "200-1",
            )

            assertThat(findComment(created.id()).deletedAt).isNotNull()
            assertThat(liveCommentIdsOf(postId)).isEmpty()
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    inner class Scenarios {
        @Test
        @DisplayName("1. 게시글 생성 → 댓글 작성 → 수정 → 삭제까지 목록이 매 단계 따라온다")
        fun createUpdateDeleteComment_isReflectedInList() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken()
            val postId = createPublicPost(accessToken)

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().totalElements).isZero()
                }

            val created = createComment(accessToken, postId, "처음 본문")
            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().totalElements).isEqualTo(1L)
                    assertThat(body.data().content[0].id()).isEqualTo(created.id())
                    assertThat(body.data().content[0].body()).isEqualTo("처음 본문")
                }

            expectResultCode(
                updateCommentRequest(accessToken, postId, created.id(), "고친 본문"),
                HttpStatus.OK,
                "200-1",
            )
            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().totalElements).isEqualTo(1L)
                    assertThat(body.data().content[0].body()).isEqualTo("고친 본문")
                }

            expectResultCode(
                deleteCommentRequest(accessToken, postId, created.id()),
                HttpStatus.OK,
                "200-1",
            )
            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().content).isEmpty()
                    assertThat(body.data().totalElements).isZero()
                }

            // 소프트 삭제라 행 자체는 남아 있다.
            assertThat(commentRepository.findByIdOrNull(created.id())).isNotNull()
        }

        @Test
        @DisplayName("2. A가 쓴 댓글을 B가 수정·삭제하면 403-1이지만 A 본인은 성공한다")
        fun otherUserCannotModifyComment_butOwnerCan() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            // 게시글 주인은 B, 댓글 작성자는 A — 게시글 소유권과 댓글 소유권이 다른 상황을 만든다.
            val postId = createPublicPost(otherToken)
            val created = createComment(authorToken, postId, "A의 댓글")

            expectResultCode(
                updateCommentRequest(otherToken, postId, created.id(), "B가 고친 본문"),
                HttpStatus.FORBIDDEN,
                "403-1",
            )
            expectResultCode(
                deleteCommentRequest(otherToken, postId, created.id()),
                HttpStatus.FORBIDDEN,
                "403-1",
            )

            // 게시글 주인이어도 남의 댓글은 건드릴 수 없다.
            val untouched = findComment(created.id())
            assertThat(untouched.body).isEqualTo("A의 댓글")
            assertThat(untouched.deletedAt).isNull()

            expectResultCode(
                updateCommentRequest(authorToken, postId, created.id(), "A가 고친 본문"),
                HttpStatus.OK,
                "200-1",
            )
            assertThat(findComment(created.id()).body).isEqualTo("A가 고친 본문")

            expectResultCode(
                deleteCommentRequest(authorToken, postId, created.id()),
                HttpStatus.OK,
                "200-1",
            )
            assertThat(findComment(created.id()).deletedAt).isNotNull()
        }

        // FIXME(#7): 게시글 소프트 삭제가 연관 댓글을 정리하지 않고(PostService.deletePost 는 post.softDelete()만
        // 호출한다), PUT/DELETE 는 경로의 postId 를 보지 않으므로(#1) 어떤 목록에도 나오지 않는 고아 댓글을
        // 계속 수정·삭제할 수 있다. 4개 API 중 조회·작성만 404-3 으로 막히는 반쪽 상태다.
        // #1 을 고쳐도 "게시글 삭제 시 댓글을 어떻게 할지"는 별도 결정이 필요하다.
        @Test
        @DisplayName("3. 게시글을 삭제하면 그 게시글의 댓글 조회·작성이 404-3이 되지만 댓글 행은 남고 수정도 계속 된다")
        fun deletingPost_blocksCommentApis_butKeepsCommentRows() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            val created = createComment(accessToken, postId, "게시글과 함께 묻힐 댓글")

            getCommentsRequest(postId)
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PageResult<CommentResponse>>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.data().totalElements).isEqualTo(1L)
                }

            deletePost(accessToken, postId)

            expectResultCode(getCommentsRequest(postId), HttpStatus.NOT_FOUND, "404-3")
            expectResultCode(
                createCommentRequest(accessToken, postId, "삭제된 게시글에 다는 댓글"),
                HttpStatus.NOT_FOUND,
                "404-3",
            )

            // 게시글 소프트 삭제는 댓글을 함께 정리하지 않는다. 댓글 행은 deletedAt=null 로 남아 있고,
            // 소유자 검사만 통과하면 수정/삭제도 그대로 된다(경로의 postId 를 서비스가 보지 않기 때문).
            val deletedPost = checkNotNull(postRepository.findByIdOrNull(postId))
            assertThat(deletedPost.deletedAt).isNotNull()

            val orphan = findComment(created.id())
            assertThat(orphan.deletedAt).isNull()
            assertThat(orphan.body).isEqualTo("게시글과 함께 묻힐 댓글")

            expectResultCode(
                updateCommentRequest(accessToken, postId, created.id(), "묻힌 댓글 수정"),
                HttpStatus.OK,
                "200-1",
            )
            assertThat(findComment(created.id()).body).isEqualTo("묻힌 댓글 수정")
        }
    }

    companion object {
        private const val DEFAULT_PASSWORD = "password123"
        private const val NON_EXISTENT_POST_ID = 999_999_999L
        private const val NON_EXISTENT_COMMENT_ID = 999_999_999L
    }
}
