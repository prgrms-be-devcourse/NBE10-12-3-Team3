package com.scommit.domain.post.bookmark.controller;

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT) + RestTestClient. Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(bookmarkdb). 다른 @SpringBootTest 와 spring.datasource.url 을
//   공유하면 같은 JVM 안에서 create-drop 이 서로의 스키마를 침범한다(컨벤션 4장).
// - 픽스처: 북마크의 선행 데이터는 User(회원가입 API) → Post(게시글 생성 API) 뿐이라
//   BookmarkE2EFixtures(@TestConfiguration)를 만들지 않았다(컨벤션 5장 우선순위 1).
//   리포지토리는 "DB 반영 확인" 용도로만 주입한다.
// - 목록 API(GET /api/bookmarks/me)는 "내 북마크"만 돌려주므로, 테스트마다 새 유저를 만들면
//   건수 어서션이 다른 테스트에 오염되지 않는다(컨벤션 9장).
// - @DirtiesContext / @Transactional 미사용.

import com.scommit.domain.post.bookmark.entity.Bookmark;
import com.scommit.domain.post.bookmark.repository.BookmarkRepository;
import com.scommit.domain.post.post.dto.PostCreateRequest;
import com.scommit.domain.post.post.dto.PostListResponse;
import com.scommit.domain.post.post.dto.PostResponse;
import com.scommit.domain.post.post.entity.PostAccessLevel;
import com.scommit.domain.post.post.entity.PublishStatus;
import com.scommit.domain.post.post.repository.PostRepository;
import com.scommit.domain.user.user.dto.LoginResponse;
import com.scommit.global.dto.PageResponse;
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
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 이 클래스 전용 DB 이름(컨벤션 4장 표: bookmarkdb).
        properties = "spring.datasource.url=jdbc:h2:mem:bookmarkdb;MODE=MySQL;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
@Tag("e2e")
class BookmarkControllerE2ETest {

    private static final String DEFAULT_PASSWORD = "password123";
    private static final long NON_EXISTENT_POST_ID = 999_999_999L;

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @Autowired
    private BookmarkRepository bookmarkRepository;

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

    // 목록 API 는 PageResponse(레코드)를 그대로 돌려주므로 미러 레코드가 필요 없다.
    // Page 인터페이스를 반환하는 API 와 달리 역직렬화할 구체 타입이 이미 있다(컨벤션 3장).
    private static ParameterizedTypeReference<ApiResponse<PageResponse<PostListResponse>>> bookmarkPageBody() {
        return new ParameterizedTypeReference<>() {};
    }

    /** 북마크의 선행 데이터인 게시글을 실제 API 로 만든다(컨벤션 5장). */
    private Long createPost(String accessToken, PublishStatus publishStatus, PostAccessLevel accessLevel) {
        return client.post().uri("/api/posts")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PostCreateRequest(null, "북마크 E2E 게시글", "게시글 본문", publishStatus, accessLevel))
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

    private RestTestClient.ResponseSpec createBookmarkRequest(String accessToken, Long postId) {
        return client.post().uri("/api/posts/" + postId + "/bookmarks")
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    /** 북마크 추가 성공을 전제로 한다. */
    private void createBookmark(String accessToken, Long postId) {
        createBookmarkRequest(accessToken, postId).expectStatus().isCreated();
    }

    private RestTestClient.ResponseSpec deleteBookmarkRequest(String accessToken, Long postId) {
        return client.delete().uri("/api/posts/" + postId + "/bookmarks")
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    private RestTestClient.ResponseSpec getMyBookmarksRequest(String accessToken, String query) {
        return client.get().uri("/api/bookmarks/me" + query)
                .header("Authorization", bearer(accessToken))
                .exchange();
    }

    private RestTestClient.ResponseSpec getMyBookmarksRequest(String accessToken) {
        return getMyBookmarksRequest(accessToken, "");
    }

    /** 목록 조회 성공을 전제로 data 를 돌려준다. */
    private PageResponse<PostListResponse> getMyBookmarks(String accessToken) {
        return getMyBookmarksRequest(accessToken)
                .expectStatus().isOk()
                .expectBody(bookmarkPageBody())
                .returnResult()
                .getResponseBody()
                .data();
    }

    /** 응답에 드러나지 않는 부작용(행 생성/삭제)을 DB에서 직접 되짚기 위한 조회. */
    private Optional<Bookmark> findBookmark(Long postId, Long userId) {
        return bookmarkRepository.findByPostIdAndUserId(postId, userId);
    }

    private long bookmarkCountOf(Long postId) {
        return postRepository.findById(postId).orElseThrow().getBookmarkCount();
    }

    @Nested
    @DisplayName("POST /api/posts/{postId}/bookmarks — 북마크 추가")
    class CreateBookmark {

        @Test
        @DisplayName("1. 성공하면 201-1을 반환하고 Bookmark 행·bookmarkCount·목록에 반영된다")
        void createBookmark_success_returns201AndPersistsBookmark() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long userId = session.user().id();
            Long postId = createPublicPost(accessToken);

            assertThat(bookmarkCountOf(postId)).isZero();

            client.post().uri("/api/posts/" + postId + "/bookmarks")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("201-1");
                        assertThat(body.msg()).isEqualTo("북마크가 추가되었습니다.");
                        assertThat(body.data()).isNull();
                    });

            // 생성이라는 부작용 자체를 DB에서 되짚어 확인한다.
            Bookmark saved = findBookmark(postId, userId).orElseThrow();
            assertThat(saved.getPost().getId()).isEqualTo(postId);
            assertThat(saved.getUser().getId()).isEqualTo(userId);
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);

