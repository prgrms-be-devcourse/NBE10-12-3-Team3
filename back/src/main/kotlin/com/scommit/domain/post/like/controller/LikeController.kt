package com.scommit.domain.post.like.controller

import com.scommit.domain.post.like.service.LikeService
import com.scommit.domain.user.user.entity.User
import com.scommit.global.dto.RsData
import com.scommit.global.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts/{postId}/likes")
@Tag(name = "LikeController", description = "게시글 좋아요 API")
class LikeController(
    private val postLikeService: LikeService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "좋아요 추가")
    fun createLike(
        @CurrentUser actor: User,
        @PathVariable postId: Long,
    ): RsData<Void> {
        postLikeService.createLike(postId, actor)
        return RsData("201-1", "좋아요가 추가되었습니다.")
    }

    @DeleteMapping
    @Operation(summary = "좋아요 취소")
    fun deleteLike(
        @CurrentUser actor: User,
        @PathVariable postId: Long,
    ): RsData<Void> {
        postLikeService.deleteLike(postId, actor)
        return RsData("200-1", "좋아요가 취소되었습니다.")
    }
}
