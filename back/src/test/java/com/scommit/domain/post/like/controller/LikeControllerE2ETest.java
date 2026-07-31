package com.scommit.domain.post.like.controller;

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient. Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(likedb). 다른 @SpringBootTest 와 spring.datasource.url 을
//   공유하면 같은 JVM 안에서 create-drop 이 서로의 스키마를 침범한다(컨벤션 4장).
// - 픽스처: 좋아요의 선행 데이터는 User(회원가입 API) → Post(게시글 생성 API) 뿐이고,
//   소프트 삭제된 게시글도 게시글 삭제 API 로 만들 수 있다. 리포지토리로만 심을 수 있는 데이터가
//   없어서 LikeE2EFixtures(@TestConfiguration)를 만들지 않았다(컨벤션 5장 우선순위 1).
//   리포지토리는 "DB 반영 확인" 용도로만 주입한다.
// - @DirtiesContext / @Transactional 미사용. 테스트끼리는 각자 만든 유저·게시글로 격리한다.

import com.scommit.domain.post.like.entity.Like;
import com.scommit.domain.post.like.repository.LikeRepository;
import com.scommit.domain.post.post.dto.PostCreateRequest;
import com.scommit.domain.post.post.dto.PostResponse;
import com.scommit.domain.post.post.entity.PostAccessLevel;
import com.scommit.domain.post.post.entity.PublishStatus;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.user.user.dto.LoginResponse;
import com.scommit.global.e2e.ApiResponse;
import com.scommit.global.e2e.E2ETestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.Optional;

import static com.scommit.global.e2e.E2ETestSupport.bearer;
import static com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken;
import static com.scommit.global.e2e.E2ETestSupport.createUserAndLogin;
import static com.scommit.global.e2e.E2ETestSupport.expectResultCode;
import static com.scommit.global.e2e.E2ETestSupport.uniqueEmail;
import static com.scommit.global.e2e.E2ETestSupport.uniqueNickname;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 이 클래스 전용 DB 이름(컨벤션 4장 표: likedb).
        properties = "spring.datasource.url=jdbc:h2:mem:likedb;MODE=MySQL;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
@Tag("e2e")
class LikeControllerE2ETest {

    private static final String DEFAULT_PASSWORD = "password123";
    private static final long NON_EXISTENT_POST_ID = 999_999_999L;

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private PostRepository postRepository;

    @BeforeEach
    void setUpClient() {
        client = E2ETestSupport.client(port);
    }

    // ---------- 공통 헬퍼 ----------

    private static ParameterizedTypeReference<ApiResponse<PostResponse>> postBody() {
        return new ParameterizedTypeReference<>() {};
    }

