package com.scommit.global.config

import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("prod")
class CloudinaryConfig {
    @Bean
    fun cloudinary(
        @Value("\${cloudinary.cloud-name}") cloudName: String,
        @Value("\${cloudinary.api-key}") apiKey: String,
        @Value("\${cloudinary.api-secret}") apiSecret: String,
    ) = Cloudinary(
        ObjectUtils.asMap(
            "cloud_name",
            cloudName,
            "api_key",
            apiKey,
            "api_secret",
            apiSecret,
            "secure",
            true,
        ),
    )
}
