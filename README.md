# ZUI Compose Desktop

`top.zhjh` 组件库项目（Compose Desktop/JVM），从 `zutil-desktop` 的 `zui` 组件中独立拆分。

## Module

- `:zui` - 组件库发布模块（不包含 demo）

## Quick Start

```bash
./gradlew :zui:build
```

## Publish To Maven Central (Central Portal)

先准备 Central Portal 用户 Token 和 GPG 密钥，然后使用以下环境变量（Gradle 项目属性方式）：

- `ORG_GRADLE_PROJECT_mavenCentralUsername`
- `ORG_GRADLE_PROJECT_mavenCentralPassword`
- `ORG_GRADLE_PROJECT_signingInMemoryKey`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyId` (optional)

发布命令：

```bash
# 发布并自动 release
./gradlew deploy

# 等价命令
./gradlew :zui:publishAndReleaseToMavenCentral
```

## Pre-publish Checks

```bash
./gradlew :zui:build
./gradlew :zui:apiCheck
./gradlew :zui:check
```

## Notes

- `zui/demo` 仍保留在 `zutil-desktop` 项目中作为示例与宣传，不在本库发布。
- 首次发布前请把 `gradle.properties` 中的 `POM_URL`/`POM_SCM_*` 替换成你的真实仓库地址。
