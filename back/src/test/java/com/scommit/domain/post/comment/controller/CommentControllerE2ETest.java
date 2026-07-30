package com.scommit.domain.post.comment.controller;

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient. Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(commentdb). 다른 @SpringBootTest 와 spring.datasource.url 을
//   공유하면 같은 JVM 안에서 create-drop 이 서로의 스키마를 침범한다(컨벤션 4장).
// - 픽스처: 댓글의 선행 데이터는 User(회원가입 API) → Post(게시글 생성 API) 뿐이고,
//   소프트 삭제된 게시글/댓글도 각각의 삭제 API 로 만들 수 있다. 즉 리포지토리로 심어야만 하는
//   데이터가 하나도 없어서 CommentE2EFixtures(@TestConfiguration)를 만들지 않았다(컨벤션 5장 우선순위 1).
//   리포지토리는 "DB 반영 확인" 용도로만 주입한다.
// - @DirtiesContext / @Transactional 미사용. 테스트끼리는 각자 만든 게시글로 격리한다.

import com.scommit.domain.post.comment.dto.CommentCreateRequest;
import com.scommit.domain.post.comment.dto.CommentResponse;
import com.scommit.domain.post.comment.dto.CommentUpdateRequest;
import com.scommit.domain.post.comment.entity.Comment;
import com.scommit.domain.post.comment.repository.CommentRepository;
import com.scommit.domain.post.post.dto.PostCreateRequest;
import com.scommit.domain.post.post.dto.PostResponse;
import com.scommit.domain.post.post.entity.Post;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static com.scommit.global.e2e.E2ETestSupport.bearer;
import static com.scommit.global.e2e.E2ETestSupport.createUserAndGetAccessToken;
import static com.scommit.global.e2e.E2ETestSupport.createUserAndLogin;
import static com.scommit.global.e2e.E2ETestSupport.expectResultCode;
import static com.scommit.global.e2e.E2ETestSupport.uniqueEmail;
import static com.scommit.global.e2e.E2ETestSupport.uniqueNickname;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 이 클래스 전용 DB 이름(컨벤션 4장 표: commentdb).
        properties = "spring.datasource.url=jdbc:h2:mem:commentdb;MODE=MySQL;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
@Tag("e2e")
class CommentControllerE2ETest {

    private static final String DEFAULT_PASSWORD = "password123";
    private static final long NON_EXISTENT_POST_ID = 999_999_999L;
    private static final long NON_EXISTENT_COMMENT_ID = 999_999_999L;

    // GET /api/posts/{postId}/comments 가 Spring Data 의 Page<T> 를 그대로 직렬화하는데
    // Page 는 인터페이스라 클라이언트에서 역직렬화할 구체 타입이 없다. 검증에 쓰는 네 필드만
    // 담은 미러 레코드로 받는다(컨벤션 3장).
    private record PageResult<T>(List<T> content, long totalElements, int size, int number) {}

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @BeforeEach
    void setUpClient() {
        client = E2ETestSupport.client(port);
    }

    // ---------- 공통 헬퍼 ----------

    private static ParameterizedTypeReference<ApiResponse<CommentResponse>> commentBody() {
        return new ParameterizedTypeReference<>() {};
    }

    private static ParameterizedTypeReference<ApiResponse<PageResult<CommentResponse>>> commentPageBody() {
        return new ParameterizedTypeReference<>() {};
    }

    /** 댓글의 선행 데이터인 게시글을 실제 API 로 만든다(컨벤션 5장). */
    private Long createPost(String accessToken, PublishStatus publishStatus, PostAccessLevel accessLevel) {
        ApiResponse<PostResponse> response = client.post().uri("/api/posts")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PostCreateRequest(null, "댓글 E2E 게시글", "게시글 본문", publishStatus, accessLevel))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(new ParameterizedTypeReference<ApiResponse<PostResponse>>() {})
                .returnResult()
                .getResponseBody();
        return response.data().id();
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

