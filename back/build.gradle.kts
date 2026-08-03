plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    kotlin("plugin.lombok") version "2.3.21"

    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco

    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "back"

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("com.cloudinary:cloudinary-http45:1.39.0")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
}

ktlint {
    version.set("1.8.0")
    ignoreFailures.set(false)
}

detekt {
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

// detekt 내장 컴파일러가 아직 JvmTarget 25를 지원하지 않아 타입 리졸루션 시 에러를 피하기 위해 detekt 태스크에 한해 21 사용
// detekt이 JDK 25의 버전 문자열(예: 25.0.3)을 파싱하지 못해 크래시되는 것을 막기 위해
// Gradle daemon 버전을 21로 고정(gradle/gradle-daemon-jvm.properties) 및 이하에서 detekt CLI의 jvmTarget/jdkHome를 21로 명시.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    jdkHome.set(
        javaToolchains
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .map { it.metadata.installationPath },
    )
}

// detekt가 내부적으로 쓰는 Kotlin 컴파일러 버전을 detekt 1.23.8이 공식 지원하는 버전으로 강제
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(
                io.gitlab.arturbosch.detekt
                    .getSupportedKotlinVersion(),
            )
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}

val jacocoExcludePatterns =
    listOf(
        "**/dto/**",
        "**/entity/**",
        "**/repository/**",
        "**/config/**",
        "**/init/**",
        "**/base/**",
        "**/*Application*",
        "**/exception/**",
        "**/security/**",
    )

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(jacocoExcludePatterns) }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) { exclude(jacocoExcludePatterns) }
            },
        ),
    )
    violationRules {
        isFailOnViolation = false
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
