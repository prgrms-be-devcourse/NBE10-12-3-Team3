package com.scommit.domain.post.post.controller

import com.scommit.domain.post.post.dto.PostCreateRequest
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.domain.post.post.dto.PostResponse
import com.scommit.domain.post.post.dto.PostUpdateRequest
import com.scommit.domain.post.post.service.PostService
import com.scommit.domain.post.postmedia.dto.PostMediaResponse
import com.scommit.domain.post.postmedia.entity.PostMediaType
import com.scommit.domain.post.postmedia.service.PostMediaService
import com.scommit.global.dto.RsData
import com.scommit.global.security.SecurityHelper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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

@Tag(name = "Post", description = "게시글 관련 API")
@RestController
@RequestMapping("/api/posts")
@Suppress("TooManyFunctions")
class PostController(
    private val postService: PostService,
    private val postMediaService: PostMediaService,
    private val securityHelper: SecurityHelper,
) {
    // GET /api/posts/me 내가 쓴 게시글 조회 - 페이지 번호 방식
    @Operation(summary = "내 게시글 조회", description = "로그인한 유저가 작성한 게시글 목록을 조회합니다.")
    @GetMapping("/me")
    fun getMyPosts(
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<Page<PostListResponse>> {
        val actor = securityHelper.actor
        val response = postService.getMyPosts(actor, pageable)
        return RsData("200-1", "내가 쓴 게시글 목록입니다.", response)
    }

    // POST /api/posts 게시글 생성
    @Operation(summary = "게시글 생성", description = "새로운 게시글을 생성합니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createPost(
        @RequestBody request: PostCreateRequest,
    ): RsData<PostResponse> {
        val actor = securityHelper.actor
        val response =
            postService.createPost(
                actor,
                request.title,
                request.body,
                request.publishStatus,
                request.accessLevel,
                request.seriesId,
            )
        return RsData("201-1", "게시글이 생성되었습니다.", response)
    }

    // GET /api/posts/users/{userId} 특정 유저 게시글 조회 - 번호 페이지네이션 (프로필 화면)
    @Operation(summary = "유저 게시글 목록 조회", description = "특정 유저가 작성한 게시글 목록을 번호 페이지네이션으로 조회합니다.")
    @GetMapping("/users/{userId}")
    fun getUserPosts(
        @PathVariable userId: Long,
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<Page<PostListResponse>> {
        val actor = securityHelper.actor
        val response = postService.getUserPosts(userId, actor, pageable)
        return RsData("200-1", "유저의 게시글 목록입니다.", response)
    }

    // GET /api/posts/search 키워드 검색
    @Operation(summary = "게시글 검색", description = "제목 또는 본문에 키워드가 포함된 PUBLIC 게시글을 검색합니다.")
    @GetMapping("/search")
    fun searchPosts(
        @RequestParam(required = false, defaultValue = "") keyword: String,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<Page<PostListResponse>> {
        if (keyword.isBlank()) {
            return RsData("200-1", "게시글 검색 결과입니다.", Page.empty(pageable))
        }
        val response = postService.searchPosts(keyword, pageable)
        return RsData("200-1", "게시글 검색 결과입니다.", response)
    }

    // GET /api/posts 홈페이지 전체 조회 - 무한 스크롤
    @Operation(summary = "게시글 전체 조회", description = "전체 게시글 목록을 조회합니다. creatorId 입력 시 특정 유저의 게시글만 조회합니다.")
    @GetMapping
    fun getPosts(
        @RequestParam(required = false) creatorId: Long?,
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<Slice<PostListResponse>> {
        val actor = securityHelper.actor
        val response = postService.getPosts(creatorId, actor, pageable)
        return RsData("200-1", "게시글 목록입니다.", response)
    }

    // GET /api/posts/{id} 게시글 상세 조회
    @Operation(summary = "게시글 상세 조회", description = "게시글 ID로 특정 게시글의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    fun getPost(
        @PathVariable id: Long,
    ): RsData<PostResponse> {
        val actor = securityHelper.actor
        val response = postService.getPost(id, actor)
        return RsData("200-1", "게시글 상세 정보입니다.", response)
    }

    // PUT /api/posts/{id} 게시글 수정
    @Operation(summary = "게시글 수정", description = "게시글 ID로 특정 게시글을 수정합니다.")
    @PutMapping("/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @RequestBody request: PostUpdateRequest,
    ): RsData<PostResponse> {
        val actor = securityHelper.actor
        val response =
            postService.updatePost(
                actor,
                id,
                request.title,
                request.body,
                request.publishStatus,
                request.accessLevel,
                request.seriesId,
            )
        return RsData("200-1", "게시글이 수정되었습니다.", response)
    }

    // DELETE /api/posts/{id} 게시글 삭제
    @Operation(summary = "게시글 삭제", description = "게시글 ID로 특정 게시글을 삭제합니다.")
    @DeleteMapping("/{id}")
    fun deletePost(
        @PathVariable id: Long,
    ): RsData<Void> {
        val actor = securityHelper.actor
        postService.deletePost(actor, id)
        return RsData("200-1", "게시글이 삭제되었습니다.")
    }

    // POST /api/posts/{id}/medias 게시글 미디어 추가
    @Operation(summary = "게시글 미디어 생성", description = "특정 게시글의 미디어를 생성합니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{id}/medias", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadMedia(
        @PathVariable id: Long,
        @RequestPart file: MultipartFile,
        @RequestParam type: PostMediaType,
    ): RsData<PostMediaResponse> {
        val response = postMediaService.uploadMedia(id, file, type)
        return RsData("201-1", "게시글 미디어가 추가되었습니다.", response)
    }

    // GET /api/posts/{id}/medias 게시글 미디어 전체 조회
    @Operation(summary = "게시글 미디어 전체 조회", description = "특정 게시글의 모든 미디어를 조회합니다.")
    @GetMapping("/{id}/medias")
    fun getMediaList(
        @PathVariable id: Long,
    ): RsData<List<PostMediaResponse>> {
        val response = postMediaService.getMediaList(id)
        return RsData("200-1", "게시글 미디어 목록입니다.", response)
    }

    // GET /api/posts/{id}/medias/thumbnail 게시글 썸네일 조회
    @Operation(summary = "게시글 썸네일 조회", description = "특정 게시글의 썸네일을 조회합니다.")
    @GetMapping("/{id}/medias/thumbnail")
    fun getThumbnail(
        @PathVariable id: Long,
    ): RsData<PostMediaResponse?> {
        val response = postMediaService.getThumbnail(id)
        return RsData("200-1", "게시글 썸네일입니다.", response)
    }

    // DELETE /api/posts/{id}/medias/{postMediaId} 게시글 미디어 삭제
    @Operation(summary = "게시글 미디어 삭제", description = "특정 게시글의 특정 미디어를 삭제합니다.")
    @DeleteMapping("/{id}/medias/{postMediaId}")
    fun deleteMedia(
        @PathVariable id: Long,
        @PathVariable postMediaId: Long,
    ): RsData<Void> {
        postMediaService.deleteMedia(id, postMediaId)
        return RsData("200-1", "게시글 미디어가 삭제되었습니다.")
    }
}
