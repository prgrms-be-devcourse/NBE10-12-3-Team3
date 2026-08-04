package com.scommit.domain.post.like.controller

import com.scommit.domain.post.like.service.LikeService
import com.scommit.domain.user.user.entity.User
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
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
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    controllers = [LikeController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtFilter::class])],
)
class LikeControllerTest {
    private val mockActor = User(1L, "test@example.com", "테스터")

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var likeService: LikeService

    @MockitoBean
    private lateinit var securityHelper: SecurityHelper

    @Suppress("UnusedPrivateProperty")
    @MockitoBean
    private lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @Nested
    @DisplayName("POST /api/posts/{postId}/likes - 좋아요 추가")
    inner class CreateLike {
        @Test
        @WithMockUser
        @DisplayName("성공: 201 응답과 메시지를 반환한다")
        fun createLike_success() {
            given(securityHelper.actor).willReturn(mockActor)

            mockMvc
                .perform(post("/api/posts/{postId}/likes", 1L).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.msg").value("좋아요가 추가되었습니다."))
        }

        @Test
        @WithMockUser
        @DisplayName("실패: 비로그인 사용자는 401을 반환한다")
        fun createLike_unauthorized() {
            given(securityHelper.actor).willReturn(null)

            mockMvc
                .perform(post("/api/posts/{postId}/likes", 1L).with(csrf()))
                .andExpect(status().isUnauthorized())
        }

        @Test
        @WithMockUser
        @DisplayName("실패: 존재하지 않는 게시글이면 404를 반환한다")
        fun createLike_postNotFound() {
            given(securityHelper.actor).willReturn(mockActor)
            willThrow(BusinessException(ErrorCode.POST_NOT_FOUND))
                .given(likeService)
                .createLike(postId = 999L, actor = mockActor)

            mockMvc
                .perform(post("/api/posts/{postId}/likes", 999L).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-3"))
        }
    }

    @Nested
    @DisplayName("DELETE /api/posts/{postId}/likes - 좋아요 취소")
    inner class DeleteLike {
        @Test
        @WithMockUser
        @DisplayName("성공: 200 응답과 메시지를 반환한다")
        fun deleteLike_success() {
            given(securityHelper.actor).willReturn(mockActor)

            mockMvc
                .perform(delete("/api/posts/{postId}/likes", 1L).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.msg").value("좋아요가 취소되었습니다."))
        }

        @Test
        @WithMockUser
        @DisplayName("실패: 비로그인 사용자는 401을 반환한다")
        fun deleteLike_unauthorized() {
            given(securityHelper.actor).willReturn(null)

            mockMvc
                .perform(delete("/api/posts/{postId}/likes", 1L).with(csrf()))
                .andExpect(status().isUnauthorized())
        }

        @Test
        @WithMockUser
        @DisplayName("실패: 존재하지 않는 게시글이면 404를 반환한다")
        fun deleteLike_postNotFound() {
            given(securityHelper.actor).willReturn(mockActor)
            willThrow(BusinessException(ErrorCode.POST_NOT_FOUND))
                .given(likeService)
                .deleteLike(postId = 999L, actor = mockActor)

            mockMvc
                .perform(delete("/api/posts/{postId}/likes", 999L).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-3"))
        }

        @Test
        @WithMockUser
        @DisplayName("실패: 좋아요가 없으면 404를 반환한다")
        fun deleteLike_likeNotFound() {
            given(securityHelper.actor).willReturn(mockActor)
            willThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .given(likeService)
                .deleteLike(postId = 1L, actor = mockActor)

            mockMvc
                .perform(delete("/api/posts/{postId}/likes", 1L).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
        }
    }
}
