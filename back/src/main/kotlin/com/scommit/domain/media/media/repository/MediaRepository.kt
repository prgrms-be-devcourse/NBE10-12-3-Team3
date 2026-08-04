package com.scommit.domain.media.media.repository

import com.scommit.domain.media.media.entity.Media
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MediaRepository : JpaRepository<Media, Long>
