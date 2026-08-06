package com.scommit.domain.post.postmedia.repository

import com.scommit.domain.post.post.entity.Post
import com.scommit.domain.post.postmedia.entity.PostMedia
import com.scommit.domain.post.postmedia.entity.PostMediaType
import org.springframework.data.jpa.repository.JpaRepository

interface PostMediaRepository : JpaRepository<PostMedia, Long> {
    fun findByPostAndType(
        post: Post,
        type: PostMediaType,
    ): PostMedia?

    fun findByPostInAndType(
        posts: Collection<Post>,
        type: PostMediaType,
    ): List<PostMedia>

    fun findAllByPost(post: Post): List<PostMedia>
}
