package com.scommit.domain.series.series.controller

import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.service.PostService
import com.scommit.domain.series.series.dto.SeriesCreateRequest
import com.scommit.domain.series.series.dto.SeriesListResponse
import com.scommit.domain.series.series.dto.SeriesResponse
import com.scommit.domain.series.series.dto.SeriesUpdateRequest
import com.scommit.domain.series.series.service.SeriesService
import com.scommit.domain.series.seriesmedia.dto.SeriesMediaResponse
import com.scommit.domain.series.seriesmedia.service.SeriesMediaService
import com.scommit.domain.user.user.entity.User
import com.scommit.global.dto.PageResponse
import com.scommit.global.dto.RsData
import com.scommit.global.security.CurrentUser
import com.scommit.global.security.SecurityUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/series")
@Tag(name = "SeriesController", description = "API 시리즈 컨트롤러")
@Suppress("TooManyFunctions")
class SeriesController(
    private val seriesService: SeriesService,
    private val seriesMediaService: SeriesMediaService,
    private val postService: PostService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "새 시리즈 생성")
    fun createSeries(
        @CurrentUser actor: User,
        @RequestBody @Valid request: SeriesCreateRequest,
    ): RsData<SeriesResponse> {
        val response = seriesService.createSeries(request.title!!, request.body, actor.id!!)
        return RsData("201-1", "시리즈를 생성하였습니다.", response)
    }

    @GetMapping
    @Operation(summary = "시리즈 전체 조회")
    fun getSeriesList(
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<Slice<SeriesListResponse>> {
        val response = seriesService.getSeriesSlice(pageable)
        return RsData("200-1", "시리즈를 전체 조회하였습니다.", response)
    }

    @GetMapping("/search")
    @Operation(summary = "시리즈 제목 검색")
    fun searchSeries(
        @RequestParam keyword: String,
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<PageResponse<SeriesListResponse>> {
        val response = PageResponse(seriesService.searchSeries(keyword, pageable))
        return RsData("200-1", "시리즈 검색 결과입니다.", response)
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "특정 유저 시리즈 조회")
    fun getUserSeriesList(
        @PathVariable userId: Long,
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<PageResponse<SeriesListResponse>> {
        val response = PageResponse(seriesService.getSeriesList(userId, pageable))
        return RsData("200-1", "유저 시리즈를 조회하였습니다.", response)
    }

    @GetMapping("/me")
    @Operation(summary = "내 시리즈 조회")
    fun getMySeriesList(
        @CurrentUser actor: User,
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<PageResponse<SeriesListResponse>> {
        val response = PageResponse(seriesService.getSeriesList(actor.id!!, pageable))
        return RsData("200-1", "내 시리즈를 조회하였습니다.", response)
    }

    @GetMapping("/{id}/posts")
    @Operation(summary = "시리즈 내 게시글 목록 조회")
    fun getSeriesPosts(
        @AuthenticationPrincipal securityUser: SecurityUser?,
        @PathVariable id: Long,
    ): RsData<List<PostListResponse>> {
        val actor = securityUser?.user
        val response = postService.getPostsBySeriesId(id, actor)
        return RsData("200-1", "시리즈 게시글 목록입니다.", response)
    }

    @PostMapping("/{id}/posts/{postId}")
    @Operation(summary = "시리즈에 포스트 추가")
    fun addPostToSeries(
        @CurrentUser actor: User,
        @PathVariable id: Long,
        @PathVariable postId: Long,
    ): RsData<Void> {
        postService.addPostToSeries(postId, id, actor)
        return RsData("200-1", "포스트가 시리즈에 추가되었습니다.")
    }

    @DeleteMapping("/{id}/posts/{postId}")
    @Operation(summary = "시리즈에서 포스트 제거")
    fun removePostFromSeries(
        @CurrentUser actor: User,
        @PathVariable id: Long,
        @PathVariable postId: Long,
    ): RsData<Void> {
        postService.removePostFromSeries(postId, id, actor)
        return RsData("200-1", "포스트가 시리즈에서 제거되었습니다.")
    }

    @GetMapping("/{id}")
    @Operation(summary = "시리즈 상세 조회")
    fun getSeries(
        @PathVariable id: Long,
    ): RsData<SeriesResponse> {
        val response = seriesService.getSeries(id)
        return RsData("200-1", "시리즈를 상세 조회하였습니다.", response)
    }

    @PutMapping("/{id}")
    @Operation(summary = "시리즈 수정")
    fun updateSeries(
        @CurrentUser actor: User,
        @PathVariable id: Long,
        @RequestBody @Valid request: SeriesUpdateRequest,
    ): RsData<SeriesResponse> {
        val response =
            seriesService.updateSeries(
                id,
                request.title!!,
                request.body,
                actor.id!!,
                actor.role,
            )
        return RsData("200-1", "시리즈를 수정하였습니다.", response)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "시리즈 삭제")
    fun deleteSeries(
        @CurrentUser actor: User,
        @PathVariable id: Long,
    ): RsData<Void> {
        seriesService.deleteSeries(id, actor.id!!, actor.role)
        return RsData("200-1", "시리즈가 삭제되었습니다.")
    }

    @PostMapping(value = ["/{id}/medias"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "시리즈 썸네일 생성")
    fun uploadMedia(
        @CurrentUser actor: User,
        @PathVariable id: Long,
        @RequestPart file: MultipartFile,
    ): RsData<SeriesMediaResponse> {
        val response = seriesMediaService.uploadMedia(id, file, actor.id!!, actor.role)
        return RsData("201-1", "썸네일을 생성하였습니다.", response)
    }

    @GetMapping("/{id}/medias")
    @Operation(summary = "시리즈 썸네일 조회")
    fun getMedia(
        @PathVariable id: Long,
    ): RsData<SeriesMediaResponse> {
        val response = seriesMediaService.getMedia(id)
        return RsData("200-1", "썸네일을 조회하였습니다.", response)
    }

    @DeleteMapping("/{id}/medias")
    @Operation(summary = "시리즈 썸네일 삭제")
    fun deleteMedia(
        @CurrentUser actor: User,
        @PathVariable id: Long,
    ): RsData<Void> {
        seriesMediaService.deleteMedia(id, actor.id!!, actor.role)
        return RsData("200-1", "썸네일이 삭제되었습니다.")
    }
}
