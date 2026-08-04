package com.scommit.domain.series.series.controller

import com.scommit.domain.media.media.entity.MediaType
import com.scommit.domain.post.post.service.PostService
import com.scommit.domain.series.series.dto.SeriesCreateRequest
import com.scommit.domain.series.series.dto.SeriesListResponse
import com.scommit.domain.series.series.dto.SeriesResponse
import com.scommit.domain.series.series.dto.SeriesUpdateRequest
import com.scommit.domain.series.series.service.SeriesService
import com.scommit.domain.series.seriesmedia.dto.SeriesMediaResponse
import com.scommit.domain.series.seriesmedia.service.SeriesMediaService
import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.user.entity.UserRole
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import com.scommit.global.security.jwt.JwtFilter
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.SliceImpl
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.multipart.MultipartFile
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

private fun <T> eqKotlin(value: T): T {
    eq(value)
    return value
}

private fun anyPageable(): Pageable {
    any(Pageable::class.java)
    return PageRequest.of(0, 10)
}

private fun anyUserRole(): UserRole {
    any(UserRole::class.java)
    return UserRole.USER
}

private fun anyMultipartFile(): MultipartFile {
    any(MultipartFile::class.java)
    return MockMultipartFile("file", ByteArray(0))
}

@WebMvcTest(
    controllers = [SeriesController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtFilter::class])],
)
class SeriesControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var seriesService: SeriesService

    @MockitoBean
    private lateinit var seriesMediaService: SeriesMediaService

    private val mockActor = User(1L, "test@example.com", "테스터", UserRole.USER)

    @MockitoBean
    lateinit var postService: PostService

    @MockitoBean
    lateinit var jpaMetamodelMappingContext: JpaMetamodelMappingContext

    @MockitoBean
    private lateinit var securityHelper: SecurityHelper

    private fun createMockSeriesResponse(
        id: Long,
        userId: Long,
        title: String,
        body: String,
    ): SeriesResponse =
        SeriesResponse(
            id,
            userId,
            "테스터",
            title,
            body,
            LocalDateTime.now(),
            LocalDateTime.now(),
        )

    private fun createMockSeriesListResponse(
        id: Long,
        userId: Long,
        title: String,
    ): SeriesListResponse = SeriesListResponse(id, userId, "테스터", title, null, 0L, null, null, null)

    @Test
    @WithMockUser
    @DisplayName("POST /api/series - 새 시리즈 생성 성공")
    fun createSeries_Success() {
        val request = SeriesCreateRequest("시리즈 제목", "시리즈 설명")
        val mockResponse = createMockSeriesResponse(1L, 1L, "시리즈 제목", "시리즈 설명")

        given(securityHelper.actor).willReturn(mockActor)
        given(seriesService.createSeries(anyString(), anyString(), anyLong())).willReturn(mockResponse)

        mockMvc
            .perform(
                post("/api/series")
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.id").value(1L))
            .andExpect(jsonPath("$.data.title").value("시리즈 제목"))
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/series - 비인증 사용자 시리즈 생성 시도 실패 (401)")
    fun createSeries_Unauthorized() {
        val request = SeriesCreateRequest("시리즈 제목", "시리즈 설명")
        given(securityHelper.actor).willReturn(null)

        mockMvc
            .perform(
                post("/api/series")
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series - 시리즈 전체 조회 성공 (무한 스크롤)")
    fun getAllSeries_Success() {
        val mockSeriesList =
            listOf(
                createMockSeriesListResponse(1L, 1L, "제목 1"),
                createMockSeriesListResponse(2L, 2L, "제목 2"),
            )
        val mockSlice = SliceImpl(mockSeriesList)

        given(seriesService.getSeriesSlice(anyPageable())).willReturn(mockSlice)

        mockMvc
            .perform(get("/api/series"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].title").value("제목 1"))
            .andExpect(jsonPath("$.data.last").value(true))
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series/users/{userId} - 특정 유저의 시리즈 조회 성공")
    fun getSeriesByUser_Success() {
        val mockSeriesList =
            listOf(
                createMockSeriesListResponse(1L, 1L, "크리에이터 제목"),
            )
        val mockPage = PageImpl(mockSeriesList)

        given(seriesService.getSeriesList(eqKotlin(1L), anyPageable())).willReturn(mockPage)

        mockMvc
            .perform(get("/api/series/users/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].title").value("크리에이터 제목"))
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series/search?keyword=X - 시리즈 제목 검색 성공")
    fun searchSeries_Success() {
        val mockSeriesList =
            listOf(
                createMockSeriesListResponse(1L, 1L, "Spring 입문"),
            )
        val mockPage = PageImpl(mockSeriesList)

        given(seriesService.searchSeries(eqKotlin("Spring"), anyPageable())).willReturn(mockPage)

        mockMvc
            .perform(get("/api/series/search").param("keyword", "Spring"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].title").value("Spring 입문"))
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series/me - 내 시리즈 조회 성공")
    fun getMySeriesList_Success() {
        val mockSeriesList =
            listOf(
                createMockSeriesListResponse(1L, 1L, "내 시리즈"),
            )
        val mockPage = PageImpl(mockSeriesList)

        given(securityHelper.actor).willReturn(mockActor)
        given(seriesService.getSeriesList(eqKotlin(1L), anyPageable())).willReturn(mockPage)

        mockMvc
            .perform(get("/api/series/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].title").value("내 시리즈"))
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series/me - 비인증 사용자 접근 실패 (401)")
    fun getMySeriesList_Unauthorized() {
        given(securityHelper.actor).willReturn(null)

        mockMvc
            .perform(get("/api/series/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series/{id} - 시리즈 상세 조회 성공")
    fun getSeriesDetail_Success() {
        val mockResponse = createMockSeriesResponse(1L, 1L, "제목 1", "설명 1")

        given(seriesService.getSeries(1L)).willReturn(mockResponse)

        mockMvc
            .perform(get("/api/series/{id}", 1L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").value("제목 1"))
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/series/{id} - 존재하지 않는 시리즈 상세 조회 실패 (404 Not Found)")
    fun getSeriesDetail_NotFound() {
        given(seriesService.getSeries(999L))
            .willThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))

        mockMvc
            .perform(get("/api/series/{id}", 999L))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/series/{id} - 시리즈 수정 성공")
    fun updateSeries_Success() {
        val request = SeriesUpdateRequest("수정된 제목", "수정된 설명")
        val mockResponse = createMockSeriesResponse(1L, 1L, "수정된 제목", "수정된 설명")

        given(securityHelper.actor).willReturn(mockActor)
        given(
            seriesService.updateSeries(eqKotlin(1L), anyString(), anyString(), anyLong(), anyUserRole()),
        ).willReturn(mockResponse)

        mockMvc
            .perform(
                put("/api/series/{id}", 1L)
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").value("수정된 제목"))
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/series/{id} - 타인 시리즈 수정 시도 실패 (403)")
    fun updateSeries_Forbidden() {
        val request = SeriesUpdateRequest("수정된 제목", "수정된 설명")
        val otherActor = User(99L, "other@example.com", "다른유저", UserRole.USER)

        given(securityHelper.actor).willReturn(otherActor)
        given(seriesService.updateSeries(eqKotlin(1L), anyString(), anyString(), eqKotlin(99L), anyUserRole()))
            .willThrow(BusinessException(ErrorCode.ACCESS_DENIED))

        mockMvc
            .perform(
                put("/api/series/{id}", 1L)
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/series/{id} - 입력값 유효성 검증 실패 (400 Bad Request)")
    fun updateSeries_ValidationError() {
        val request = SeriesUpdateRequest("", "설명")

        mockMvc
            .perform(
                put("/api/series/{id}", 1L)
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/series/{id} - 존재하지 않는 시리즈 수정 실패 (404 Not Found)")
    fun updateSeries_NotFound() {
        val request = SeriesUpdateRequest("수정 제목", "수정 설명")

        given(securityHelper.actor).willReturn(mockActor)
        given(seriesService.updateSeries(eqKotlin(999L), anyString(), anyString(), anyLong(), anyUserRole()))
            .willThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))

        mockMvc
            .perform(
                put("/api/series/{id}", 999L)
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/series/{id} - 시리즈 삭제 성공")
    fun deleteSeries_Success() {
        given(securityHelper.actor).willReturn(mockActor)

        mockMvc
            .perform(
                delete("/api/series/{id}", 1L)
                    .with(csrf()),
            ).andExpect(status().isOk)
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/series/{id} - 타인 시리즈 삭제 시도 실패 (403)")
    fun deleteSeries_Forbidden() {
        val otherActor = User(99L, "other@example.com", "다른유저", UserRole.USER)
        given(securityHelper.actor).willReturn(otherActor)
        doThrow(BusinessException(ErrorCode.ACCESS_DENIED))
            .`when`(seriesService)
            .deleteSeries(eqKotlin(1L), eqKotlin(99L), anyUserRole())

        mockMvc
            .perform(
                delete("/api/series/{id}", 1L)
                    .with(csrf()),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.resultCode").value("403-1"))
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/series/{id} - 존재하지 않는 시리즈 삭제 실패 (404 Not Found)")
    fun deleteSeries_NotFound() {
        given(securityHelper.actor).willReturn(mockActor)
        doThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
            .`when`(seriesService)
            .deleteSeries(eqKotlin(999L), anyLong(), anyUserRole())

        mockMvc
            .perform(
                delete("/api/series/{id}", 999L)
                    .with(csrf()),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.resultCode").value("404-1"))
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/series - 입력값 유효성 검증 실패 (400 Bad Request)")
    fun createSeries_ValidationError() {
        val request = SeriesCreateRequest("", "설명")

        given(securityHelper.actor).willReturn(mockActor)

        mockMvc
            .perform(
                post("/api/series")
                    .with(csrf())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)
    }

    @Nested
    @DisplayName("GET /api/series/{id}/medias 시리즈 썸네일 조회")
    inner class GetMedia {
        @Test
        @WithMockUser
        @DisplayName("성공 (200)")
        fun getMedia_Success() {
            val response = SeriesMediaResponse(1L, 1L, "series/uuid.png", MediaType.IMAGE)
            given(seriesMediaService.getMedia(1L)).willReturn(response)

            mockMvc
                .perform(get("/api/series/1/medias"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.url").value("series/uuid.png"))
                .andExpect(jsonPath("$.data.seriesId").value(1L))
        }

        @Test
        @WithMockUser
        @DisplayName("미디어 없음 → 200 (data: null)")
        fun getMedia_NotFound() {
            given(seriesMediaService.getMedia(999L)).willReturn(null)

            mockMvc
                .perform(get("/api/series/999/medias"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data").doesNotExist())
        }
    }

    @Nested
    @DisplayName("POST /api/series/{id}/medias 시리즈 썸네일 업로드")
    inner class UploadMedia {
        @Test
        @WithMockUser
        @DisplayName("성공 (201)")
        fun uploadMedia_Success() {
            val response = SeriesMediaResponse(1L, 1L, "series/uuid.png", MediaType.IMAGE)
            val file = MockMultipartFile("file", "thumb.png", "image/png", "content".toByteArray())

            given(securityHelper.actor).willReturn(mockActor)
            given(seriesMediaService.uploadMedia(anyLong(), anyMultipartFile(), anyLong(), any())).willReturn(response)

            mockMvc
                .perform(
                    multipart("/api/series/1/medias")
                        .file(file)
                        .with(csrf()),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.url").value("series/uuid.png"))
        }

        @Test
        @WithMockUser
        @DisplayName("타인 시리즈에 썸네일 업로드 시도 실패 (403)")
        fun uploadMedia_Forbidden() {
            val file = MockMultipartFile("file", "thumb.png", "image/png", "content".toByteArray())
            val otherActor = User(99L, "other@example.com", "다른유저", UserRole.USER)

            given(securityHelper.actor).willReturn(otherActor)
            given(seriesMediaService.uploadMedia(anyLong(), anyMultipartFile(), eq(99L), any()))
                .willThrow(BusinessException(ErrorCode.ACCESS_DENIED))

            mockMvc
                .perform(
                    multipart("/api/series/1/medias")
                        .file(file)
                        .with(csrf()),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.resultCode").value("403-1"))
        }

        @Test
        @WithMockUser
        @DisplayName("시리즈 없음 → 404")
        fun uploadMedia_SeriesNotFound() {
            val file = MockMultipartFile("file", "thumb.png", "image/png", "content".toByteArray())

            given(securityHelper.actor).willReturn(mockActor)
            given(seriesMediaService.uploadMedia(anyLong(), anyMultipartFile(), anyLong(), any()))
                .willThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))

            mockMvc
                .perform(
                    multipart("/api/series/999/medias")
                        .file(file)
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("DELETE /api/series/{id}/medias 시리즈 썸네일 삭제")
    inner class DeleteMedia {
        @Test
        @WithMockUser
        @DisplayName("성공 (200)")
        fun deleteMedia_Success() {
            given(securityHelper.actor).willReturn(mockActor)

            mockMvc
                .perform(
                    delete("/api/series/1/medias")
                        .with(csrf()),
                ).andExpect(status().isOk)
        }

        @Test
        @WithMockUser
        @DisplayName("타인 시리즈 썸네일 삭제 시도 실패 (403)")
        fun deleteMedia_Forbidden() {
            val otherActor = User(99L, "other@example.com", "다른유저", UserRole.USER)
            given(securityHelper.actor).willReturn(otherActor)
            doThrow(BusinessException(ErrorCode.ACCESS_DENIED))
                .`when`(seriesMediaService)
                .deleteMedia(eq(1L), eq(99L), any())

            mockMvc
                .perform(
                    delete("/api/series/1/medias")
                        .with(csrf()),
                ).andExpect(status().isForbidden)
                .andExpect(jsonPath("$.resultCode").value("403-1"))
        }

        @Test
        @WithMockUser
        @DisplayName("미디어 없음 → 404")
        fun deleteMedia_MediaNotFound() {
            given(securityHelper.actor).willReturn(mockActor)
            doThrow(BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .`when`(seriesMediaService)
                .deleteMedia(anyLong(), anyLong(), any())

            mockMvc
                .perform(
                    delete("/api/series/1/medias")
                        .with(csrf()),
                ).andExpect(status().isNotFound)
        }
    }
}
