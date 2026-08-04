package com.scommit.domain.user.usermedia.repository

import com.scommit.domain.user.user.entity.User
import com.scommit.domain.user.usermedia.entity.UserMedia
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserMediaRepository : JpaRepository<UserMedia, Long> {
    fun findByUser(user: User): UserMedia?

    // N+1 방지: 여러 유저의 프로필 미디어를 한 번에 조회
    fun findAllByUserIn(users: List<User>): List<UserMedia>
}
