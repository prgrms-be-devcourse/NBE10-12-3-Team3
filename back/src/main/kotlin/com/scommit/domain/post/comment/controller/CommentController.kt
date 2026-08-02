package com.scommit.domain.post.comment.controller

import com.scommit.domain.post.comment.dto.CommentCreateRequest
import com.scommit.domain.post.comment.dto.CommentResponse
import com.scommit.domain.post.comment.dto.CommentUpdateRequest
import com.scommit.domain.post.comment.service.CommentService
import com.scommit.global.dto.RsData
import com.scommit.global.security.SecurityHelper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Comment", description = "댓글 관련 API")
@RestController
@RequestMapping("/api/posts/{postId}/comments")
class CommentController(
    private val commentService: CommentService,
    private val securityHelper: SecurityHelper,
) {
    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createComment(
        @PathVariable postId: Long,
        @RequestBody request: CommentCreateRequest,
    ): RsData<CommentResponse> {
        val actor = securityHelper.actor
        val response = commentService.createComment(actor, postId, request.body)
        return RsData("201-1", "댓글이 작성되었습니다.", response)
    }

    @Operation(summary = "댓글 목록 조회", description = "특정 게시글의 댓글 목록을 페이지로 조회합니다.")
    @GetMapping
    fun getComments(
        @PathVariable postId: Long,
        @PageableDefault(size = 10, sort = ["id"]) pageable: Pageable,
    ): RsData<Page<CommentResponse>> {
        val response = commentService.getComments(postId, pageable)
        return RsData("200-1", "댓글 목록입니다.", response)
    }

    @Operation(summary = "댓글 수정", description = "댓글을 수정합니다.")
    @PutMapping("/{id}")
    @Suppress("UnusedParameter") // postId는 리소스 경로 중첩을 위한 것으로 로직에는 사용되지 않음
    fun updateComment(
        @PathVariable postId: Long,
        @PathVariable id: Long,
        @RequestBody request: CommentUpdateRequest,
    ): RsData<CommentResponse> {
        val actor = securityHelper.actor
        val response = commentService.updateComment(actor, id, request.body)
        return RsData("200-1", "댓글이 수정되었습니다.", response)
    }

    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다.")
    @DeleteMapping("/{id}")
    @Suppress("UnusedParameter") // postId는 리소스 경로 중첩을 위한 것으로 로직에는 사용되지 않음
    fun deleteComment(
        @PathVariable postId: Long,
        @PathVariable id: Long,
    ): RsData<Void> {
        val actor = securityHelper.actor
        commentService.deleteComment(actor, id)
        return RsData("200-1", "댓글이 삭제되었습니다.")
    }
}
