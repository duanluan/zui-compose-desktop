plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.dokka)
  alias(libs.plugins.binaryCompatibility)
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
