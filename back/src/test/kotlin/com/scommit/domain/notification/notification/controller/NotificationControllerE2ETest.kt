package com.scommit.domain.notification.notification.controller

// 결정사항 (docs/e2e-test-convention.md 를 그대로 따른다)
// - @SpringBootTest(RANDOM_PORT). Mock / MockMvc / @MockitoBean 일절 사용 금지.
// - DB: 이 클래스 전용 H2 in-memory(notificationdb). 컨벤션 4장.
// - 알림은 저장되지 않는다. 조회용 목록 API 도 없고, SseEmitterRepository(ConcurrentHashMap)에
//   물려 있는 열린 연결로 즉시 push 될 뿐이다. 따라서 "DB 반영 확인"이 불가능하고,
//   검증은 전부 "실제로 연결에 이벤트가 도착했는가"로 한다.
// - 알림을 만드는 것은 다른 도메인의 행위다. 전부 실제 API 로 유발할 수 있다(컨벤션 5장 우선순위 1).
//     COMMENT    ← POST /api/posts/{postId}/comments        (CommentService.createComment)
//     FOLLOW     ← POST /api/subscriptions/follow/{id}      (SubscriptionService.follow)
//     MEMBERSHIP ← POST /api/subscriptions/membership/{id}  (SubscriptionService.joinMembership)
//     NEW_POST   ← POST /api/posts (PUBLIC)                 (PostService.createPost → sendSse)
//   그래서 픽스처 클래스를 만들지 않았다.
// - HTTP 클라이언트: 성공 케이스는 RestTestClient 를 쓸 수 없다. text/event-stream 응답은
//   서버가 30분 타임아웃까지 연결을 열어 두므로 본문을 끝까지 읽는 클라이언트는 그대로 멈춘다.
//   스트림을 조금씩 읽고 원할 때 끊을 수 있는 java.net.http.HttpClient 를 쓴다 — 실제 HTTP 요청이라
//   "Mock 금지" 원칙에 어긋나지 않는다. 본문 없이 끝나는 인증 실패 케이스는 RestTestClient 로 본다.
// - @DirtiesContext / @Transactional 미사용. SseEmitterRepository 는 컨텍스트 전역 싱글턴이지만
//   userId 를 키로 쓰므로, 테스트마다 새 유저를 만들면 서로 간섭하지 않는다.

