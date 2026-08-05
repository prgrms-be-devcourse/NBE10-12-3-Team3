package com.scommit.domain.post.like.controller

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient. Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(likedb). 다른 @SpringBootTest 와 spring.datasource.url 을
//   공유하면 같은 JVM 안에서 create-drop 이 서로의 스키마를 침범한다(컨벤션 4장).
// - 픽스처: 좋아요의 선행 데이터는 User(회원가입 API) → Post(게시글 생성 API) 뿐이고,
//   소프트 삭제된 게시글도 게시글 삭제 API 로 만들 수 있다. 리포지토리로만 심을 수 있는 데이터가
//   없어서 LikeE2EFixtures(@TestConfiguration)를 만들지 않았다(컨벤션 5장 우선순위 1).
//   리포지토리는 "DB 반영 확인" 용도로만 주입한다.
// - @DirtiesContext / @Transactional 미사용. 테스트끼리는 각자 만든 유저·게시글로 격리한다.

import com.scommit.domain.post.like.entity.Like
import com.scommit.domain.post.like.repository.LikeRepository
import com.scommit.domain.post.post.dto.PostCreateRequest
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.dto.LoginResponse
import com.scommit.global.e2e.ApiResponse
import com.scommit.global.e2e.E2ETestSupport
import com.scommit.global.e2e.E2ETestSupport.bearer
import com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken
import com.scommit.global.e2e.E2ETestSupport.createUserAndLogin
import com.scommit.global.e2e.E2ETestSupport.expectResultCode
import com.scommit.global.e2e.E2ETestSupport.uniqueEmail
import com.scommit.global.e2e.E2ETestSupport.uniqueNickname
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // 이 클래스 전용 DB 이름(컨벤션 4장 표: likedb).
    properties = ["spring.datasource.url=jdbc:h2:mem:likedb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
class LikeControllerE2ETest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @Autowired
    private lateinit var likeRepository: LikeRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    // ---------- 공통 헬퍼 ----------

    /** 좋아요의 선행 데이터인 게시글을 실제 API 로 만든다(컨벤션 5장). */
    private fun createPost(
        accessToken: String,
        publishStatus: PublishStatus,
        accessLevel: PostAccessLevel,
    ): Long =
        checkNotNull(
            checkNotNull(
                client
                    .post()
                    .uri("/api/posts")
                    .header("Authorization", bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PostCreateRequest(null, "좋아요 E2E 게시글", "게시글 본문", publishStatus, accessLevel))
                    .exchange()
                    .expectStatus()
                    .isCreated()
                    .expectBody<ApiResponse<PostResponse>>()
                    .returnResult()
                    .responseBody,
            ).data.id,
        )

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

    private fun createLikeRequest(
        accessToken: String,
        postId: Long,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/posts/$postId/likes")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** 좋아요 추가 성공을 전제로 한다. */
    private fun createLike(
        accessToken: String,
        postId: Long,
    ) {
        createLikeRequest(accessToken, postId).expectStatus().isCreated()
    }

    private fun deleteLikeRequest(
        accessToken: String,
        postId: Long,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri("/api/posts/$postId/likes")
            .header("Authorization", bearer(accessToken))
            .exchange()

    /** 응답에 드러나지 않는 부작용(행 생성/삭제)을 DB에서 직접 되짚기 위한 조회. */
    private fun findLike(
        postId: Long,
        userId: Long,
    ): Like? = likeRepository.findByPostIdAndUserId(postId, userId)

    private fun likeCountOf(postId: Long): Long = checkNotNull(postRepository.findByIdOrNull(postId)).likeCount

    /** 게시글 상세 API 로 likeCount / isLiked 가 사용자에게 어떻게 보이는지 확인한다. */
    private fun getPost(
        accessToken: String,
        postId: Long,
    ): PostResponse =
        checkNotNull(
            client
                .get()
                .uri("/api/posts/$postId")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<PostResponse>>()
                .returnResult()
                .responseBody,
        ).data

    @Nested
    @DisplayName("POST /api/posts/{postId}/likes — 좋아요 추가")
    inner class CreateLike {
        @Test
        @DisplayName("1. 성공하면 201-1을 반환하고 Like 행과 likeCount에 반영된다")
        fun createLike_success_returns201AndPersistsLike() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val userId = session.user.id
            val postId = createPublicPost(accessToken)

            assertThat(likeCountOf(postId)).isZero()

            client
                .post()
                .uri("/api/posts/$postId/likes")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody<ApiResponse<Void>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("201-1")
                    assertThat(body.msg).isEqualTo("좋아요가 추가되었습니다.")
                    assertThat(body.data).isNull()
                }

            // 생성이라는 부작용 자체를 DB에서 되짚어 확인한다.
            val saved = checkNotNull(findLike(postId, userId))
            assertThat(saved.post.id).isEqualTo(postId)
            assertThat(saved.user.id).isEqualTo(userId)
            assertThat(likeCountOf(postId)).isEqualTo(1L)

            // 사용자에게 보이는 값도 같이 확인한다.
            val post = getPost(accessToken, postId)
            assertThat(post.likeCount).isEqualTo(1)
            assertThat(post.isLiked).isTrue()
        }

        @Test
        @DisplayName("2. 타인의 게시글에도 좋아요할 수 있다")
        fun createLike_onOtherUsersPost_returns201() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(authorToken)

            val liker = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(createLikeRequest(liker.accessToken, postId), HttpStatus.CREATED, "201-1")

            assertThat(findLike(postId, liker.user.id)).isNotNull()
            assertThat(likeCountOf(postId)).isEqualTo(1L)

            // 좋아요를 누른 사람에게만 isLiked 가 true 다.
            assertThat(getPost(liker.accessToken, postId).isLiked).isTrue()
            assertThat(getPost(authorToken, postId).isLiked).isFalse()
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        fun createLike_unauthenticated_returns401_1() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)

            expectResultCode(
                client.post().uri("/api/posts/$postId/likes").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )

            assertThat(likeCountOf(postId)).isZero()
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun createLike_postNotFound_returns404_3() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                createLikeRequest(accessToken, NON_EXISTENT_POST_ID),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        @Test
        @DisplayName("5. 소프트 삭제된 게시글이면 404-3을 반환한다")
        fun createLike_softDeletedPost_returns404_3() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val postId = createPublicPost(accessToken)
            deletePost(accessToken, postId)

            expectResultCode(createLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3")

            assertThat(findLike(postId, session.user.id)).isNull()
        }

        @Test
        @DisplayName("6. 이미 좋아요한 게시글이면 409-7을 반환하고 likeCount가 늘지 않는다")
        fun createLike_duplicate_returns409_7() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val postId = createPublicPost(accessToken)

            createLike(accessToken, postId)

            client
                .post()
                .uri("/api/posts/$postId/likes")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody<ApiResponse<Void>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("409-7")
                    assertThat(body.msg).isEqualTo("이미 좋아요를 누른 게시글입니다.")
                }

            // 유니크 제약(uk_post_likes_post_user)이 중복 행을 막고, 카운트도 두 번 오르지 않는다.
            assertThat(likeCountOf(postId)).isEqualTo(1L)
            assertThat(likeRepository.existsByPostIdAndUserId(postId, session.user.id)).isTrue()
        }

        @Test
        @DisplayName("7. 타인의 PRIVATE 게시글에는 좋아요할 수 없고 403-1을 반환한다")
        fun createLike_onOthersPrivatePost_returns403_1() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPost(authorToken, PublishStatus.PRIVATE, PostAccessLevel.FREE)

            val other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(createLikeRequest(other.accessToken, postId), HttpStatus.FORBIDDEN, "403-1")

            assertThat(findLike(postId, other.user.id)).isNull()
            assertThat(likeCountOf(postId)).isZero()
        }

        @Test
        @DisplayName("8. 타인의 DRAFT 게시글에는 좋아요할 수 없고 403-1을 반환한다")
        fun createLike_onOthersDraftPost_returns403_1() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPost(authorToken, PublishStatus.DRAFT, PostAccessLevel.FREE)

            val other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(createLikeRequest(other.accessToken, postId), HttpStatus.FORBIDDEN, "403-1")

            assertThat(findLike(postId, other.user.id)).isNull()
            assertThat(likeCountOf(postId)).isZero()
        }

        @Test
        @DisplayName("9. 작성자는 자신의 PRIVATE/DRAFT 게시글에도 좋아요할 수 있다")
        fun createLike_onOwnNonPublicPost_returns201() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPost(session.accessToken, PublishStatus.PRIVATE, PostAccessLevel.FREE)

            expectResultCode(createLikeRequest(session.accessToken, postId), HttpStatus.CREATED, "201-1")

            assertThat(findLike(postId, session.user.id)).isNotNull()
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/likes — 좋아요 취소")
    inner class DeleteLike {
        @Test
        @DisplayName("1. 200-1을 반환하고 likeCount가 줄면서 Like 행도 실제로 삭제된다")
        fun deleteLike_returns200AndPersistsDeletion() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val userId = session.user.id
            val postId = createPublicPost(accessToken)

            createLike(accessToken, postId)
            assertThat(likeCountOf(postId)).isEqualTo(1L)

            client
                .delete()
                .uri("/api/posts/$postId/likes")
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody<ApiResponse<Void>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("200-1")
                    assertThat(body.msg).isEqualTo("좋아요가 취소되었습니다.")
                    assertThat(body.data).isNull()
                }

            // 카운트도 줄고
            assertThat(likeCountOf(postId)).isZero()
            // 행도 실제로 지워진다.
            assertThat(findLike(postId, userId)).isNull()

            val post = getPost(accessToken, postId)
            assertThat(post.likeCount).isZero()
            assertThat(post.isLiked).isFalse()
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun deleteLike_unauthenticated_returns401_1() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(session.accessToken)
            createLike(session.accessToken, postId)

            expectResultCode(
                client.delete().uri("/api/posts/$postId/likes").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )

            assertThat(likeCountOf(postId)).isEqualTo(1L)
            assertThat(findLike(postId, session.user.id)).isNotNull()
        }

        @Test
        @DisplayName("3. 존재하지 않는 게시글이면 404-3을 반환한다")
        fun deleteLike_postNotFound_returns404_3() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            expectResultCode(
                deleteLikeRequest(accessToken, NON_EXISTENT_POST_ID),
                HttpStatus.NOT_FOUND,
                "404-3",
            )
        }

        // 게시글 존재 확인이 좋아요 조회보다 먼저라 404-9(좋아요 없음)가 아니라 404-3이 나온다.
        @Test
        @DisplayName("4. 소프트 삭제된 게시글이면 404-3을 반환한다")
        fun deleteLike_softDeletedPost_returns404_3() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(accessToken)
            createLike(accessToken, postId)
            deletePost(accessToken, postId)

            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3")
        }

        @Test
        @DisplayName("5. 좋아요한 적이 없으면 404-9를 반환한다")
        fun deleteLike_notLiked_returns404_9() {
            val authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(authorToken)

            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            client
                .delete()
                .uri("/api/posts/$postId/likes")
                .header("Authorization", bearer(otherToken))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody<ApiResponse<Void>>()
                .value { body ->
                    checkNotNull(body)
                    assertThat(body.resultCode).isEqualTo("404-9")
                    assertThat(body.msg).isEqualTo("좋아요 기록이 없습니다.")
                }
        }

        @Test
        @DisplayName("6. 타인의 좋아요는 취소되지 않는다")
        fun deleteLike_doesNotTouchOtherUsersLike() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)
            createLike(author.accessToken, postId)

            val otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            // 좋아요를 누른 적 없는 사용자의 취소 요청은 자기 기록만 찾으므로 404-9다.
            expectResultCode(deleteLikeRequest(otherToken, postId), HttpStatus.NOT_FOUND, "404-9")

            assertThat(findLike(postId, author.user.id)).isNotNull()
            assertThat(likeCountOf(postId)).isEqualTo(1L)
        }

        @Test
        @DisplayName("7. 같은 좋아요를 두 번 취소하면 첫 번째만 200-1이고 두 번째는 404-9다")
        fun deleteLike_twice_secondReturns404_9() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val postId = createPublicPost(accessToken)
            createLike(accessToken, postId)

            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.OK, "200-1")
            assertThat(likeCountOf(postId)).isZero()

            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-9")
            assertThat(likeCountOf(postId)).isZero()
            assertThat(findLike(postId, session.user.id)).isNull()
        }

        @Test
        @DisplayName("8. 취소한 뒤 다시 좋아요하면 201-1로 재좋아요가 된다")
        fun createLike_afterDelete_returns201_1() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val postId = createPublicPost(accessToken)

            createLike(accessToken, postId)
            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.OK, "200-1")

            expectResultCode(createLikeRequest(accessToken, postId), HttpStatus.CREATED, "201-1")

            val post = getPost(accessToken, postId)
            assertThat(post.likeCount).isEqualTo(1)
            assertThat(post.isLiked).isTrue()
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    inner class Scenarios {
        @Test
        @DisplayName("1. 두 사용자가 좋아요하면 likeCount가 2가 되고 isLiked는 각자 기준으로 나온다")
        fun twoUsersLike_likeCountIsTwoAndIsLikedIsPerUser() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)

            createLike(author.accessToken, postId)
            assertThat(likeCountOf(postId)).isEqualTo(1L)

            createLike(other.accessToken, postId)
            assertThat(likeCountOf(postId)).isEqualTo(2L)

            assertThat(getPost(author.accessToken, postId).likeCount).isEqualTo(2)
            assertThat(getPost(author.accessToken, postId).isLiked).isTrue()
            assertThat(getPost(other.accessToken, postId).isLiked).isTrue()

            val bystanderToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val seenByBystander = getPost(bystanderToken, postId)
            assertThat(seenByBystander.likeCount).isEqualTo(2)
            assertThat(seenByBystander.isLiked).isFalse()
        }

        @Test
        @DisplayName("2. 한 사람이 취소해도 다른 사람의 좋아요는 남고 likeCount만 1 줄어든다")
        fun oneUserCancels_othersLikeRemains() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)

            createLike(author.accessToken, postId)
            createLike(other.accessToken, postId)
            assertThat(likeCountOf(postId)).isEqualTo(2L)

            expectResultCode(deleteLikeRequest(other.accessToken, postId), HttpStatus.OK, "200-1")

            assertThat(likeCountOf(postId)).isEqualTo(1L)
            assertThat(findLike(postId, author.user.id)).isNotNull()
            assertThat(getPost(author.accessToken, postId).isLiked).isTrue()
        }

        @Test
        @DisplayName("3. 게시글을 삭제하면 좋아요 추가·취소가 모두 404-3이 되고 연관 Like 행도 정리된다")
        fun deletingPost_blocksLikeApis_andCleansUpLikeRows() {
            val session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val accessToken = session.accessToken
            val postId = createPublicPost(accessToken)
            createLike(accessToken, postId)

            deletePost(accessToken, postId)

            expectResultCode(createLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3")
            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3")

            assertThat(checkNotNull(postRepository.findByIdOrNull(postId)).deletedAt).isNotNull()
            assertThat(findLike(postId, session.user.id)).isNull()
        }
    }

    companion object {
        private const val DEFAULT_PASSWORD = "password123"
        private const val NON_EXISTENT_POST_ID = 999_999_999L
    }
}