    private RestTestClient.ResponseSpec createCommentRequest(String accessToken, Long postId, String body) {
        return client.post().uri("/api/posts/" + postId + "/comments")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CommentCreateRequest(body))
                .exchange();
    }

    /** 댓글 작성 성공을 전제로 응답 data 를 돌려준다. */
    private CommentResponse createComment(String accessToken, Long postId, String body) {
        return createCommentRequest(accessToken, postId, body)
                .expectStatus().isCreated()
                .expectBody(commentBody())
                .returnResult()
                .getResponseBody()
                .data();
    }

    private RestTestClient.ResponseSpec getCommentsRequest(Long postId, Integer page, Integer size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/posts/" + postId + "/comments");
        if (page != null) {
            builder.queryParam("page", page);
        }
        if (size != null) {
            builder.queryParam("size", size);
        }
        return client.get().uri(builder.build().toUriString()).exchange();
    }

    private RestTestClient.ResponseSpec getCommentsRequest(Long postId) {
        return getCommentsRequest(postId, null, null);
    }

    private RestTestClient.ResponseSpec updateCommentRequest(
            String accessToken, Long postId, Long commentId, String body) {
        return client.put().uri("/api/posts/" + postId + "/comments/" + commentId)
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CommentUpdateRequest(body))
                .exchange();
    }

    private RestTestClient.ResponseSpec deleteCommentRequest(String accessToken, Long postId, Long commentId) {
        return client.delete().uri("/api/posts/" + postId + "/comments/" + commentId)
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId).orElseThrow();
    }

    /** 목록 API 를 거치지 않고 "이 댓글이 이 게시글에 달렸는지"를 DB 로 직접 확인한다. */
    private List<Long> liveCommentIdsOf(Long postId) {
        return commentRepository.findAllByPostIdAndDeletedAtIsNull(postId, PageRequest.of(0, 50))
                .map(Comment::getId)
                .getContent();
    }

    @Nested
    @DisplayName("POST /api/posts/{postId}/comments — 댓글 작성")
    class CreateComment {

        @Test
        @DisplayName("1. 성공하면 201-1과 댓글 정보를 반환하고 DB에 반영된다")
        void createComment_success_returns201AndPersistsComment() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long userId = session.user().id();
            String nickname = session.user().nickname();
            Long postId = createPublicPost(accessToken);

            client.post().uri("/api/posts/" + postId + "/comments")
                    .header("Authorization", bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CommentCreateRequest("첫 번째 댓글"))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(commentBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("201-1");
                        assertThat(body.msg()).isEqualTo("댓글이 작성되었습니다.");
                        assertThat(body.data().id()).isNotNull();
                        assertThat(body.data().postId()).isEqualTo(postId);
                        assertThat(body.data().userId()).isEqualTo(userId);
                        assertThat(body.data().nickname()).isEqualTo(nickname);
                        assertThat(body.data().body()).isEqualTo("첫 번째 댓글");
                        assertThat(body.data().createdAt()).isNotNull();
                        assertThat(body.data().updatedAt()).isNotNull();

                        // 생성이라는 부작용 자체를 DB에서 되짚어 확인한다.
                        Comment saved = findComment(body.data().id());
                        assertThat(saved.getBody()).isEqualTo("첫 번째 댓글");
                        assertThat(saved.getDeletedAt()).isNull();
                        assertThat(saved.getPost().getId()).isEqualTo(postId);
                        assertThat(saved.getUser().getId()).isEqualTo(userId);
                    });
        }

        @Test
        @DisplayName("2. 타인의 게시글에도 댓글을 작성할 수 있다")
        void createComment_onOtherUsersPost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);

            LoginResponse commenter = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            CommentResponse created = createComment(commenter.accessToken(), postId, "남의 글에 다는 댓글");

            assertThat(created.userId()).isEqualTo(commenter.user().id());
            assertThat(created.postId()).isEqualTo(postId);
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id());
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        void createComment_unauthenticated_returns401_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            expectResultCode(
                    client.post().uri("/api/posts/" + postId + "/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new CommentCreateRequest("비로그인 댓글"))
                            .exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(liveCommentIdsOf(postId)).isEmpty();
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        void createComment_postNotFound_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    createCommentRequest(accessToken, NON_EXISTENT_POST_ID, "없는 글에 다는 댓글"),
                    HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("5. 소프트 삭제된 게시글이면 404-3을 반환한다")
        void createComment_softDeletedPost_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            deletePost(accessToken, postId);

            expectResultCode(
                    createCommentRequest(accessToken, postId, "삭제된 글에 다는 댓글"),
                    HttpStatus.NOT_FOUND, "404-3");

            assertThat(liveCommentIdsOf(postId)).isEmpty();
        }

        @Test
        @DisplayName("6. JSON 형식이 올바르지 않으면 400-1과 파싱 실패 메시지를 반환한다")
        void createComment_malformedJson_returns400_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            client.post().uri("/api/posts/" + postId + "/comments")
                    .header("Authorization", bearer(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ \"body\": ")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        // 400-1 은 Bean Validation 실패와 JSON 파싱 실패가 공유하는 코드라 msg 까지 본다(컨벤션 6장).
                        assertThat(body.resultCode()).isEqualTo("400-1");
                        assertThat(body.msg()).isEqualTo("올바른 JSON 요청 형식이 아닙니다.");
                    });
        }

        @Test
        @DisplayName("7. body가 null이어도 201-1로 빈 댓글이 만들어진다")
        // FIXME(#6): CommentCreateRequest 에 Bean Validation 어노테이션이 없고 컨트롤러에도 @Valid 가 없어서
        // 본문 없는 댓글이 그대로 저장된다(Comment.body 는 nullable TEXT). 400-1 이 기대되는 지점이다.
        void createComment_nullBody_returns201WithNullBody() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            CommentResponse created = createComment(accessToken, postId, null);

            assertThat(created.body()).isNull();
            assertThat(findComment(created.id()).getBody()).isNull();
        }

        @Test
        @DisplayName("8. 타인의 PRIVATE 게시글에도 댓글이 작성된다")
        // FIXME(#3): CommentService.createComment 는 post.getDeletedAt() 만 확인하고 publishStatus 를 보지 않는다.
        // PostService.getPost 는 PRIVATE 게시글을 작성자 외에게 403-1 로 막는데, 댓글 작성은 뚫려 있다.
        // 게시글 본문을 볼 수 없는 사용자가 그 글에 댓글을 달 수 있는 상태다.
        void createComment_onOthersPrivatePost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.PRIVATE, PostAccessLevel.FREE);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            // 게시글 상세는 403-1 로 막힌다.
            expectResultCode(
                    client.get().uri("/api/posts/" + postId)
                            .header("Authorization", bearer(otherToken))
                            .exchange(),
                    HttpStatus.FORBIDDEN, "403-1");

            // 그런데 댓글 작성은 통과한다.
            CommentResponse created = createComment(otherToken, postId, "볼 수 없는 글에 다는 댓글");
            assertThat(created.postId()).isEqualTo(postId);
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id());
        }

        @Test
        @DisplayName("9. 타인의 DRAFT 게시글에도 댓글이 작성된다")
        // FIXME(#4): 바로 위 케이스(#3)와 같은 원인. 아직 발행되지 않은(DRAFT) 게시글에도 제3자가 댓글을 달 수 있다.
        void createComment_onOthersDraftPost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.DRAFT, PostAccessLevel.FREE);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            CommentResponse created = createComment(otherToken, postId, "미발행 글에 다는 댓글");
            assertThat(created.postId()).isEqualTo(postId);
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id());
        }
    }

    @Nested
    @DisplayName("GET /api/posts/{postId}/comments — 댓글 목록 조회")
    class GetComments {

        @Test
        @DisplayName("1. 비로그인으로도 200-1과 작성 순 목록을 반환한다")
        void getComments_anonymous_returns200AndCommentsInIdOrder() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(session.accessToken());

            CommentResponse first = createComment(session.accessToken(), postId, "첫 댓글");
            CommentResponse second = createComment(session.accessToken(), postId, "둘째 댓글");

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.msg()).isEqualTo("댓글 목록입니다.");
                        assertThat(body.data().totalElements()).isEqualTo(2);
                        assertThat(body.data().size()).isEqualTo(10);   // @PageableDefault(size = 10)
                        assertThat(body.data().number()).isZero();
                        // @PageableDefault(sort = "id") — 방향 지정이 없어 id 오름차순(= 작성 순)
                        assertThat(body.data().content())
                                .extracting(CommentResponse::id)
                                .containsExactly(first.id(), second.id());
                        assertThat(body.data().content())
                                .extracting(CommentResponse::body)
                                .containsExactly("첫 댓글", "둘째 댓글");
                        assertThat(body.data().content().get(0).userId()).isEqualTo(session.user().id());
                        assertThat(body.data().content().get(0).nickname()).isEqualTo(session.user().nickname());
                    });
        }

        @Test
        @DisplayName("2. 댓글이 없으면 200-1과 빈 페이지를 반환한다")
        void getComments_noComments_returnsEmptyPage() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content()).isEmpty();
                        assertThat(body.data().totalElements()).isZero();
                    });
        }

        @Test
        @DisplayName("3. page/size 파라미터가 반영된다")
        void getComments_withPaging_respectsPageAndSize() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            CommentResponse first = createComment(accessToken, postId, "댓글1");
            CommentResponse second = createComment(accessToken, postId, "댓글2");
            CommentResponse third = createComment(accessToken, postId, "댓글3");

            getCommentsRequest(postId, 0, 2)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().totalElements()).isEqualTo(3);
                        assertThat(body.data().size()).isEqualTo(2);
                        assertThat(body.data().number()).isZero();
                        assertThat(body.data().content())
                                .extracting(CommentResponse::id)
                                .containsExactly(first.id(), second.id());
                    });

            getCommentsRequest(postId, 1, 2)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.data().number()).isEqualTo(1);
                        assertThat(body.data().content())
                                .extracting(CommentResponse::id)
                                .containsExactly(third.id());
                    });
        }

        @Test
        @DisplayName("4. 삭제된 댓글은 목록에서 빠진다")
        void getComments_excludesSoftDeletedComments() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            CommentResponse kept = createComment(accessToken, postId, "남는 댓글");
            CommentResponse removed = createComment(accessToken, postId, "지울 댓글");

            deleteCommentRequest(accessToken, postId, removed.id()).expectStatus().isOk();

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().totalElements()).isEqualTo(1);
                        assertThat(body.data().content())
                                .extracting(CommentResponse::id)
                                .containsExactly(kept.id());
                    });
        }

        @Test
        @DisplayName("5. 존재하지 않는 게시글이면 404-3을 반환한다")
        void getComments_postNotFound_returns404_3() {
            expectResultCode(
                    getCommentsRequest(NON_EXISTENT_POST_ID),
                    HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("6. 소프트 삭제된 게시글이면 404-3을 반환한다")
        void getComments_softDeletedPost_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            createComment(accessToken, postId, "게시글이 지워질 댓글");
            deletePost(accessToken, postId);

            expectResultCode(getCommentsRequest(postId), HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("7. 타인의 PRIVATE 게시글 댓글이 비로그인에게도 노출된다")
        // FIXME(#2): CommentService.getComments 는 publishStatus 를 보지 않는다. 게시글 상세(GET /api/posts/{id})는
        // PRIVATE 을 작성자 외에게 403-1 로 막고 SecurityConfig 도 이 경로를 비로그인에게 열어 두기 때문에,
        // 비공개 글에 달린 댓글 본문·작성자 닉네임이 익명 사용자에게 그대로 노출된다.
        void getComments_onPrivatePost_isVisibleToAnonymous() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(author.accessToken(), PublishStatus.PRIVATE, PostAccessLevel.FREE);
            CommentResponse comment = createComment(author.accessToken(), postId, "비공개 글의 댓글");

            // 비로그인 사용자에게 게시글 상세는 막혀 있다.
            expectResultCode(
                    client.get().uri("/api/posts/" + postId).exchange(),
                    HttpStatus.FORBIDDEN, "403-1");

            // 그런데 댓글 목록은 그대로 보인다.
            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content())
                                .extracting(CommentResponse::id)
                                .containsExactly(comment.id());
                        assertThat(body.data().content().get(0).body()).isEqualTo("비공개 글의 댓글");
                        assertThat(body.data().content().get(0).nickname()).isEqualTo(author.user().nickname());
                    });
        }
    }

    @Nested
    @DisplayName("PUT /api/posts/{postId}/comments/{id} — 댓글 수정")
    class UpdateComment {

        @Test
        @DisplayName("1. 성공하면 200-1을 반환하고 본문이 DB와 목록에 반영된다")
        void updateComment_success_returns200AndPersistsNewBody() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "수정 전 본문");

            updateCommentRequest(accessToken, postId, created.id(), "수정 후 본문")
                    .expectStatus().isOk()
                    .expectBody(commentBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.msg()).isEqualTo("댓글이 수정되었습니다.");
                        assertThat(body.data().id()).isEqualTo(created.id());
                        assertThat(body.data().postId()).isEqualTo(postId);
                        assertThat(body.data().userId()).isEqualTo(session.user().id());
                        assertThat(body.data().body()).isEqualTo("수정 후 본문");
                    });

            // 수정이라는 부작용을 DB와 후속 조회로 각각 되짚는다.
            assertThat(findComment(created.id()).getBody()).isEqualTo("수정 후 본문");

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> assertThat(body.data().content())
                            .extracting(CommentResponse::body)
                            .containsExactly("수정 후 본문"));
        }

        @Test
        @DisplayName("2. 수정 응답의 updatedAt은 갱신 전 값이고, DB의 updatedAt만 갱신된다")
        // FIXME(#5): CommentService.updateComment 가 flush 이전에 new CommentResponse(comment) 를 만든다.
        // @LastModifiedDate(updatedAt)는 트랜잭션 커밋 시점(flush)에 AuditingEntityListener 가 채우므로,
        // 응답에는 수정 전 값(= 생성 시각)이 실리고 DB에만 새 값이 들어간다. 클라이언트가 응답의
        // updatedAt 을 신뢰하면 "수정했는데 수정 시각이 그대로"인 화면이 나온다.
        void updateComment_response_updatedAtIsStaleWhileDbIsRefreshed() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "수정 전 본문");

            LocalDateTime createdUpdatedAt = created.updatedAt();
            LocalDateTime dbUpdatedAtBefore = findComment(created.id()).getUpdatedAt();
            assertThat(dbUpdatedAtBefore).isEqualTo(createdUpdatedAt);

            CommentResponse updated = updateCommentRequest(accessToken, postId, created.id(), "수정 후 본문")
                    .expectStatus().isOk()
                    .expectBody(commentBody())
                    .returnResult()
                    .getResponseBody()
                    .data();

            // 응답 값: 갱신되지 않은 옛 updatedAt 이 그대로 실려 온다.
            assertThat(updated.updatedAt()).isEqualTo(createdUpdatedAt);

            // DB 값: 실제로는 갱신되어 있다. 둘의 차이 자체를 고정해 둔다.
            LocalDateTime dbUpdatedAtAfter = findComment(created.id()).getUpdatedAt();
            assertThat(dbUpdatedAtAfter).isAfter(createdUpdatedAt);
            assertThat(dbUpdatedAtAfter).isNotEqualTo(updated.updatedAt());

            // createdAt 은 응답/DB 모두 그대로다.
            assertThat(updated.createdAt()).isEqualTo(created.createdAt());
            assertThat(findComment(created.id()).getCreatedAt()).isEqualTo(created.createdAt());
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        void updateComment_unauthenticated_returns401_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "원래 본문");

            expectResultCode(
                    client.put().uri("/api/posts/" + postId + "/comments/" + created.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new CommentUpdateRequest("비로그인 수정"))
                            .exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(findComment(created.id()).getBody()).isEqualTo("원래 본문");
        }

        @Test
        @DisplayName("4. 타인의 댓글이면 403-1을 반환하고 본문이 바뀌지 않는다")
        void updateComment_byOtherUser_returns403_1() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);
            CommentResponse created = createComment(authorToken, postId, "원래 본문");

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    updateCommentRequest(otherToken, postId, created.id(), "남이 고친 본문"),
                    HttpStatus.FORBIDDEN, "403-1");

            assertThat(findComment(created.id()).getBody()).isEqualTo("원래 본문");
        }

        @Test
        @DisplayName("5. 존재하지 않는 댓글이면 404-6을 반환한다")
        void updateComment_commentNotFound_returns404_6() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            expectResultCode(
                    updateCommentRequest(accessToken, postId, NON_EXISTENT_COMMENT_ID, "없는 댓글 수정"),
                    HttpStatus.NOT_FOUND, "404-6");
        }

        @Test
        @DisplayName("6. 이미 삭제된 댓글이면 404-6을 반환한다")
        void updateComment_softDeletedComment_returns404_6() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "지울 댓글");
            deleteCommentRequest(accessToken, postId, created.id()).expectStatus().isOk();

            expectResultCode(
                    updateCommentRequest(accessToken, postId, created.id(), "삭제된 댓글 수정"),
                    HttpStatus.NOT_FOUND, "404-6");

            assertThat(findComment(created.id()).getBody()).isEqualTo("지울 댓글");
        }

        @Test
        @DisplayName("7. 댓글이 속하지 않은 다른 게시글 id를 경로에 넣어도 수정에 성공한다")
        // FIXME(#1): CommentController.updateComment 가 @PathVariable Long postId 를 받기만 하고
        // CommentService.updateComment(actor, id, body) 에 넘기지 않는다. 댓글이 그 게시글에 속하는지
        // 검증하는 코드가 어디에도 없어서, 경로의 postId 가 사실상 장식이다.
        void updateComment_withUnrelatedPostIdInPath_stillSucceeds() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            Long unrelatedPostId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "원래 본문");

            updateCommentRequest(accessToken, unrelatedPostId, created.id(), "엉뚱한 경로로 고친 본문")
                    .expectStatus().isOk()
                    .expectBody(commentBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        // 응답의 postId 는 경로 값이 아니라 댓글이 실제로 속한 게시글 id 다.
                        assertThat(body.data().postId()).isEqualTo(postId);
                        assertThat(body.data().body()).isEqualTo("엉뚱한 경로로 고친 본문");
                    });

            assertThat(findComment(created.id()).getBody()).isEqualTo("엉뚱한 경로로 고친 본문");
            assertThat(liveCommentIdsOf(unrelatedPostId)).isEmpty();
        }

        @Test
        @DisplayName("8. 존재하지 않는 게시글 id를 경로에 넣어도 수정에 성공한다")
        // FIXME(#1): 바로 위 케이스와 같은 원인이지만 심각도가 더 크다. 경로의 postId 는 조회조차 되지 않으므로
        // 존재하지 않는 게시글 id 로도 내 댓글을 수정할 수 있다.
        // 게시글 존재 여부 검증이 댓글 수정 경로에는 아예 없다.
        void updateComment_withNonExistentPostIdInPath_stillSucceeds() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "원래 본문");

            updateCommentRequest(accessToken, NON_EXISTENT_POST_ID, created.id(), "없는 게시글 경로로 고친 본문")
                    .expectStatus().isOk()
                    .expectBody(commentBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().postId()).isEqualTo(postId);
                        assertThat(body.data().body()).isEqualTo("없는 게시글 경로로 고친 본문");
                    });

            assertThat(findComment(created.id()).getBody()).isEqualTo("없는 게시글 경로로 고친 본문");
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/comments/{id} — 댓글 삭제")
    class DeleteComment {

        @Test
        @DisplayName("1. 성공하면 200-1을 반환하고 deletedAt이 세팅되며 목록에서 빠진다")
        void deleteComment_success_returns200AndSoftDeletes() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "지울 댓글");

            deleteCommentRequest(accessToken, postId, created.id())
                    .expectStatus().isOk()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.msg()).isEqualTo("댓글이 삭제되었습니다.");
                        assertThat(body.data()).isNull();
                    });

            // 하드 삭제가 아니라 소프트 삭제라는 부작용을 DB에서 직접 확인한다.
            Comment deleted = findComment(created.id());
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.getBody()).isEqualTo("지울 댓글");

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content()).isEmpty();
                        assertThat(body.data().totalElements()).isZero();
                    });
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void deleteComment_unauthenticated_returns401_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "지켜질 댓글");

            expectResultCode(
                    client.delete().uri("/api/posts/" + postId + "/comments/" + created.id()).exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(findComment(created.id()).getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("3. 타인의 댓글이면 403-1을 반환하고 삭제되지 않는다")
        void deleteComment_byOtherUser_returns403_1() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);
            CommentResponse created = createComment(authorToken, postId, "남이 못 지우는 댓글");

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    deleteCommentRequest(otherToken, postId, created.id()),
                    HttpStatus.FORBIDDEN, "403-1");

            assertThat(findComment(created.id()).getDeletedAt()).isNull();
            assertThat(liveCommentIdsOf(postId)).containsExactly(created.id());
        }

        @Test
        @DisplayName("4. 존재하지 않는 댓글이면 404-6을 반환한다")
        void deleteComment_commentNotFound_returns404_6() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            expectResultCode(
                    deleteCommentRequest(accessToken, postId, NON_EXISTENT_COMMENT_ID),
                    HttpStatus.NOT_FOUND, "404-6");
        }

        @Test
        @DisplayName("5. 이미 삭제된 댓글을 다시 삭제하면 404-6을 반환한다")
        void deleteComment_alreadyDeleted_returns404_6() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "두 번 지울 댓글");

            deleteCommentRequest(accessToken, postId, created.id()).expectStatus().isOk();
            LocalDateTime firstDeletedAt = findComment(created.id()).getDeletedAt();

            expectResultCode(
                    deleteCommentRequest(accessToken, postId, created.id()),
                    HttpStatus.NOT_FOUND, "404-6");

            // 두 번째 요청이 deletedAt 을 덮어쓰지 않는다.
            assertThat(findComment(created.id()).getDeletedAt()).isEqualTo(firstDeletedAt);
        }

        @Test
        @DisplayName("6. 댓글이 속하지 않은 다른 게시글 id를 경로에 넣어도 삭제에 성공한다")
        // FIXME(#1): CommentController.deleteComment 도 @PathVariable postId 를
        // CommentService.deleteComment(actor, id) 에 넘기지 않아 경로의 게시글 id 가 검증되지 않는다.
        void deleteComment_withUnrelatedPostIdInPath_stillSucceeds() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            Long unrelatedPostId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "엉뚱한 경로로 지울 댓글");

            expectResultCode(
                    deleteCommentRequest(accessToken, unrelatedPostId, created.id()),
                    HttpStatus.OK, "200-1");

            assertThat(findComment(created.id()).getDeletedAt()).isNotNull();
            assertThat(liveCommentIdsOf(postId)).isEmpty();
        }

        @Test
        @DisplayName("7. 존재하지 않는 게시글 id를 경로에 넣어도 삭제에 성공한다")
        // FIXME(#1): 바로 위 케이스와 같은 원인이지만 심각도가 더 크다. 존재하지도 않는 게시글 id 로
        // 댓글 삭제가 성사된다. 댓글 삭제 경로에는 게시글 존재 여부 검증이 아예 없다.
        void deleteComment_withNonExistentPostIdInPath_stillSucceeds() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "없는 게시글 경로로 지울 댓글");

            expectResultCode(
                    deleteCommentRequest(accessToken, NON_EXISTENT_POST_ID, created.id()),
                    HttpStatus.OK, "200-1");

            assertThat(findComment(created.id()).getDeletedAt()).isNotNull();
            assertThat(liveCommentIdsOf(postId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    class Scenarios {

        @Test
        @DisplayName("1. 게시글 생성 → 댓글 작성 → 수정 → 삭제까지 목록이 매 단계 따라온다")
        void createUpdateDeleteComment_isReflectedInList() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> assertThat(body.data().totalElements()).isZero());

            CommentResponse created = createComment(accessToken, postId, "처음 본문");
            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.data().totalElements()).isEqualTo(1);
                        assertThat(body.data().content().get(0).id()).isEqualTo(created.id());
                        assertThat(body.data().content().get(0).body()).isEqualTo("처음 본문");
                    });

            expectResultCode(
                    updateCommentRequest(accessToken, postId, created.id(), "고친 본문"),
                    HttpStatus.OK, "200-1");
            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.data().totalElements()).isEqualTo(1);
                        assertThat(body.data().content().get(0).body()).isEqualTo("고친 본문");
                    });

            expectResultCode(
                    deleteCommentRequest(accessToken, postId, created.id()),
                    HttpStatus.OK, "200-1");
            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> {
                        assertThat(body.data().content()).isEmpty();
                        assertThat(body.data().totalElements()).isZero();
                    });

            // 소프트 삭제라 행 자체는 남아 있다.
            assertThat(commentRepository.findById(created.id())).isPresent();
        }

        @Test
        @DisplayName("2. A가 쓴 댓글을 B가 수정·삭제하면 403-1이지만 A 본인은 성공한다")
        void otherUserCannotModifyComment_butOwnerCan() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            // 게시글 주인은 B, 댓글 작성자는 A — 게시글 소유권과 댓글 소유권이 다른 상황을 만든다.
            Long postId = createPublicPost(otherToken);
            CommentResponse created = createComment(authorToken, postId, "A의 댓글");

            expectResultCode(
                    updateCommentRequest(otherToken, postId, created.id(), "B가 고친 본문"),
                    HttpStatus.FORBIDDEN, "403-1");
            expectResultCode(
                    deleteCommentRequest(otherToken, postId, created.id()),
                    HttpStatus.FORBIDDEN, "403-1");

            // 게시글 주인이어도 남의 댓글은 건드릴 수 없다.
            Comment untouched = findComment(created.id());
            assertThat(untouched.getBody()).isEqualTo("A의 댓글");
            assertThat(untouched.getDeletedAt()).isNull();

            expectResultCode(
                    updateCommentRequest(authorToken, postId, created.id(), "A가 고친 본문"),
                    HttpStatus.OK, "200-1");
            assertThat(findComment(created.id()).getBody()).isEqualTo("A가 고친 본문");

            expectResultCode(
                    deleteCommentRequest(authorToken, postId, created.id()),
                    HttpStatus.OK, "200-1");
            assertThat(findComment(created.id()).getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("3. 게시글을 삭제하면 그 게시글의 댓글 조회·작성이 404-3이 되지만 댓글 행은 남고 수정도 계속 된다")
        // FIXME(#7): 게시글 소프트 삭제가 연관 댓글을 정리하지 않고(PostService.deletePost 는 post.softDelete()만
        // 호출한다), PUT/DELETE 는 경로의 postId 를 보지 않으므로(#1) 어떤 목록에도 나오지 않는 고아 댓글을
        // 계속 수정·삭제할 수 있다. 4개 API 중 조회·작성만 404-3 으로 막히는 반쪽 상태다.
        // #1 을 고쳐도 "게시글 삭제 시 댓글을 어떻게 할지"는 별도 결정이 필요하다.
        void deletingPost_blocksCommentApis_butKeepsCommentRows() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            CommentResponse created = createComment(accessToken, postId, "게시글과 함께 묻힐 댓글");

            getCommentsRequest(postId)
                    .expectStatus().isOk()
                    .expectBody(commentPageBody())
                    .value(body -> assertThat(body.data().totalElements()).isEqualTo(1));

            deletePost(accessToken, postId);

            expectResultCode(getCommentsRequest(postId), HttpStatus.NOT_FOUND, "404-3");
            expectResultCode(
                    createCommentRequest(accessToken, postId, "삭제된 게시글에 다는 댓글"),
                    HttpStatus.NOT_FOUND, "404-3");

            // 게시글 소프트 삭제는 댓글을 함께 정리하지 않는다. 댓글 행은 deletedAt=null 로 남아 있고,
            // 소유자 검사만 통과하면 수정/삭제도 그대로 된다(경로의 postId 를 서비스가 보지 않기 때문).
            Post deletedPost = postRepository.findById(postId).orElseThrow();
            assertThat(deletedPost.getDeletedAt()).isNotNull();

            Comment orphan = findComment(created.id());
            assertThat(orphan.getDeletedAt()).isNull();
            assertThat(orphan.getBody()).isEqualTo("게시글과 함께 묻힐 댓글");

            expectResultCode(
                    updateCommentRequest(accessToken, postId, created.id(), "묻힌 댓글 수정"),
                    HttpStatus.OK, "200-1");
            assertThat(findComment(created.id()).getBody()).isEqualTo("묻힌 댓글 수정");
        }
    }
}