    /** 좋아요의 선행 데이터인 게시글을 실제 API 로 만든다(컨벤션 5장). */
    private Long createPost(String accessToken, PublishStatus publishStatus, PostAccessLevel accessLevel) {
        return client.post().uri("/api/posts")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PostCreateRequest(null, "좋아요 E2E 게시글", "게시글 본문", publishStatus, accessLevel))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(postBody())
                .returnResult()
                .getResponseBody()
                .data()
                .id();
    }

    private Long createPublicPost(String accessToken) {
        return createPost(accessToken, PublishStatus.PUBLIC, PostAccessLevel.FREE);
    }

    private void deletePost(String accessToken, Long postId) {
        client.delete().uri("/api/posts/" + postId)
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus().isOk();
    }

    private RestTestClient.ResponseSpec createLikeRequest(String accessToken, Long postId) {
        return client.post().uri("/api/posts/" + postId + "/likes")
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    /** 좋아요 추가 성공을 전제로 한다. */
    private void createLike(String accessToken, Long postId) {
        createLikeRequest(accessToken, postId).expectStatus().isCreated();
    }

    private RestTestClient.ResponseSpec deleteLikeRequest(String accessToken, Long postId) {
        return client.delete().uri("/api/posts/" + postId + "/likes")
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    /** 응답에 드러나지 않는 부작용(행 생성/삭제)을 DB에서 직접 되짚기 위한 조회. */
    private Optional<Like> findLike(Long postId, Long userId) {
        return likeRepository.findByPostIdAndUserId(postId, userId);
    }

    private long likeCountOf(Long postId) {
        return postRepository.findById(postId).orElseThrow().getLikeCount();
    }

    /** 게시글 상세 API 로 likeCount / isLiked 가 사용자에게 어떻게 보이는지 확인한다. */
    private PostResponse getPost(String accessToken, Long postId) {
        return client.get().uri("/api/posts/" + postId)
                .header("Authorization", bearer(accessToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(postBody())
                .returnResult()
                .getResponseBody()
                .data();
    }

    @Nested
    @DisplayName("POST /api/posts/{postId}/likes — 좋아요 추가")
    class CreateLike {

        @Test
        @DisplayName("1. 성공하면 201-1을 반환하고 Like 행과 likeCount에 반영된다")
        void createLike_success_returns201AndPersistsLike() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long userId = session.user().id();
            Long postId = createPublicPost(accessToken);

            assertThat(likeCountOf(postId)).isZero();

            client.post().uri("/api/posts/" + postId + "/likes")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("201-1");
                        assertThat(body.msg()).isEqualTo("좋아요가 추가되었습니다.");
                        assertThat(body.data()).isNull();
                    });

            // 생성이라는 부작용 자체를 DB에서 되짚어 확인한다.
            Like saved = findLike(postId, userId).orElseThrow();
            assertThat(saved.getPost().getId()).isEqualTo(postId);
            assertThat(saved.getUser().getId()).isEqualTo(userId);
            assertThat(likeCountOf(postId)).isEqualTo(1);

            // 사용자에게 보이는 값도 같이 확인한다.
            PostResponse post = getPost(accessToken, postId);
            assertThat(post.likeCount()).isEqualTo(1);
            assertThat(post.isLiked()).isTrue();
        }

        @Test
        @DisplayName("2. 타인의 게시글에도 좋아요할 수 있다")
        void createLike_onOtherUsersPost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);

            LoginResponse liker = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(createLikeRequest(liker.accessToken(), postId), HttpStatus.CREATED, "201-1");

            assertThat(findLike(postId, liker.user().id())).isPresent();
            assertThat(likeCountOf(postId)).isEqualTo(1);

            // 좋아요를 누른 사람에게만 isLiked 가 true 다.
            assertThat(getPost(liker.accessToken(), postId).isLiked()).isTrue();
            assertThat(getPost(authorToken, postId).isLiked()).isFalse();
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        void createLike_unauthenticated_returns401_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            expectResultCode(
                    client.post().uri("/api/posts/" + postId + "/likes").exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(likeCountOf(postId)).isZero();
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        void createLike_postNotFound_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    createLikeRequest(accessToken, NON_EXISTENT_POST_ID),
                    HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("5. 소프트 삭제된 게시글이면 404-3을 반환한다")
        void createLike_softDeletedPost_returns404_3() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);
            deletePost(accessToken, postId);

            expectResultCode(createLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3");

            assertThat(findLike(postId, session.user().id())).isEmpty();
        }

        @Test
        @DisplayName("6. 이미 좋아요한 게시글이면 409-7을 반환하고 likeCount가 늘지 않는다")
        void createLike_duplicate_returns409_7() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);

            createLike(accessToken, postId);

            client.post().uri("/api/posts/" + postId + "/likes")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("409-7");
                        assertThat(body.msg()).isEqualTo("이미 좋아요를 누른 게시글입니다.");
                    });

            // 유니크 제약(uk_post_likes_post_user)이 중복 행을 막고, 카운트도 두 번 오르지 않는다.
            assertThat(likeCountOf(postId)).isEqualTo(1);
            assertThat(likeRepository.existsByPostIdAndUserId(postId, session.user().id())).isTrue();
        }

        @Test
        @DisplayName("7. 타인의 PRIVATE 게시글에도 좋아요가 된다")
        // FIXME(#3): LikeService.createLike 는 findByIdAndDeletedAtIsNull 만 보고 publishStatus 를 검사하지 않는다.
        // PostService.getPost 는 PRIVATE 게시글을 작성자 외에게 403-1 로 막는데, 좋아요는 뚫려 있다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #3
        void createLike_onOthersPrivatePost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.PRIVATE, PostAccessLevel.FREE);

            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            // 게시글 상세는 403-1 로 막힌다.
            expectResultCode(
                    client.get().uri("/api/posts/" + postId)
                            .header("Authorization", bearer(other.accessToken()))
                            .exchange(),
                    HttpStatus.FORBIDDEN, "403-1");

            // 그런데 좋아요는 통과한다.
            expectResultCode(createLikeRequest(other.accessToken(), postId), HttpStatus.CREATED, "201-1");

            assertThat(findLike(postId, other.user().id())).isPresent();
            assertThat(likeCountOf(postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("8. 타인의 DRAFT 게시글에도 좋아요가 된다")
        // FIXME(#3): 바로 위 케이스와 같은 원인. 아직 발행되지 않은(DRAFT) 게시글에도 제3자가 좋아요를 누를 수 있다.
        void createLike_onOthersDraftPost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.DRAFT, PostAccessLevel.FREE);

            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(createLikeRequest(other.accessToken(), postId), HttpStatus.CREATED, "201-1");

            assertThat(findLike(postId, other.user().id())).isPresent();
            assertThat(likeCountOf(postId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/likes — 좋아요 취소")
    class DeleteLike {

        @Test
        @DisplayName("1. 200-1을 반환하고 likeCount는 줄지만 Like 행은 삭제되지 않는다")
        // FIXME(#1): LikeService.deleteLike 가 likeRepository.delete(like) 로 삭제를 예약한 뒤
        // postRepository.decreaseLikeCount(postId) 를 호출하는데, 이 @Modifying 쿼리가
        // clearAutomatically = true / flushAutomatically = false 다. 대상 테이블이 서로 달라
        // (post_likes ↔ posts) Hibernate 의 auto-flush 가 걸리지 않고, 쿼리 직후 영속성 컨텍스트가
        // clear 되면서 예약된 DELETE 가 통째로 버려진다. 결과적으로 카운트만 줄고 행은 남는다.
        // 2026-07-27 커밋 51dbb50("count 로직 DB로 마이그레이션")에서 post.decreaseLikeCount() 를
        // postRepository.decreaseLikeCount() 로 바꾸며 생긴 회귀다 — 그 이전 버전은 정상 동작했다.
        // 순수 JDBC 조회와 Hibernate SQL 로그(DELETE 문 미발행)로 확인했다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #1
        void deleteLike_returns200ButLikeRowSurvives() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long userId = session.user().id();
            Long postId = createPublicPost(accessToken);

            createLike(accessToken, postId);
            assertThat(likeCountOf(postId)).isEqualTo(1);

            client.delete().uri("/api/posts/" + postId + "/likes")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.msg()).isEqualTo("좋아요가 취소되었습니다.");
                        assertThat(body.data()).isNull();
                    });

            // 카운트는 줄었는데
            assertThat(likeCountOf(postId)).isZero();
            // 행은 그대로 남아 있다 — 이 어긋남 자체가 버그다.
            assertThat(findLike(postId, userId)).isPresent();

            // 사용자 화면에도 "좋아요 0개인데 내가 누른 상태"로 보인다.
            PostResponse post = getPost(accessToken, postId);
            assertThat(post.likeCount()).isZero();
            assertThat(post.isLiked()).isTrue();
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void deleteLike_unauthenticated_returns401_1() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(session.accessToken());
            createLike(session.accessToken(), postId);

            expectResultCode(
                    client.delete().uri("/api/posts/" + postId + "/likes").exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(likeCountOf(postId)).isEqualTo(1);
            assertThat(findLike(postId, session.user().id())).isPresent();
        }

        @Test
        @DisplayName("3. 존재하지 않는 게시글이면 404-3을 반환한다")
        void deleteLike_postNotFound_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    deleteLikeRequest(accessToken, NON_EXISTENT_POST_ID),
                    HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("4. 소프트 삭제된 게시글이면 404-3을 반환한다")
        // 게시글 존재 확인이 좋아요 조회보다 먼저라 404-9(좋아요 없음)가 아니라 404-3이 나온다.
        void deleteLike_softDeletedPost_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            createLike(accessToken, postId);
            deletePost(accessToken, postId);

            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("5. 좋아요한 적이 없으면 404-9를 반환한다")
        void deleteLike_notLiked_returns404_9() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            client.delete().uri("/api/posts/" + postId + "/likes")
                    .header("Authorization", bearer(otherToken))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("404-9");
                        assertThat(body.msg()).isEqualTo("좋아요 기록이 없습니다.");
                    });
        }

        @Test
        @DisplayName("6. 타인의 좋아요는 취소되지 않는다")
        void deleteLike_doesNotTouchOtherUsersLike() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(author.accessToken());
            createLike(author.accessToken(), postId);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            // 좋아요를 누른 적 없는 사용자의 취소 요청은 자기 기록만 찾으므로 404-9다.
            expectResultCode(deleteLikeRequest(otherToken, postId), HttpStatus.NOT_FOUND, "404-9");

            assertThat(findLike(postId, author.user().id())).isPresent();
            assertThat(likeCountOf(postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("7. 같은 좋아요를 두 번 취소해도 200-1이고 likeCount는 0 아래로 내려가지 않는다")
        // FIXME(#1): 첫 취소에서 행이 실제로 지워지지 않기 때문에(#1) 두 번째 취소도 대상을 찾아
        // 성공한다. 정상 구현이라면 두 번째는 404-9여야 한다. likeCount 가 음수가 되지 않는 것은
        // decreaseLikeCount 의 CASE WHEN 가드 덕분이지, 취소가 멱등해서가 아니다.
        void deleteLike_twice_returns200BothTimes() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);
            createLike(accessToken, postId);

            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.OK, "200-1");
            assertThat(likeCountOf(postId)).isZero();

            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.OK, "200-1");
            assertThat(likeCountOf(postId)).isZero();
            assertThat(findLike(postId, session.user().id())).isPresent();
        }

        @Test
        @DisplayName("8. 취소한 뒤 다시 좋아요하면 409-7이라 재좋아요가 불가능하다")
        // FIXME(#1): 취소가 행을 지우지 않으므로(#1) 유니크 제약(uk_post_likes_post_user)이 그대로 살아 있어
        // 같은 게시글에 다시 좋아요를 누를 수 없다. 사용자 입장에서는 "좋아요를 껐다 켤 수 없는" 상태이고,
        // likeCount 는 0인데 isLiked 는 true 로 남아 화면과 데이터가 어긋난다.
        void createLike_afterDelete_returns409_7() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);

            createLike(accessToken, postId);
            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.OK, "200-1");

            expectResultCode(createLikeRequest(accessToken, postId), HttpStatus.CONFLICT, "409-7");

            PostResponse post = getPost(accessToken, postId);
            assertThat(post.likeCount()).isZero();
            assertThat(post.isLiked()).isTrue();
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    class Scenarios {

        @Test
        @DisplayName("1. 두 사용자가 좋아요하면 likeCount가 2가 되고 isLiked는 각자 기준으로 나온다")
        void twoUsersLike_likeCountIsTwoAndIsLikedIsPerUser() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(author.accessToken());

            createLike(author.accessToken(), postId);
            assertThat(likeCountOf(postId)).isEqualTo(1);

            createLike(other.accessToken(), postId);
            assertThat(likeCountOf(postId)).isEqualTo(2);

            assertThat(getPost(author.accessToken(), postId).likeCount()).isEqualTo(2);
            assertThat(getPost(author.accessToken(), postId).isLiked()).isTrue();
            assertThat(getPost(other.accessToken(), postId).isLiked()).isTrue();

            String bystanderToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            PostResponse seenByBystander = getPost(bystanderToken, postId);
            assertThat(seenByBystander.likeCount()).isEqualTo(2);
            assertThat(seenByBystander.isLiked()).isFalse();
        }

        @Test
        @DisplayName("2. 한 사람이 취소해도 다른 사람의 좋아요는 남고 likeCount만 1 줄어든다")
        void oneUserCancels_othersLikeRemains() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(author.accessToken());

            createLike(author.accessToken(), postId);
            createLike(other.accessToken(), postId);
            assertThat(likeCountOf(postId)).isEqualTo(2);

            expectResultCode(deleteLikeRequest(other.accessToken(), postId), HttpStatus.OK, "200-1");

            assertThat(likeCountOf(postId)).isEqualTo(1);
            assertThat(findLike(postId, author.user().id())).isPresent();
            assertThat(getPost(author.accessToken(), postId).isLiked()).isTrue();
        }

        @Test
        @DisplayName("3. 게시글을 삭제하면 좋아요 추가·취소가 모두 404-3이 되지만 Like 행은 남는다")
        // FIXME(#2): PostService.deletePost 는 post.softDelete() 만 호출하고 연관 Like 를 정리하지 않는다.
        // 어떤 화면에도 나오지 않는 고아 좋아요 행이 남고, 게시글이 복구되면(BaseEntity.restore) 그대로 되살아난다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #2
        void deletingPost_blocksLikeApis_butKeepsLikeRows() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);
            createLike(accessToken, postId);

            deletePost(accessToken, postId);

            expectResultCode(createLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3");
            expectResultCode(deleteLikeRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3");

            assertThat(postRepository.findById(postId).orElseThrow().getDeletedAt()).isNotNull();
            assertThat(findLike(postId, session.user().id())).isPresent();
        }
    }
}
