package com.scommit.global.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
@Profile("!prod")
class MediaConfig : WebMvcConfigurer {
    @Value("\${file.path}")
    private lateinit var mediaPath: String

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val locationUri =
            Paths
                .get(mediaPath)
                .toAbsolutePath()
                .toUri()
                .toString()

        registry
            .addResourceHandler("/media/**")
            .addResourceLocations(locationUri)
    }
}
