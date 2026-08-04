package com.scommit.domain.media.media.service

import com.scommit.domain.media.media.entity.Media
import org.springframework.web.multipart.MultipartFile

interface MediaService {
    fun uploadMedia(
        file: MultipartFile?,
        category: String,
    ): Media

    fun deleteMedia(mediaId: Long)
}
