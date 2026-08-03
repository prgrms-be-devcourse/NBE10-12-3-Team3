package com.scommit.global.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    companion object {
        private const val DEFAULT_PAGE_NUMBER = 0
        private const val DEFAULT_PAGE_SIZE = 10
    }

    // Swagger UI 우상단 Authorize 버튼 활성화 — Bearer JWT 토큰 입력 후 인증 필요 API 테스트 가능
    @Bean
    fun openAPI(): OpenAPI {
        val bearerAuth =
            SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .`in`(SecurityScheme.In.HEADER)
                .name("Authorization")

        val securityRequirement = SecurityRequirement().addList("bearerAuth")

        return OpenAPI()
            .info(
                Info()
                    .title("SCommit API")
                    .description("개발자 블로그 플랫폼 SCommit API 명세서")
                    .version("v1.0.0"),
            ).components(Components().addSecuritySchemes("bearerAuth", bearerAuth))
            .addSecurityItem(securityRequirement)
    }

    // Swagger에서 Pageable이 JSON 객체로 표시되는 문제를 막기 위해 page, size, sort 파라미터로 직접 등록
    @Bean
    fun swaggerPageableCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.paths.values.forEach { pathItem ->
                pathItem.readOperations().forEach { operation ->
                    if (operation.parameters == null) return@forEach

                    val hasPageable =
                        operation.parameters
                            .any { p -> "pageable" == p.name }

                    operation.parameters
                        .removeIf { param -> "pageable" == param.name || "sort" == param.name }

                    if (hasPageable) {
                        val pageParams =
                            listOf(
                                Parameter()
                                    .`in`("query")
                                    .name("page")
                                    .description("페이지 번호 (0부터 시작)")
                                    .schema(IntegerSchema().example(DEFAULT_PAGE_NUMBER)),
                                Parameter()
                                    .`in`("query")
                                    .name("size")
                                    .description("페이지 크기")
                                    .schema(IntegerSchema().example(DEFAULT_PAGE_SIZE)),
                                Parameter()
                                    .`in`("query")
                                    .name("sort")
                                    .description("정렬 (예: id,desc)")
                                    .schema(StringSchema().example("id,desc")),
                            )
                        operation.parameters.addAll(pageParams)
                    }
                }
            }
        }
}
