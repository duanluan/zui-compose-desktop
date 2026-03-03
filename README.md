# ZUI Compose Desktop

`top.zhjh` 组件库项目（Compose Desktop/JVM），从 `zutil-desktop` 的 `zui` 组件中独立拆分。

## Module

- `:zui` - 组件库发布模块（不包含 demo）

## Quick Start

```bash
./gradlew :zui:build
```

## Dependency

```kotlin
implementation("top.zhjh:zui-compose-desktop:0.1.0")
```

## Notes

- `zui/demo` 仍保留在 `zutil-desktop` 项目中作为示例与宣传，不在本库发布。
