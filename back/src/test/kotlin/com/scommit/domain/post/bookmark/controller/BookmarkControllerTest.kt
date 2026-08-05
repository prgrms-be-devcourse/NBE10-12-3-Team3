package com.scommit.domain.post.bookmark.controller

import com.scommit.domain.post.bookmark.service.BookmarkService
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.currentUser
import com.scommit.global.security.jwt.JwtFilter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [BookmarkController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtFilter::class])],
)
class BookmarkControllerTest {
    private val mockActor = User(1L, "test@example.com", "테스터")
    private val defaultPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var bookmarkService: BookmarkService

    @Suppress("UnusedPrivateProperty")
    @MockitoBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Nested
    @DisplayName("POST /api/posts/{postId}/bookmarks - 북마크 추가")
    inner class CreateBookmark {
        @Test
        @DisplayName("성공: 201 응답과 메시지를 반환한다")
        fun createBookmark_success() {
            mockMvc
                .perform(post("/api/posts/{postId}/bookmarks", 1L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("북마크가 추가되었습니다."))
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 404를 반환한다")
        fun createBookmark_postNotFound() {
            willThrow(BusinessException(ErrorCode.POST_NOT_FOUND))
                .given(bookmarkService)
                .createBookmark(postId = 999L, actor = mockActor)

            mockMvc
                .perform(post("/api/posts/{postId}/bookmarks", 999L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-3"))
        }
    }

    @Nested
    @DisplayName("GET /api/bookmarks/me - 내 북마크 목록 조회")
    inner class GetMyBookmarks {
        @Test
        @DisplayName("성공: 북마크한 게시글 목록을 반환한다")
        fun getMyBookmarks_success() {
            val response =
                PostListResponse(
                    10L,
                    2L,
                    "작성자",
                    null,
                    "제목",
                    PublishStatus.PUBLIC,
                    PostAccessLevel.FREE,
                    0L,
                    5L,
                    1L,
                    false,
                    true,
                    LocalDateTime.now(),
                )
            val page: Page<PostListResponse> = PageImpl(listOf(response))

            given(bookmarkService.getMyBookmarks(actor = mockActor, pageable = defaultPageable)).willReturn(page)

            mockMvc
                .perform(get("/api/bookmarks/me").with(currentUser(mockActor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(10L))
                .andExpect(jsonPath("$.data.content[0].isBookmarked").value(true))
        }

        @Test
        @DisplayName("성공: 북마크가 없으면 빈 목록을 반환한다")
        fun getMyBookmarks_empty() {
            val emptyPage: Page<PostListResponse> = PageImpl(emptyList())

            given(bookmarkService.getMyBookmarks(actor = mockActor, pageable = defaultPageable)).willReturn(emptyPage)

            mockMvc
                .perform(get("/api/bookmarks/me").with(currentUser(mockActor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/bookmarks - 북마크 취소")
    inner class DeleteBookmark {
        @Test
        @DisplayName("성공: 200 응답과 메시지를 반환한다")
        fun deleteBookmark_success() {
            mockMvc
                .perform(delete("/api/posts/{postId}/bookmarks", 1L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("북마크가 취소되었습니다."))
        }

        @Test
        @DisplayName("실패: 존재하지 않는 게시글이면 404를 반환한다")
        fun deleteBookmark_postNotFound() {
            willThrow(BusinessException(ErrorCode.POST_NOT_FOUND))
                .given(bookmarkService)
                .deleteBookmark(postId = 999L, actor = mockActor)

            mockMvc
                .perform(delete("/api/posts/{postId}/bookmarks", 999L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-3"))
        }

        @Test
        @DisplayName("실패: 북마크가 없으면 404를 반환한다")
        fun deleteBookmark_bookmarkNotFound() {
            willThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .given(bookmarkService)
                .deleteBookmark(postId = 1L, actor = mockActor)

            mockMvc
                .perform(delete("/api/posts/{postId}/bookmarks", 1L).with(currentUser(mockActor)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
        }
    }
}
