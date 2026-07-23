plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"

    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
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

jacoco {
    toolVersion = "0.8.13"
}

val jacocoExcludePatterns = listOf(
    "**/dto/**",
    "**/entity/**",
    "**/repository/**",
    "**/config/**",
    "**/init/**",
    "**/base/**",
    "**/*Application*",
    "**/exception/**",
    "**/security/**"
)

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExcludePatterns) }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExcludePatterns) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
