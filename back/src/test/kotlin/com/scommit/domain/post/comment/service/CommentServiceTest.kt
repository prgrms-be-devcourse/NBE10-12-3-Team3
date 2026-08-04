package com.scommit.domain.post.comment.service

import com.scommit.domain.notification.notification.dto.NotificationResponse
import com.scommit.domain.notification.notification.dto.NotificationType
import com.scommit.domain.notification.notification.repository.SseEmitterRepository
import com.scommit.domain.post.comment.dto.CommentResponse
import com.scommit.domain.post.comment.entity.Comment
import com.scommit.domain.post.comment.repository.CommentRepository
import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.Optional

private fun anyNotificationResponse(): NotificationResponse {
    any(NotificationResponse::class.java)
    return NotificationResponse(NotificationType.COMMENT, "", 0L)
}

/**
 * CommentService 단위 테스트
 * - DB, Spring Context 없이 Mockito로 의존성을 가짜(Mock)로 대체
 * - commentRepository, postRepository의 반환값을 미리 지정하고 실제 서비스 로직만 검증
 * - 주요 검증 항목: 댓글 CRUD 성공/실패, 본인 확인(ACCESS_DENIED), soft delete 처리
 */
@ExtendWith(MockitoExtension::class)
internal class CommentServiceTest {
    @Mock
    private lateinit var commentRepository: CommentRepository

    @Mock
    private lateinit var postRepository: PostRepository

    @Mock
    private lateinit var sseEmitterRepository: SseEmitterRepository

    @InjectMocks
    private lateinit var commentService: CommentService

    // 테스트에 사용할 유저 2명 (본인 / 타인 구분용)
    private lateinit var mockUser: User
    private lateinit var otherUser: User
    private lateinit var mockPost: Post

    @BeforeEach
    fun setUp() {
        // JPA가 없으므로 id는 ReflectionTestUtils로 직접 주입
        mockUser =
            User(
                email = "test@example.com",
                nickname = "테스터",
                role = UserRole.USER,
            )
        ReflectionTestUtils.setField(mockUser, "id", 1L)

        otherUser =
            User(
                email = "other@example.com",
                nickname = "다른유저",
                role = UserRole.USER,
            )
        ReflectionTestUtils.setField(otherUser, "id", 2L)

        mockPost =
            Post(
                user = mockUser,
                series = null,
                title = "테스트 게시글",
                body = "내용",
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = PostAccessLevel.FREE,
            )
        ReflectionTestUtils.setField(mockPost, "id", 10L)
    }

    // 테스트용 Comment 빌더 헬퍼 - 매 테스트마다 반복 코드를 줄이기 위해 사용
    private fun buildComment(
        id: Long?,
        user: User,
        post: Post,
    ): Comment {
        val comment = Comment(post, user, "댓글 내용")
        ReflectionTestUtils.setField(comment, "id", id)
        return comment
    }

    // softDelete된 댓글 생성 (deletedAt이 null이 아닌 상태)
    private fun buildDeletedComment(
        id: Long?,
        user: User,
        post: Post,
    ): Comment =
        buildComment(id, user, post).also {
            ReflectionTestUtils.setField(it, "deletedAt", LocalDateTime.now())
        }

    @Nested
    @DisplayName("댓글 목록 조회 테스트")
    internal inner class GetComments {
        // 정상적인 게시글의 댓글 목록을 페이지로 조회
        @Test
        @DisplayName("성공: 게시글의 댓글 목록을 페이지로 반환한다.")
        fun get_Success() {
            val pageable: Pageable = PageRequest.of(0, 10)
            val comment = buildComment(1L, mockUser, mockPost)
            val commentPage: Page<Comment> = PageImpl(listOf(comment), pageable, 1)

            `when`(postRepository.findById(10L)).thenReturn(Optional.of(mockPost))
            `when`(commentRepository.findAllByPostIdAndDeletedAtIsNull(10L, pageable))
                .thenReturn(commentPage)

            val result: Page<CommentResponse> = commentService.getComments(10L, pageable)

            // 댓글 1개가 포함된 페이지인지 검증
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content[0].userId).isEqualTo(mockUser.id)
        }

