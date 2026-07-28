plugins {
    // 로컬에 없는 JDK 버전(예: gradle-daemon-jvm.properties가 요구하는 21, java.toolchain의 25)을
    // foojay Disco API를 통해 자동으로 찾아 다운로드해주는 toolchain resolver. detekt 요구 버전 때문에 추가
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "back"
