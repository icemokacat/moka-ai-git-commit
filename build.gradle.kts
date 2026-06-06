import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        pluginVerifier()
    }
    implementation("com.squareup.okhttp3:okhttp:${providers.gradleProperty("okhttpVersion").get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${providers.gradleProperty("serializationVersion").get()}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${providers.gradleProperty("junitVersion").get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    // .env.test.local 에서 KEY=VALUE 읽어 시스템 프로퍼티로 주입
    val envFile = file(".env.test.local")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') && '=' in it }
            .forEach { line ->
                val (key, value) = line.split('=', limit = 2)
                systemProperty(key.trim(), value.trim())
            }
    }
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    // JAR 변경 시 자동 hot-reload 비활성화 — PluginSet.withModule 내부 assertion 오류 방지
    autoReload = false
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    publishing { token = providers.environmentVariable("PUBLISH_TOKEN") }

    pluginVerification {
        ides {
            val typeMap = mapOf(
                "IC" to IntelliJPlatformType.IntellijIdeaCommunity,
                "IU" to IntelliJPlatformType.IntellijIdeaUltimate,
            )
            providers.gradleProperty("verifyIdeVersions").get()
                .split(",")
                .map { it.trim() }
                .forEach { spec ->
                    val dashIdx = spec.indexOf('-')
                    val typeCode = spec.substring(0, dashIdx)
                    val version  = spec.substring(dashIdx + 1)
                    val type = typeMap[typeCode]
                        ?: error("지원하지 않는 IDE 타입 코드: $typeCode (지원 목록: ${typeMap.keys})")
                    ide(type, version)
                }
        }
    }
}
