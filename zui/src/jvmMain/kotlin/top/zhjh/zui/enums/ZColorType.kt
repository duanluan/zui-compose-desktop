package top.zhjh.zui.enums

/**
 * 按钮颜色类型枚举。
 *
 * 说明：
 * - 该枚举仅描述“颜色语义”，不包含结构/表现类型；
 * - 类似 `TEXT` 这类表现类型应在具体组件中定义（例如 `ZButtonType.Text`）。
 */
enum class ZColorType {
  DEFAULT, PRIMARY, SUCCESS, INFO, WARNING, DANGER
}
