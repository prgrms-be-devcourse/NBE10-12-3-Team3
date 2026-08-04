package com.scommit.domain.post.post.dto

import com.scommit.domain.post.post.entity.PostAccessLevel
import com.scommit.domain.post.post.entity.PublishStatus

// POST /posts 게시글 생성 요청 DTO
// userId는 JWT 토큰에서 추출하므로 포함하지 않음
// title이 null이어도 Bean Validation이 없어 그대로 서비스까지 전달되고 DB NOT NULL 제약
// 위반으로 500-1이 나는 게 실제(버그) 동작이라 title도 nullable로 둔다 — PostControllerE2ETest의
// createPost_nullTitle_actualBehaviorReturns500_1이 이 동작 자체를 검증한다.
data class PostCreateRequest(
    val seriesId: Long?,
    val title: String?,
    val body: String?,
    val publishStatus: PublishStatus,
    val accessLevel: PostAccessLevel,
)
