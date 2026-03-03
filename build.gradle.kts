plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.binaryCompatibility) apply false
}

tasks.register("deploy") {
  group = "publishing"
  description = "Publishes and releases the zui module to Maven Central via Central Portal"
  dependsOn(":zui:publishAndReleaseToMavenCentral")
}