import com.scommit.domain.notification.notification.dto.NotificationType
import com.scommit.domain.post.post.dto.PostCreateRequest
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
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
import org.junit.jupiter.api.Timeout
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // 이 클래스 전용 DB 이름(컨벤션 4장 표: notificationdb).
    properties = ["spring.datasource.url=jdbc:h2:mem:notificationdb;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@ActiveProfiles("test")
@Tag("e2e")
// SSE 는 "오지 않는 것"까지 검증하므로 대기 시간이 섞인다. 서버가 끝내 응답하지 않는 상황에서
// 30분(SseEmitter 타임아웃) 동안 빌드가 매달리지 않도록 클래스 전체에 상한을 둔다.
@Timeout(90)
class NotificationControllerE2ETest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestTestClient

    @BeforeEach
    fun setUpClient() {
        client = E2ETestSupport.client(port)
    }

    // ---------- SSE 수신 도구 ----------

    /** `event:<name>` + `data:<payload>` 두 줄을 한 덩어리로 본 값. */
    private data class SseEvent(
        val name: String,
        val data: String,
    )

    /**
     * 열린 SSE 연결 하나. 백그라운드 스레드가 스트림을 읽어 큐에 쌓고, 테스트는 큐를 폴링한다.
     * [close] 로 반드시 끊어야 한다 — 끊지 않으면 서버 쪽 비동기 요청이 남고
     * `HttpClient` 종료가 SseEmitter 타임아웃까지 블로킹된다.
     */
    private class SseConnection(
        private val httpClient: HttpClient,
        private val lines: Stream<String>,
    ) : AutoCloseable {
        private val received: BlockingQueue<String> = LinkedBlockingQueue()

        init {
            // 스트림 읽기는 블로킹이라 별도 스레드에서 돌린다. close() 시 예외로 끝나는 것이 정상이다.
            Thread.ofVirtual().name("sse-reader").start {
                try {
                    lines.filter { line -> !line.isBlank() }.forEach(received::offer)
                } catch (ignored: RuntimeException) {
                    // 연결을 끊으면서 나는 예외 — 테스트 실패로 다룰 것이 없다.
                }
            }
        }

        /** 다음 이벤트를 기다린다. 시간 안에 오지 않으면 null. */
        fun nextEvent(): SseEvent? {
            val nameLine = received.poll(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS) ?: return null
            val dataLine = received.poll(EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            assertThat(nameLine).startsWith("event:")
            assertThat(dataLine).`as`("event 줄 뒤에는 data 줄이 와야 한다").isNotNull().startsWith("data:")
            return SseEvent(nameLine.removePrefix("event:"), dataLine.removePrefix("data:"))
        }

        /** 일정 시간 동안 아무 이벤트도 오지 않았음을 확인한다. */
        fun expectSilence() {
            assertThat(received.poll(SILENCE_WINDOW.toMillis(), TimeUnit.MILLISECONDS))
                .`as`("알림이 오지 않아야 한다")
                .isNull()
        }

        override fun close() {
            lines.close()
            httpClient.shutdownNow()
        }
    }

    /**
     * 실제 HTTP 로 구독을 열고, 서버가 맨 처음 보내는 `connect` 이벤트까지 소비한 연결을 돌려준다.
     * 이후 테스트는 "구독 이후에 발생한 알림"만 보면 된다.
     */
    private fun subscribe(accessToken: String): SseConnection {
        val connection = openSubscription(accessToken)
        val connect = connection.nextEvent()
        assertThat(connect).`as`("구독 직후 connect 이벤트가 와야 한다").isNotNull()
        checkNotNull(connect)
        assertThat(connect.name).isEqualTo("connect")
        assertThat(connect.data).isEqualTo("connected!")
        return connection
    }

    /** 상태 코드와 Content-Type 만 확인하고 연결을 열어 둔다(첫 이벤트는 소비하지 않는다). */
    private fun openSubscription(accessToken: String): SseConnection {
        val httpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/api/notifications/subscribe"))
                .header("Authorization", bearer(accessToken))
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .GET()
                .build()

        val response: HttpResponse<Stream<String>> =
            try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofLines())
            } catch (e: IOException) {
                httpClient.shutdownNow()
                throw e
            } catch (e: InterruptedException) {
                httpClient.shutdownNow()
                throw e
            }

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value())
        assertThat(checkNotNull(response.headers().firstValue("Content-Type").getOrNull()))
            .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)
        return SseConnection(httpClient, response.body())
    }

    // ---------- 알림을 유발하는 다른 도메인 API ----------

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
                    .body(PostCreateRequest(null, "알림 E2E 게시글", "게시글 본문", publishStatus, accessLevel))
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

    private fun createComment(
        accessToken: String,
        postId: Long,
        body: String,
    ) {
        client
            .post()
            .uri("/api/posts/$postId/comments")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"body\":\"$body\"}")
            .exchange()
            .expectStatus()
            .isCreated()
    }

    private fun follow(
        accessToken: String,
        creatorId: Long,
    ) {
        expectResultCode(
            client
                .post()
                .uri("/api/subscriptions/follow/$creatorId")
                .header("Authorization", bearer(accessToken))
                .exchange(),
            HttpStatus.OK,
            "200-1",
        )
    }

    private fun joinMembership(
        accessToken: String,
        creatorId: Long,
    ) {
        expectResultCode(
            client
                .post()
                .uri("/api/subscriptions/membership/$creatorId")
                .header("Authorization", bearer(accessToken))
                .exchange(),
            HttpStatus.OK,
            "200-1",
        )
    }

    @Nested
    @DisplayName("GET /api/notifications/subscribe — 알림 구독(SSE)")
    inner class Subscribe {
        @Test
        @DisplayName("1. 성공하면 200과 text/event-stream으로 connect 이벤트를 먼저 보낸다")
        fun subscribe_success_returns200AndConnectEvent() {
            val accessToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            // openSubscription 이 상태 코드와 Content-Type 을 확인하고, 여기서 첫 이벤트를 직접 본다.
            openSubscription(accessToken).use { connection ->
                val connect = connection.nextEvent()
                assertThat(connect).isNotNull()
                checkNotNull(connect)
                assertThat(connect.name).isEqualTo("connect")
                assertThat(connect.data).isEqualTo("connected!")

                // 아무 일도 없으면 그 뒤로는 조용하다.
                connection.expectSilence()
            }
        }

        @Test
        @DisplayName("2. 미인증이면 401-1을 반환한다")
        fun subscribe_unauthenticated_returns401_1() {
            expectResultCode(
                client.get().uri("/api/notifications/subscribe").exchange(),
                HttpStatus.UNAUTHORIZED,
                "401-1",
            )
        }

        @Test
        @DisplayName("3. 깨진 토큰이면 401-1과 로그인 안내 메시지를 반환한다")
        fun subscribe_malformedToken_returns401_1() {
            client
                .get()
                .uri("/api/notifications/subscribe")
                .header("Authorization", "Bearer not.a.real.token")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(ApiResponse.VOID_BODY)
                .value { body ->
                    checkNotNull(body)
                    // 401-1 은 AuthenticationEntryPoint 와 BusinessException(UNAUTHORIZED)이 공유하는
                    // 코드라 msg 까지 본다(컨벤션 6장). 깨진 토큰은 JwtFilter 가 조용히 익명으로 흘려보내고
                    // EntryPoint 가 응답하므로 "로그인 후 이용해주세요." 쪽이다.
                    assertThat(body.resultCode).isEqualTo("401-1")
                    assertThat(body.msg).isEqualTo("로그인 후 이용해주세요.")
                }
        }

        @Test
        @DisplayName("4. 내 게시글에 타인이 댓글을 달면 COMMENT 알림이 온다")
        fun subscribe_receivesCommentNotification() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val commenter = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)

            subscribe(author.accessToken).use { connection ->
                createComment(commenter.accessToken, postId, "알림을 만드는 댓글")

                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.name).isEqualTo("notification")
                assertThat(event.data)
                    .contains("\"type\":\"${NotificationType.COMMENT}\"")
                    .contains("\"message\":\"${commenter.user.nickname}님이 댓글을 작성했습니다.\"")
                    // targetId 는 알림을 눌렀을 때 이동할 대상 — 댓글이 아니라 게시글 id 다.
                    .contains("\"targetId\":$postId")
            }
        }

        @Test
        @DisplayName("5. 내 글에 내가 댓글을 달면 알림이 오지 않는다")
        fun subscribe_ownCommentDoesNotNotify() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val commenter = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)

            subscribe(author.accessToken).use { connection ->
                createComment(author.accessToken, postId, "내가 내 글에 다는 댓글")
                connection.expectSilence()

                // 연결이 죽어서 조용한 것이 아님을 같은 연결로 확인한다.
                createComment(commenter.accessToken, postId, "남이 다는 댓글")
                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.data).contains("\"type\":\"${NotificationType.COMMENT}\"")
            }
        }

        @Test
        @DisplayName("6. 타인이 나를 팔로우하면 FOLLOW 알림이 온다")
        fun subscribe_receivesFollowNotification() {
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val follower = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            subscribe(creator.accessToken).use { connection ->
                follow(follower.accessToken, creator.user.id)

                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.name).isEqualTo("notification")
                assertThat(event.data)
                    .contains("\"type\":\"${NotificationType.FOLLOW}\"")
                    .contains("\"message\":\"${follower.user.nickname}님이 팔로우했습니다.\"")
                    // FOLLOW 의 targetId 는 팔로우한 사람의 유저 id 다.
                    .contains("\"targetId\":${follower.user.id}")
            }
        }

        @Test
        @DisplayName("7. 타인이 내 멤버십에 가입하면 MEMBERSHIP 알림이 온다")
        fun subscribe_receivesMembershipNotification() {
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val member = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            subscribe(creator.accessToken).use { connection ->
                joinMembership(member.accessToken, creator.user.id)

                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.data)
                    .contains("\"type\":\"${NotificationType.MEMBERSHIP}\"")
                    .contains("\"message\":\"${member.user.nickname}님이 멤버십에 가입했습니다.\"")
                    .contains("\"targetId\":${member.user.id}")
            }
        }

        @Test
        @DisplayName("8. 내가 팔로우한 창작자가 PUBLIC 게시글을 쓰면 NEW_POST 알림이 온다")
        fun subscribe_receivesNewPostNotification() {
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val follower = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            follow(follower.accessToken, creator.user.id)

            subscribe(follower.accessToken).use { connection ->
                val postId = createPublicPost(creator.accessToken)

                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.data)
                    .contains("\"type\":\"${NotificationType.NEW_POST}\"")
                    .contains("\"message\":\"${creator.user.nickname}님이 새 게시글을 작성했습니다.\"")
                    .contains("\"targetId\":$postId")
            }
        }

        @Test
        @DisplayName("9. 팔로우한 창작자가 DRAFT 게시글을 써도 알림이 오지 않는다")
        fun subscribe_draftPostDoesNotNotify() {
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val follower = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            follow(follower.accessToken, creator.user.id)

            subscribe(follower.accessToken).use { connection ->
                // PostService.createPost 는 publishStatus == PUBLIC 일 때만 sendSse 를 부른다.
                createPost(creator.accessToken, PublishStatus.DRAFT, PostAccessLevel.FREE)
                connection.expectSilence()

                // 같은 연결로 PUBLIC 을 하나 더 써서 "연결이 살아 있었다"를 확인한다.
                val publicPostId = createPublicPost(creator.accessToken)
                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.data).contains("\"targetId\":$publicPostId")
            }
        }

        @Test
        @DisplayName("10. FOLLOW 등급 구독자는 PAID 게시글 알림을 받지 않는다")
        fun subscribe_paidPostNotifiesMembersOnly() {
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val follower = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            follow(follower.accessToken, creator.user.id)

            subscribe(follower.accessToken).use { connection ->
                // PAID 글의 수신자는 findByCreatorIdAndTierAndDeletedAtIsNull(..., MEMBERSHIP) 로만 뽑는다.
                createPost(creator.accessToken, PublishStatus.PUBLIC, PostAccessLevel.PAID)
                connection.expectSilence()

                // FREE 글은 같은 구독자에게도 간다 — 등급 필터가 원인임을 대비로 보인다.
                val freePostId = createPublicPost(creator.accessToken)
                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.data).contains("\"targetId\":$freePostId")
            }
        }

        // FIXME(#7): NotificationType 에 좋아요/북마크에 해당하는 값이 없고 LikeService/BookmarkService 도
        // SseEmitterRepository 를 주입받지 않는다. 댓글·팔로우와 달리 "내 글이 좋아요를 받았다"는 알림이
        // 아예 없는 스펙 공백이라, 현재 동작(무음)을 그대로 고정해 둔다.
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #7
        @Test
        @DisplayName("11. 좋아요·북마크는 알림을 만들지 않는다")
        fun subscribe_likeAndBookmarkDoNotNotify() {
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val readerToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)

            subscribe(author.accessToken).use { connection ->
                client
                    .post()
                    .uri("/api/posts/$postId/likes")
                    .header("Authorization", bearer(readerToken))
                    .exchange()
                    .expectStatus()
                    .isCreated()
                connection.expectSilence()

                client
                    .post()
                    .uri("/api/posts/$postId/bookmarks")
                    .header("Authorization", bearer(readerToken))
                    .exchange()
                    .expectStatus()
                    .isCreated()
                connection.expectSilence()

                // 댓글은 오는지 확인해서 연결이 살아 있었음을 보인다.
                createComment(readerToken, postId, "알림이 오는 행위")
                assertThat(connection.nextEvent()).isNotNull()
            }
        }

        @Test
        @DisplayName("12. 다른 사람에게 가는 알림은 내 연결로 오지 않는다")
        fun subscribe_doesNotReceiveOtherUsersNotification() {
            val bystander = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val author = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val commenterToken = createUserAndGetAccessToken(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val postId = createPublicPost(author.accessToken)

            subscribe(bystander.accessToken).use { connection ->
                // 알림 수신자는 게시글 작성자다. 구경꾼의 연결에는 아무것도 오면 안 된다.
                createComment(commenterToken, postId, "남의 글에 다는 댓글")
                connection.expectSilence()

                // 구경꾼을 팔로우하면 그때는 온다.
                follow(commenterToken, bystander.user.id)
                val event = connection.nextEvent()
                assertThat(event).isNotNull()
                checkNotNull(event)
                assertThat(event.data).contains("\"type\":\"${NotificationType.FOLLOW}\"")
            }
        }

        // FIXME(#6): SseEmitterRepository.add 가 ConcurrentHashMap.put 이라 같은 userId 의 이전 emitter 를
        // 그냥 덮어쓴다. 이전 emitter 는 complete() 되지도 remove() 되지도 않아, 클라이언트는 끊긴 줄 모른 채
        // 30분 타임아웃까지 연결을 붙들고 아무 알림도 받지 못한다(탭 두 개를 열면 바로 재현된다).
        // 상세: docs/like-bookmark-notification-e2e-known-issues.md #6
        @Test
        @DisplayName("13. 같은 유저가 다시 구독하면 새 연결만 알림을 받고 이전 연결은 조용해진다")
        fun subscribe_secondSubscriptionSilentlyReplacesFirst() {
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val follower = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            follow(follower.accessToken, creator.user.id)

            subscribe(follower.accessToken).use { first ->
                subscribe(follower.accessToken).use { second ->
                    val postId = createPublicPost(creator.accessToken)

                    // 나중에 연 연결만 받는다.
                    val event = second.nextEvent()
                    assertThat(event).isNotNull()
                    checkNotNull(event)
                    assertThat(event.data).contains("\"targetId\":$postId")

                    // 먼저 연 연결은 끊기지도 않고(스트림이 살아 있어 읽기가 블로킹된다) 알림도 못 받는다.
                    first.expectSilence()
                }
            }
        }
    }

    @Nested
    @DisplayName("시나리오 — 여러 API를 잇는 통합 흐름")
    inner class Scenarios {
        @Test
        @DisplayName("1. 구독 하나로 FOLLOW → NEW_POST → COMMENT 알림을 발생 순서대로 받는다")
        fun singleSubscription_receivesAllNotificationTypesInOrder() {
            val me = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val creator = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())
            val fan = createUserAndLogin(client, uniqueEmail(), DEFAULT_PASSWORD, uniqueNickname())

            val myPostId = createPublicPost(me.accessToken)
            follow(me.accessToken, creator.user.id)

            subscribe(me.accessToken).use { connection ->
                // 1) 누가 나를 팔로우
                follow(fan.accessToken, me.user.id)
                val followEvent = connection.nextEvent()
                assertThat(followEvent).isNotNull()
                checkNotNull(followEvent)
                assertThat(followEvent.data).contains("\"type\":\"${NotificationType.FOLLOW}\"")

                // 2) 내가 팔로우한 창작자가 글을 씀
                val creatorPostId = createPublicPost(creator.accessToken)
                val newPostEvent = connection.nextEvent()
                assertThat(newPostEvent).isNotNull()
                checkNotNull(newPostEvent)
                assertThat(newPostEvent.data)
                    .contains("\"type\":\"${NotificationType.NEW_POST}\"")
                    .contains("\"targetId\":$creatorPostId")

                // 3) 내 글에 누가 댓글
                createComment(fan.accessToken, myPostId, "마지막 알림")
                val commentEvent = connection.nextEvent()
                assertThat(commentEvent).isNotNull()
                checkNotNull(commentEvent)
                assertThat(commentEvent.data)
                    .contains("\"type\":\"${NotificationType.COMMENT}\"")
                    .contains("\"targetId\":$myPostId")

                connection.expectSilence()
            }
        }
    }

    companion object {
        private const val DEFAULT_PASSWORD = "password123"

        /** 이벤트가 "온다"고 기대할 때의 대기 상한. 로컬 루프백이라 실제로는 즉시 도착한다. */
        private val EVENT_TIMEOUT: Duration = Duration.ofSeconds(10)

        /** 이벤트가 "오지 않는다"고 기대할 때의 대기 시간. 너무 짧으면 검증이 무의미해진다. */
        private val SILENCE_WINDOW: Duration = Duration.ofSeconds(2)
    }
}
