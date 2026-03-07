plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.dokka)
  alias(libs.plugins.binaryCompatibility)
}

plugins.withId("signing") {
  extensions.configure<org.gradle.plugins.signing.SigningExtension> {
    val signingInMemoryKey = providers.gradleProperty("signingInMemoryKey").orNull
    val signingInMemoryKeyId = providers.gradleProperty("signingInMemoryKeyId").orNull?.trim()
    val signingInMemoryKeyPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull

    // CI 有 in-memory key 时优先走内存签名；本地开发环境回退到 gpg 命令。
    if (signingInMemoryKey.isNullOrBlank()) {
      useGpgCmd()
    } else {
      val normalizedKeyId = signingInMemoryKeyId?.takeIf { keyId ->
        keyId.matches(Regex("^(0x)?[0-9a-fA-F]{8,16}$"))
      }
      if (normalizedKeyId == null) {
        // keyId 缺失或格式非法时改走无 keyId 模式，避免 CI 因格式校验失败。
        useInMemoryPgpKeys(signingInMemoryKey, signingInMemoryKeyPassword)
      } else {
        useInMemoryPgpKeys(
          normalizedKeyId,
          signingInMemoryKey,
          signingInMemoryKeyPassword
        )
      }
    }
  }
}

kotlin {
  jvm()
  jvmToolchain(17)

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material)
        implementation(libs.compose.ui)
        implementation(libs.icons.feather)
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}
