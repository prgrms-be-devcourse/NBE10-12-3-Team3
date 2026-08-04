package com.scommit.domain.post.bookmark.controller

import com.scommit.domain.post.bookmark.service.BookmarkService
import com.scommit.domain.post.post.dto.PostListResponse
import com.scommit.global.dto.PageResponse
import com.scommit.global.dto.RsData
import com.scommit.global.exception.BusinessException
import com.scommit.global.exception.ErrorCode
import com.scommit.global.security.SecurityHelper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "BookmarkController", description = "게시글 북마크 API")
class BookmarkController(
    private val postBookmarkService: BookmarkService,
    private val securityHelper: SecurityHelper,
) {
    @PostMapping("/posts/{postId}/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "북마크 추가")
    fun createBookmark(
        @PathVariable postId: Long,
    ): RsData<Void> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        postBookmarkService.createBookmark(postId, actor)
        return RsData("201-1", "북마크가 추가되었습니다.")
    }

    @GetMapping("/bookmarks/me")
    @Operation(summary = "내 북마크 목록 조회")
    fun getMyBookmarks(
        @PageableDefault(size = 10, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): RsData<PageResponse<PostListResponse>> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        val response = PageResponse(postBookmarkService.getMyBookmarks(actor, pageable))
        return RsData("200-1", "북마크한 게시글 목록입니다.", response)
    }

    @DeleteMapping("/posts/{postId}/bookmarks")
    @Operation(summary = "북마크 취소")
    fun deleteBookmark(
        @PathVariable postId: Long,
    ): RsData<Void> {
        val actor = securityHelper.actor ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        postBookmarkService.deleteBookmark(postId, actor)
        return RsData("200-1", "북마크가 취소되었습니다.")
    }
}