        // 없는 게시글 조회 시 예외
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun get_PostNotFound() {
            val pageable: Pageable = PageRequest.of(0, 10)
            `when`(postRepository.findById(999L)).thenReturn(Optional.empty())

            assertThatThrownBy { commentService.getComments(999L, pageable) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }

        // softDelete된 게시글은 조회 불가
        @Test
        @DisplayName("실패: 삭제된 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun get_PostDeleted() {
            val pageable: Pageable = PageRequest.of(0, 10)
            ReflectionTestUtils.setField(mockPost, "deletedAt", LocalDateTime.now())
            `when`(postRepository.findById(10L)).thenReturn(Optional.of(mockPost))

            assertThatThrownBy { commentService.getComments(10L, pageable) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("댓글 작성 테스트")
    internal inner class CreateComment {
        // 정상적인 게시글에 로그인한 유저가 댓글 작성
        @Test
        @DisplayName("성공: 게시글에 댓글을 작성한다.")
        fun create_Success() {
            val saved = buildComment(1L, mockUser, mockPost)
            `when`(postRepository.findById(10L)).thenReturn(Optional.of(mockPost))
            `when`(commentRepository.save(any(Comment::class.java))).thenReturn(saved)

            val response = commentService.createComment(mockUser, 10L, "댓글 내용")

            // 작성자 ID와 게시글 ID가 응답에 올바르게 포함되는지 검증
            assertThat(response.userId).isEqualTo(mockUser.id)
            assertThat(response.postId).isEqualTo(mockPost.id)
        }

        // 없는 게시글에 댓글 작성 시도 → 게시글 조회 시점에 예외 발생
        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun create_PostNotFound() {
            `when`(postRepository.findById(999L)).thenReturn(Optional.empty())

            assertThatThrownBy { commentService.createComment(mockUser, 999L, "댓글") }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }

        @Test
        @DisplayName("성공: 타인이 댓글 작성 시 게시글 작성자에게 SSE 알림을 전송한다.")
        fun create_OtherUser_SendsSse() {
            val saved = buildComment(1L, otherUser, mockPost)
            `when`(postRepository.findById(10L)).thenReturn(Optional.of(mockPost))
            `when`(commentRepository.save(any(Comment::class.java))).thenReturn(saved)

            commentService.createComment(otherUser, 10L, "댓글")

            verify(sseEmitterRepository).sendToUser(
                eq(checkNotNull(mockUser.id)),
                anyNotificationResponse(),
            )
        }

        @Test
        @DisplayName("성공: 게시글 작성자 본인이 댓글 작성 시 SSE 알림을 전송하지 않는다.")
        fun create_SelfComment_NoSse() {
            val saved = buildComment(1L, mockUser, mockPost)
            `when`(postRepository.findById(10L)).thenReturn(Optional.of(mockPost))
            `when`(commentRepository.save(any(Comment::class.java))).thenReturn(saved)

            commentService.createComment(mockUser, 10L, "댓글")

            verify(sseEmitterRepository, never())
                .sendToUser(anyLong(), anyNotificationResponse())
        }

        // softDelete된 게시글에는 댓글 작성 불가
        @Test
        @DisplayName("실패: 삭제된 게시글이면 POST_NOT_FOUND 예외를 던진다.")
        fun create_PostDeleted() {
            ReflectionTestUtils.setField(mockPost, "deletedAt", LocalDateTime.now())
            `when`(postRepository.findById(10L)).thenReturn(Optional.of(mockPost))

            assertThatThrownBy { commentService.createComment(mockUser, 10L, "댓글") }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("댓글 수정 테스트")
    internal inner class UpdateComment {
        // 본인 댓글 수정 → body가 실제로 바뀌는지 확인 (더티체킹 방식)
        @Test
        @DisplayName("성공: 본인 댓글을 수정한다.")
        fun update_Success() {
            val comment = buildComment(1L, mockUser, mockPost)
            `when`(commentRepository.findById(1L)).thenReturn(Optional.of(comment))

            val response = commentService.updateComment(mockUser, 1L, "수정된 댓글")

            assertThat(response.body).isEqualTo("수정된 댓글")
        }

        // 없는 댓글 수정 시도
        @Test
        @DisplayName("실패: 존재하지 않는 댓글이면 COMMENT_NOT_FOUND 예외를 던진다.")
        fun update_CommentNotFound() {
            `when`(commentRepository.findById(999L)).thenReturn(Optional.empty())

            assertThatThrownBy { commentService.updateComment(mockUser, 999L, "수정") }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND)
        }

        // softDelete된 댓글은 없는 것과 동일하게 처리
        @Test
        @DisplayName("실패: 삭제된 댓글이면 COMMENT_NOT_FOUND 예외를 던진다.")
        fun update_CommentDeleted() {
            val deleted = buildDeletedComment(1L, mockUser, mockPost)
            `when`(commentRepository.findById(1L)).thenReturn(Optional.of(deleted))

            assertThatThrownBy { commentService.updateComment(mockUser, 1L, "수정") }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND)
        }

        // 타인의 댓글 수정 시도 → 본인 확인 로직에서 차단
        @Test
        @DisplayName("실패: 다른 유저의 댓글을 수정하면 ACCESS_DENIED 예외를 던진다.")
        fun update_NotOwner() {
            val comment = buildComment(1L, otherUser, mockPost)
            `when`(commentRepository.findById(1L)).thenReturn(Optional.of(comment))

            assertThatThrownBy { commentService.updateComment(mockUser, 1L, "수정") }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }
    }

    @Nested
    @DisplayName("댓글 삭제 테스트")
    internal inner class DeleteComment {
        // softDelete 방식이므로 실제로 행이 지워지는 게 아니라 deletedAt이 채워져야 함
        @Test
        @DisplayName("성공: 본인 댓글을 삭제한다.")
        fun delete_Success() {
            val comment = buildComment(1L, mockUser, mockPost)
            `when`(commentRepository.findById(1L)).thenReturn(Optional.of(comment))

            commentService.deleteComment(mockUser, 1L)

            assertThat(comment.deletedAt).isNotNull()
        }

        // 없는 댓글 삭제 시도
        @Test
        @DisplayName("실패: 존재하지 않는 댓글이면 COMMENT_NOT_FOUND 예외를 던진다.")
        fun delete_CommentNotFound() {
            `when`(commentRepository.findById(999L)).thenReturn(Optional.empty())

            assertThatThrownBy { commentService.deleteComment(mockUser, 999L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND)
        }

        // 이미 삭제된 댓글 재삭제 시도 → 멱등성 보장이 아닌 에러 반환 방식
        @Test
        @DisplayName("실패: 이미 삭제된 댓글이면 COMMENT_NOT_FOUND 예외를 던진다.")
        fun delete_AlreadyDeleted() {
            val deleted = buildDeletedComment(1L, mockUser, mockPost)
            `when`(commentRepository.findById(1L)).thenReturn(Optional.of(deleted))

            assertThatThrownBy { commentService.deleteComment(mockUser, 1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND)
        }

        // 타인 댓글 삭제 시도 → 본인 확인 로직에서 차단
        @Test
        @DisplayName("실패: 다른 유저의 댓글을 삭제하면 ACCESS_DENIED 예외를 던진다.")
        fun delete_NotOwner() {
            val comment = buildComment(1L, otherUser, mockPost)
            `when`(commentRepository.findById(1L)).thenReturn(Optional.of(comment))

            assertThatThrownBy { commentService.deleteComment(mockUser, 1L) }
                .isInstanceOf(BusinessException::class.java)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED)
        }
    }
}