            // 후속 조회로도 확인한다.
            PageResponse<PostListResponse> myBookmarks = getMyBookmarks(accessToken);
            assertThat(myBookmarks.totalElements()).isEqualTo(1);
            assertThat(myBookmarks.content())
                    .extracting(PostListResponse::id)
                    .containsExactly(postId);
            assertThat(myBookmarks.content().get(0).isBookmarked()).isTrue();
            assertThat(myBookmarks.content().get(0).bookmarkCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("2. 타인의 게시글에도 북마크할 수 있다")
        void createBookmark_onOtherUsersPost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);

            LoginResponse bookmarker = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(createBookmarkRequest(bookmarker.accessToken(), postId), HttpStatus.CREATED, "201-1");

            assertThat(findBookmark(postId, bookmarker.user().id())).isPresent();
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);

            // 북마크는 누른 사람의 목록에만 들어간다.
            assertThat(getMyBookmarks(bookmarker.accessToken()).content())
                    .extracting(PostListResponse::id)
                    .containsExactly(postId);
            assertThat(getMyBookmarks(authorToken).content()).isEmpty();
        }

        @Test
        @DisplayName("3. 미인증이면 401-1을 반환한다")
        void createBookmark_unauthenticated_returns401_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            expectResultCode(
                    client.post().uri("/api/posts/" + postId + "/bookmarks").exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(bookmarkCountOf(postId)).isZero();
        }

        @Test
        @DisplayName("4. 존재하지 않는 게시글이면 404-3을 반환한다")
        void createBookmark_postNotFound_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    createBookmarkRequest(accessToken, NON_EXISTENT_POST_ID),
                    HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("5. 소프트 삭제된 게시글이면 404-3을 반환한다")
        void createBookmark_softDeletedPost_returns404_3() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);
            deletePost(accessToken, postId);

            expectResultCode(createBookmarkRequest(accessToken, postId), HttpStatus.NOT_FOUND, "404-3");

            assertThat(findBookmark(postId, session.user().id())).isEmpty();
        }

        @Test
        @DisplayName("6. 이미 북마크한 게시글이면 409-8을 반환하고 bookmarkCount가 늘지 않는다")
        void createBookmark_duplicate_returns409_8() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);

            createBookmark(accessToken, postId);

            client.post().uri("/api/posts/" + postId + "/bookmarks")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("409-8");
                        assertThat(body.msg()).isEqualTo("이미 북마크한 게시글입니다.");
                    });

            // 유니크 제약(uk_post_bookmarks_post_user)이 중복 행을 막고, 목록에도 하나만 남는다.
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);
            assertThat(getMyBookmarks(accessToken).totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("7. 타인의 PRIVATE 게시글에도 북마크가 된다")
        // FIXME(#3): BookmarkService.createBookmark 는 findByIdAndDeletedAtIsNull 만 보고 publishStatus 를
        // 검사하지 않는다. PostService.getPost 는 PRIVATE 게시글을 작성자 외에게 403-1 로 막는데 북마크는 뚫려 있다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #3
        void createBookmark_onOthersPrivatePost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.PRIVATE, PostAccessLevel.FREE);

            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            // 게시글 상세는 403-1 로 막힌다.
            expectResultCode(
                    client.get().uri("/api/posts/" + postId)
                            .header("Authorization", bearer(other.accessToken()))
                            .exchange(),
                    HttpStatus.FORBIDDEN, "403-1");

            // 그런데 북마크는 통과한다.
            expectResultCode(createBookmarkRequest(other.accessToken(), postId), HttpStatus.CREATED, "201-1");

            assertThat(findBookmark(postId, other.user().id())).isPresent();
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("8. 타인의 DRAFT 게시글에도 북마크가 된다")
        // FIXME(#3): 바로 위 케이스와 같은 원인. 아직 발행되지 않은(DRAFT) 게시글도 제3자가 북마크할 수 있다.
        void createBookmark_onOthersDraftPost_returns201() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.DRAFT, PostAccessLevel.FREE);

            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(createBookmarkRequest(other.accessToken(), postId), HttpStatus.CREATED, "201-1");

            assertThat(findBookmark(postId, other.user().id())).isPresent();
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GET /api/bookmarks/me — 내 북마크 목록 조회")
    class GetMyBookmarks {

        @Test
        @DisplayName("1. 성공하면 200-1과 최근 북마크 순 목록을 반환한다")
        void getMyBookmarks_success_returns200AndBookmarksInDescOrder() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long first = createPublicPost(accessToken);
            Long second = createPublicPost(accessToken);

            createBookmark(accessToken, first);
            createBookmark(accessToken, second);

            getMyBookmarksRequest(accessToken)
                    .expectStatus().isOk()
                    .expectBody(bookmarkPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.msg()).isEqualTo("북마크한 게시글 목록입니다.");
                        assertThat(body.data().totalElements()).isEqualTo(2);
                        assertThat(body.data().pageSize()).isEqualTo(10);   // @PageableDefault(size = 10)
                        assertThat(body.data().pageNumber()).isZero();
                        assertThat(body.data().totalPages()).isEqualTo(1);
                        assertThat(body.data().isLast()).isTrue();
                        // @PageableDefault(sort = "id", direction = DESC) — Bookmark.id 내림차순(= 최근 북마크 순)
                        assertThat(body.data().content())
                                .extracting(PostListResponse::id)
                                .containsExactly(second, first);

                        PostListResponse head = body.data().content().get(0);
                        assertThat(head.userId()).isEqualTo(session.user().id());
                        assertThat(head.nickname()).isEqualTo(session.user().nickname());
                        assertThat(head.title()).isEqualTo("북마크 E2E 게시글");
                        assertThat(head.publishStatus()).isEqualTo(PublishStatus.PUBLIC);
                        assertThat(head.isBookmarked()).isTrue();
                        assertThat(head.isLiked()).isFalse();
                    });
        }

        @Test
        @DisplayName("2. 북마크가 없으면 200-1과 빈 페이지를 반환한다")
        void getMyBookmarks_noBookmarks_returnsEmptyPage() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            getMyBookmarksRequest(accessToken)
                    .expectStatus().isOk()
                    .expectBody(bookmarkPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().content()).isEmpty();
                        assertThat(body.data().totalElements()).isZero();
                        assertThat(body.data().totalPages()).isZero();
                        assertThat(body.data().isLast()).isTrue();
                    });
        }

        @Test
        @DisplayName("3. page/size 파라미터가 반영된다")
        void getMyBookmarks_withPaging_respectsPageAndSize() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long first = createPublicPost(accessToken);
            Long second = createPublicPost(accessToken);
            Long third = createPublicPost(accessToken);

            createBookmark(accessToken, first);
            createBookmark(accessToken, second);
            createBookmark(accessToken, third);

            getMyBookmarksRequest(accessToken, "?page=0&size=2")
                    .expectStatus().isOk()
                    .expectBody(bookmarkPageBody())
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.data().totalElements()).isEqualTo(3);
                        assertThat(body.data().pageSize()).isEqualTo(2);
                        assertThat(body.data().totalPages()).isEqualTo(2);
                        assertThat(body.data().isLast()).isFalse();
                        assertThat(body.data().content())
                                .extracting(PostListResponse::id)
                                .containsExactly(third, second);
                    });

            getMyBookmarksRequest(accessToken, "?page=1&size=2")
                    .expectStatus().isOk()
                    .expectBody(bookmarkPageBody())
                    .value(body -> {
                        assertThat(body.data().pageNumber()).isEqualTo(1);
                        assertThat(body.data().isLast()).isTrue();
                        assertThat(body.data().content())
                                .extracting(PostListResponse::id)
                                .containsExactly(first);
                    });
        }

        @Test
        @DisplayName("4. 게시글이 삭제되면 목록에서 빠지지만 Bookmark 행은 남는다")
        void getMyBookmarks_excludesDeletedPosts() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            LoginResponse reader = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long kept = createPublicPost(author.accessToken());
            Long removed = createPublicPost(author.accessToken());

            createBookmark(reader.accessToken(), kept);
            createBookmark(reader.accessToken(), removed);
            assertThat(getMyBookmarks(reader.accessToken()).totalElements()).isEqualTo(2);

            deletePost(author.accessToken(), removed);

            // 목록 쿼리가 findByUserIdAndPostDeletedAtIsNull 이라 삭제된 게시글은 빠진다.
            PageResponse<PostListResponse> afterDelete = getMyBookmarks(reader.accessToken());
            assertThat(afterDelete.totalElements()).isEqualTo(1);
            assertThat(afterDelete.content())
                    .extracting(PostListResponse::id)
                    .containsExactly(kept);

            // 다만 북마크 행 자체는 정리되지 않는다(#2).
            assertThat(findBookmark(removed, reader.user().id())).isPresent();
        }

        @Test
        @DisplayName("5. 미인증이면 401-1을 반환한다")
        void getMyBookmarks_unauthenticated_returns401_1() {
            expectResultCode(
                    client.get().uri("/api/bookmarks/me").exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");
        }

        @Test
        @DisplayName("6. 타인의 북마크는 내 목록에 나오지 않는다")
        void getMyBookmarks_isScopedToActor() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long mine = createPublicPost(authorToken);
            Long theirs = createPublicPost(authorToken);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            createBookmark(authorToken, mine);
            createBookmark(otherToken, theirs);

            assertThat(getMyBookmarks(authorToken).content())
                    .extracting(PostListResponse::id)
                    .containsExactly(mine);
            assertThat(getMyBookmarks(otherToken).content())
                    .extracting(PostListResponse::id)
                    .containsExactly(theirs);
        }

        @Test
        @DisplayName("7. 타인의 PRIVATE 게시글도 내 북마크 목록에 그대로 노출된다")
        // FIXME(#4): 목록 쿼리 findByUserIdAndPostDeletedAtIsNull 이 publishStatus 를 보지 않는다.
        // #3 으로 비공개 글을 북마크할 수 있고, 그 뒤에는 작성자가 상세를 403-1 로 막아 둔 글의 제목·작성자·
        // 조회수·좋아요 수가 북마크 목록을 통해 계속 보인다(본문은 PostListResponse 에 없어 노출되지 않는다).
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #4
        void getMyBookmarks_showsOthersPrivatePost() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPost(authorToken, PublishStatus.PRIVATE, PostAccessLevel.FREE);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            createBookmark(otherToken, postId);

            // 상세 조회는 여전히 막혀 있는데
            expectResultCode(
                    client.get().uri("/api/posts/" + postId)
                            .header("Authorization", bearer(otherToken))
                            .exchange(),
                    HttpStatus.FORBIDDEN, "403-1");

            // 북마크 목록에는 그대로 실려 온다.
            PageResponse<PostListResponse> myBookmarks = getMyBookmarks(otherToken);
            assertThat(myBookmarks.content())
                    .extracting(PostListResponse::id)
                    .containsExactly(postId);
            assertThat(myBookmarks.content().get(0).publishStatus()).isEqualTo(PublishStatus.PRIVATE);
            assertThat(myBookmarks.content().get(0).title()).isEqualTo("북마크 E2E 게시글");
        }

        @Test
        @DisplayName("8. 존재하지 않는 정렬 필드를 넘기면 500-1을 반환한다")
        // FIXME(#5): @PageableDefault 로 들어온 Sort 의 프로퍼티를 검증하는 곳이 없어서
        // Spring Data 의 PropertyReferenceException 이 GlobalExceptionHandler 의 Exception 핸들러까지 올라간다.
        // 클라이언트 입력이 원인인데 500-1(서버 오류)로 응답한다. 400-1 이 기대되는 지점이다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #5
        void getMyBookmarks_invalidSortProperty_returns500_1() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);
            createBookmark(accessToken, postId);

            expectResultCode(
                    getMyBookmarksRequest(accessToken, "?sort=notAField,desc"),
                    HttpStatus.INTERNAL_SERVER_ERROR, "500-1");
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/bookmarks — 북마크 취소")
    class DeleteBookmark {

        @Test
        @DisplayName("1. 200-1을 반환하고 bookmarkCount는 줄지만 Bookmark 행과 목록 노출은 그대로다")
        // FIXME(#1): BookmarkService.deleteBookmark 가 bookmarkRepository.delete(bookmark) 로 삭제를 예약한 뒤
        // postRepository.decreaseBookmarkCount(postId) 를 호출하는데, 이 @Modifying 쿼리가
        // clearAutomatically = true / flushAutomatically = false 다. 대상 테이블이 서로 달라
        // (post_bookmarks ↔ posts) Hibernate 의 auto-flush 가 걸리지 않고, 쿼리 직후 영속성 컨텍스트가
        // clear 되면서 예약된 DELETE 가 통째로 버려진다. LikeService.deleteLike 도 같은 모양이다.
        // 2026-07-27 커밋 c2360f8("count 로직 DB로 마이그레이션")에서 post.decreaseBookmarkCount() 를
        // postRepository.decreaseBookmarkCount() 로 바꾸며 생긴 회귀다 — 그 이전 버전은 정상 동작했다.
        // 순수 JDBC 조회와 Hibernate SQL 로그(DELETE 문 미발행)로 확인했다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #1
        void deleteBookmark_returns200ButBookmarkRowSurvives() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long userId = session.user().id();
            Long postId = createPublicPost(accessToken);

            createBookmark(accessToken, postId);
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);

            client.delete().uri("/api/posts/" + postId + "/bookmarks")
                    .header("Authorization", bearer(accessToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("200-1");
                        assertThat(body.msg()).isEqualTo("북마크가 취소되었습니다.");
                        assertThat(body.data()).isNull();
                    });

            // 카운트는 줄었는데
            assertThat(bookmarkCountOf(postId)).isZero();
            // 행은 그대로 남아 있고
            assertThat(findBookmark(postId, userId)).isPresent();
            // 취소한 게시글이 내 북마크 목록에 계속 보인다 — 사용자에게 바로 드러나는 증상이다.
            PageResponse<PostListResponse> myBookmarks = getMyBookmarks(accessToken);
            assertThat(myBookmarks.totalElements()).isEqualTo(1);
            assertThat(myBookmarks.content())
                    .extracting(PostListResponse::id)
                    .containsExactly(postId);
            assertThat(myBookmarks.content().get(0).bookmarkCount()).isZero();
            assertThat(myBookmarks.content().get(0).isBookmarked()).isTrue();
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        void deleteBookmark_unauthenticated_returns401_1() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(session.accessToken());
            createBookmark(session.accessToken(), postId);

            expectResultCode(
                    client.delete().uri("/api/posts/" + postId + "/bookmarks").exchange(),
                    HttpStatus.UNAUTHORIZED, "401-1");

            assertThat(bookmarkCountOf(postId)).isEqualTo(1);
            assertThat(findBookmark(postId, session.user().id())).isPresent();
        }

        @Test
        @DisplayName("3. 존재하지 않는 게시글이면 404-3을 반환한다")
        void deleteBookmark_postNotFound_returns404_3() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            expectResultCode(
                    deleteBookmarkRequest(accessToken, NON_EXISTENT_POST_ID),
                    HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("4. 소프트 삭제된 게시글이면 404-3을 반환한다")
        // 게시글 존재 확인이 북마크 조회보다 먼저라 404-8(북마크 없음)이 아니라 404-3이 나온다.
        void deleteBookmark_softDeletedPost_returns404_3() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String readerToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(author.accessToken());
            createBookmark(readerToken, postId);

            deletePost(author.accessToken(), postId);

            expectResultCode(deleteBookmarkRequest(readerToken, postId), HttpStatus.NOT_FOUND, "404-3");
        }

        @Test
        @DisplayName("5. 북마크한 적이 없으면 404-8을 반환한다")
        void deleteBookmark_notBookmarked_returns404_8() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(authorToken);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            client.delete().uri("/api/posts/" + postId + "/bookmarks")
                    .header("Authorization", bearer(otherToken))
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody(ApiResponse.VOID_BODY)
                    .value(body -> {
                        assertThat(body.resultCode()).isEqualTo("404-8");
                        assertThat(body.msg()).isEqualTo("북마크를 찾을 수 없습니다.");
                    });
        }

        @Test
        @DisplayName("6. 타인의 북마크는 취소되지 않는다")
        void deleteBookmark_doesNotTouchOtherUsersBookmark() {
            LoginResponse owner = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(owner.accessToken());
            createBookmark(owner.accessToken(), postId);

            String otherToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            // 북마크한 적 없는 사용자의 취소 요청은 자기 기록만 찾으므로 404-8이다.
            expectResultCode(deleteBookmarkRequest(otherToken, postId), HttpStatus.NOT_FOUND, "404-8");

            assertThat(findBookmark(postId, owner.user().id())).isPresent();
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);
            assertThat(getMyBookmarks(owner.accessToken()).totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("7. 같은 북마크를 두 번 취소해도 200-1이고 bookmarkCount는 0 아래로 내려가지 않는다")
        // FIXME(#1): 첫 취소에서 행이 실제로 지워지지 않기 때문에(#1) 두 번째 취소도 대상을 찾아 성공한다.
        // 정상 구현이라면 두 번째는 404-8이어야 한다. bookmarkCount 가 음수가 되지 않는 것은
        // decreaseBookmarkCount 의 CASE WHEN 가드 덕분이지, 취소가 멱등해서가 아니다.
        void deleteBookmark_twice_returns200BothTimes() {
            LoginResponse session = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            String accessToken = session.accessToken();
            Long postId = createPublicPost(accessToken);
            createBookmark(accessToken, postId);

            expectResultCode(deleteBookmarkRequest(accessToken, postId), HttpStatus.OK, "200-1");
            assertThat(bookmarkCountOf(postId)).isZero();

            expectResultCode(deleteBookmarkRequest(accessToken, postId), HttpStatus.OK, "200-1");
            assertThat(bookmarkCountOf(postId)).isZero();
            assertThat(findBookmark(postId, session.user().id())).isPresent();
        }

        @Test
        @DisplayName("8. 취소한 뒤 다시 북마크하면 409-8이라 재북마크가 불가능하다")
        // FIXME(#1): 취소가 행을 지우지 않으므로(#1) 유니크 제약(uk_post_bookmarks_post_user)이 살아 있어
        // 같은 게시글을 다시 북마크할 수 없다. 목록에는 계속 남아 있는데 "다시 북마크"만 막히는 상태다.
        void createBookmark_afterDelete_returns409_8() {
            String accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(accessToken);

            createBookmark(accessToken, postId);
            expectResultCode(deleteBookmarkRequest(accessToken, postId), HttpStatus.OK, "200-1");

            expectResultCode(createBookmarkRequest(accessToken, postId), HttpStatus.CONFLICT, "409-8");

            assertThat(bookmarkCountOf(postId)).isZero();
            assertThat(getMyBookmarks(accessToken).totalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    class Scenarios {

        @Test
        @DisplayName("1. 두 사용자가 북마크하면 bookmarkCount는 2가 되고 목록은 각자 것만 보인다")
        void twoUsersBookmark_countIsSharedButListIsPerUser() {
            LoginResponse author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            LoginResponse other = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long postId = createPublicPost(author.accessToken());

            createBookmark(author.accessToken(), postId);
            createBookmark(other.accessToken(), postId);

            assertThat(bookmarkCountOf(postId)).isEqualTo(2);
            assertThat(getMyBookmarks(author.accessToken()).totalElements()).isEqualTo(1);
            assertThat(getMyBookmarks(other.accessToken()).totalElements()).isEqualTo(1);

            // 한쪽이 취소하면 공유 카운트만 1 줄어든다(행은 #1 때문에 남는다).
            expectResultCode(deleteBookmarkRequest(other.accessToken(), postId), HttpStatus.OK, "200-1");
            assertThat(bookmarkCountOf(postId)).isEqualTo(1);
            assertThat(findBookmark(postId, author.user().id())).isPresent();
        }

        @Test
        @DisplayName("2. 좋아요한 게시글을 북마크하면 목록의 isLiked도 true로 나온다")
        void bookmarkList_reflectsLikeState() {
            String authorToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());
            Long liked = createPublicPost(authorToken);
            Long notLiked = createPublicPost(authorToken);

            String readerToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname());

            client.post().uri("/api/posts/" + liked + "/likes")
                    .header("Authorization", bearer(readerToken))
                    .exchange()
                    .expectStatus().isCreated();

            createBookmark(readerToken, liked);
            createBookmark(readerToken, notLiked);

            PageResponse<PostListResponse> myBookmarks = getMyBookmarks(readerToken);
            assertThat(myBookmarks.content())
                    .extracting(PostListResponse::id, PostListResponse::isLiked, PostListResponse::isBookmarked)
                    .containsExactly(
                            tuple(notLiked, false, true),
                            tuple(liked, true, true));
            assertThat(myBookmarks.content().get(1).likeCount()).isEqualTo(1);
        }
    }
}
