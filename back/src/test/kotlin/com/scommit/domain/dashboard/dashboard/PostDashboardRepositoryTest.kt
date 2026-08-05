package com.scommit.domain.dashboard.dashboard

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.post.post.repository.PostRepository
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.domain.user.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("포스트 대시보드 Repository")
class PostDashboardRepositoryTest {
    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var creator: User

    @BeforeEach
    fun setUp() {
        creator =
            userRepository.save(
                User(
                    email = "post-creator-" + System.nanoTime() + "@test.com",
                    password = "password123",
                    nickname = "창작자",
                    introduction = "Test intro",
                    role = UserRole.USER,
                ),
            )
    }

    @Test
    @DisplayName("총 포스트 개수를 조회한다")
    fun `총 포스트 개수를 조회한다`() {
        // Given
        savePost("Post 1", "Content 1", PostAccessLevel.FREE)
        savePost("Post 2", "Content 2", PostAccessLevel.PAID)

        // When
        val totalPosts = postRepository.countByUserIdAndDeletedAtIsNull(checkNotNull(creator.id))

        // Then
        assertThat(totalPosts).isEqualTo(2)
    }

    @Test
    @DisplayName("soft-delete된 포스트는 조회에 포함되지 않는다")
    fun `soft-delete된 포스트는 조회에 포함되지 않는다`() {
        // Given
        val post = savePost("Delete Me", null, PostAccessLevel.FREE)

        post.softDelete()
        postRepository.save(post)

        // When
        val count = postRepository.countByUserIdAndDeletedAtIsNull(checkNotNull(creator.id))

        // Then
        assertThat(count).isEqualTo(0)
    }

    @Test
    @DisplayName("접근 레벨별 포스트 개수를 조회한다")
    fun `접근 레벨별 포스트 개수를 조회한다`() {
        // Given: FREE 3개, PAID 2개
        repeat(3) { i -> savePost("Free Post $i", null, PostAccessLevel.FREE) }
        repeat(2) { i -> savePost("Paid Post $i", null, PostAccessLevel.PAID) }

        // When
        val freeCount =
            postRepository.countByUserIdAndAccessLevelAndDeletedAtIsNull(checkNotNull(creator.id), PostAccessLevel.FREE)
        val paidCount =
            postRepository.countByUserIdAndAccessLevelAndDeletedAtIsNull(checkNotNull(creator.id), PostAccessLevel.PAID)

        // Then
        assertThat(freeCount).isEqualTo(3)
        assertThat(paidCount).isEqualTo(2)
    }

    private fun savePost(
        title: String,
        body: String?,
        accessLevel: PostAccessLevel,
    ): Post =
        postRepository.save(
            Post(
                user = creator,
                series = null,
                title = title,
                body = body,
                publishStatus = PublishStatus.PUBLIC,
                accessLevel = accessLevel,
            ),
        )
}
