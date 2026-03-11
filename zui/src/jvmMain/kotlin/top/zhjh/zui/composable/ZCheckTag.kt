package top.zhjh.zui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.zhjh.zui.enums.ZColorType
import top.zhjh.zui.theme.isAppInDarkTheme

/**
 * ZCheckTag（可选中的标签）。
 *
 * 与普通 Tag 的区别：
 * - 拥有 `checked` 状态；
 * - 内置 `Role.Checkbox` 语义，支持键盘切换与读屏识别；
 * - 配色强调“已选中/未选中/禁用/交互态”之间的层次。
 */
@Composable
fun ZCheckTag(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit = {},
  text: String,
  modifier: Modifier = Modifier,
  type: ZColorType = ZColorType.PRIMARY,
  enabled: Boolean = true
) {
  // 文本便捷重载：统一单行省略与最大宽度约束。
  ZCheckTag(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    type = type,
    enabled = enabled
  ) {
    ZText(
      text = text,
      modifier = Modifier.widthIn(max = ZCheckTagDefaults.TextMaxWidth),
      color = LocalContentColor.current,
      fontSize = ZCheckTagDefaults.FontSize,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/**
 * 插槽版 ZCheckTag。
 *
 * 说明：
 * - 允许调用方自定义标签内容（图标、文本组合等）；
 * - 交互行为（hover/focus/toggleable）与样式逻辑统一由组件内部维护。
 */
@Composable
fun ZCheckTag(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier,
  type: ZColorType = ZColorType.PRIMARY,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit
) {
  val disabled = !enabled
  // 1) 收集环境与交互状态。
  val isDarkTheme = isAppInDarkTheme()
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val isFocused by interactionSource.collectIsFocusedAsState()
  // 2) 根据 type/checked/enabled/交互态综合解析视觉样式。
  val style = remember(type, checked, enabled, isHovered, isFocused, isDarkTheme) {
    getZCheckTagStyle(
      type = type,
      checked = checked,
      disabled = disabled,
      isHovered = isHovered,
      isFocused = isFocused,
      isDarkTheme = isDarkTheme
    )
  }

  CompositionLocalProvider(LocalContentColor provides style.textColor) {
    // 桌面端焦点态采用更粗边框，提升键盘导航可见性。
    val borderWidth = if (enabled && isFocused) 2.dp else 1.dp
    Row(
      modifier = modifier
        .defaultMinSize(minHeight = ZCheckTagDefaults.MinHeight)
        .clip(ZCheckTagDefaults.Shape)
        .background(style.backgroundColor)
        .border(borderWidth, style.borderColor, ZCheckTagDefaults.Shape)
        .then(
          if (enabled) {
            Modifier
              // 悬停反馈 + 键盘可聚焦 + 手型光标，保证桌面端可交互感知。
              .hoverable(interactionSource = interactionSource)
              .focusable(interactionSource = interactionSource)
              .pointerHoverIcon(PointerIcon.Hand)
              // toggleable 会自动维护 checked 语义并处理标准键盘切换行为。
              .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox
              )
          } else {
            Modifier
          }
        )
        .padding(ZCheckTagDefaults.ContentPadding),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
      content = content
    )
  }
}

@Immutable
private data class ZCheckTagStyle(
  /** 背景色。 */
  val backgroundColor: Color,

  /** 边框色。 */
  val borderColor: Color,

  /** 内容色（文本/图标）。 */
  val textColor: Color
)

@Immutable
private data class ZCheckTagPalette(
  /** 已选中态基础背景色。 */
  val backgroundColor: Color,

  /** 已选中态基础边框色。 */
  val borderColor: Color,

  /** 已选中态基础文字色。 */
  val textColor: Color
)

/**
 * 解析 CheckTag 的最终视觉样式。
 *
 * 规则顺序：
 * 1. checked 分支优先；
 * 2. disabled 覆盖交互；
 * 3. hover/focus 在基础色上做轻量混色增强；
 * 4. 未选中态按主题回落到默认中性色。
 */
private fun getZCheckTagStyle(
  type: ZColorType,
  checked: Boolean,
  disabled: Boolean,
  isHovered: Boolean,
  isFocused: Boolean,
  isDarkTheme: Boolean
): ZCheckTagStyle {
  if (checked) {
    // 已选中时先取主题色底板，再叠加 disabled / hover / focus 的细节变化。
    val palette = checkedPalette(type, isDarkTheme)
    if (disabled) {
      return if (isDarkTheme) {
        ZCheckTagStyle(
          backgroundColor = palette.backgroundColor.copy(alpha = 0.5f),
          borderColor = palette.borderColor.copy(alpha = 0.5f),
          textColor = Color(0xff8d9095)
        )
      } else {
        ZCheckTagStyle(
          backgroundColor = lerp(palette.backgroundColor, Color.White, 0.35f),
          borderColor = lerp(palette.borderColor, Color.White, 0.35f),
          textColor = Color(0xffa8abb2)
        )
      }
    }

    if (isHovered || isFocused) {
      return if (isDarkTheme) {
        ZCheckTagStyle(
          backgroundColor = lerp(
            palette.backgroundColor,
            Color.White,
            if (isFocused) 0.12f else 0.08f
          ),
          borderColor = lerp(
            palette.borderColor,
            Color.White,
            if (isFocused) 0.18f else 0.08f
          ),
          textColor = palette.textColor
        )
      } else {
        ZCheckTagStyle(
          backgroundColor = lerp(
            palette.backgroundColor,
            Color.Black,
            if (isFocused) 0.06f else 0.04f
          ),
          borderColor = lerp(
            palette.borderColor,
            Color.Black,
            if (isFocused) 0.10f else 0.04f
          ),
          textColor = palette.textColor
        )
      }
    }

    return ZCheckTagStyle(
      backgroundColor = palette.backgroundColor,
      borderColor = palette.borderColor,
      textColor = palette.textColor
    )
  }

  // 未选中 + 禁用：直接使用固定禁用色，不响应交互。
  if (disabled) {
    return if (isDarkTheme) {
      ZCheckTagStyle(
        backgroundColor = Color(0xff202121),
        borderColor = Color(0xff2d2d2f),
        textColor = Color(0xff8d9095)
      )
    } else {
      ZCheckTagStyle(
        backgroundColor = Color(0xfff4f4f5),
        borderColor = Color(0xffebeef5),
        textColor = Color(0xffa8abb2)
      )
    }
  }

  if (isHovered || isFocused) {
    return uncheckedInteractiveStyle(type = type, isDarkTheme = isDarkTheme, isFocused = isFocused)
  }

  return if (isDarkTheme) {
    ZCheckTagStyle(
      backgroundColor = Color(0xff202121),
      borderColor = Color(0xff2d2d2f),
      textColor = Color(0xffa6a9ad)
    )
  } else {
    ZCheckTagStyle(
      backgroundColor = Color(0xfff4f4f5),
      borderColor = Color(0xffebeef5),
      textColor = Color(0xff909399)
    )
  }
}

/**
 * 已选中态基础调色板。
 *
 * 这里的颜色与按钮体系保持一致，确保同一语义色在组件之间观感统一。
 */
private fun checkedPalette(type: ZColorType, isDarkTheme: Boolean): ZCheckTagPalette {
  if (isDarkTheme) {
    return when (type) {
      ZColorType.DEFAULT -> ZCheckTagPalette(
        backgroundColor = Color(0xff202121),
        borderColor = Color(0xff2d2d2f),
        textColor = Color(0xffa6a9ad)
      )
      ZColorType.PRIMARY -> ZCheckTagPalette(
        backgroundColor = Color(0xff18222c),
        borderColor = Color(0xff2a598a),
        textColor = Color(0xff409eff)
      )
      ZColorType.SUCCESS -> ZCheckTagPalette(
        backgroundColor = Color(0xff1c2518),
        borderColor = Color(0xff3e6b27),
        textColor = Color(0xff67c23a)
      )
      ZColorType.INFO -> ZCheckTagPalette(
        backgroundColor = Color(0xff2b2d31),
        borderColor = Color(0xff6b6f75),
        textColor = Color(0xffd2d6dd)
      )
      ZColorType.WARNING -> ZCheckTagPalette(
        backgroundColor = Color(0xff292218),
        borderColor = Color(0xff7d5b28),
        textColor = Color(0xffe6a23c)
      )
      ZColorType.DANGER -> ZCheckTagPalette(
        backgroundColor = Color(0xff2b1d1d),
        borderColor = Color(0xff854040),
        textColor = Color(0xfff56c6c)
      )
    }
  }

  return when (type) {
    ZColorType.DEFAULT -> ZCheckTagPalette(
      backgroundColor = Color(0xfff4f4f5),
      borderColor = Color(0xffe9e9eb),
      textColor = Color(0xff909399)
    )
    ZColorType.PRIMARY -> ZCheckTagPalette(
      backgroundColor = Color(0xffecf5ff),
      borderColor = Color(0xffd9ecff),
      textColor = Color(0xff409eff)
    )
    ZColorType.SUCCESS -> ZCheckTagPalette(
      backgroundColor = Color(0xfff0f9eb),
      borderColor = Color(0xffe1f3d8),
      textColor = Color(0xff67c23a)
    )
    ZColorType.INFO -> ZCheckTagPalette(
      backgroundColor = Color(0xffe9e9eb),
      borderColor = Color(0xffd4d4d8),
      textColor = Color(0xff909399)
    )
    ZColorType.WARNING -> ZCheckTagPalette(
      backgroundColor = Color(0xfffdf6ec),
      borderColor = Color(0xfffaecd8),
      textColor = Color(0xffe6a23c)
    )
    ZColorType.DANGER -> ZCheckTagPalette(
      backgroundColor = Color(0xfffef0f0),
      borderColor = Color(0xfffde2e2),
      textColor = Color(0xfff56c6c)
    )
  }
}

/**
 * 未选中态交互样式（Hover / Focus）。
 *
 * 说明：
 * - Focus 比 Hover 更强调，以便键盘用户快速定位；
 * - 暗色/亮色分别使用不同的背景与边框梯度，避免发灰或过曝。
 */
private fun uncheckedInteractiveStyle(
  type: ZColorType,
  isDarkTheme: Boolean,
  isFocused: Boolean
): ZCheckTagStyle {
  if (isDarkTheme) {
    return when (type) {
      ZColorType.DEFAULT, ZColorType.PRIMARY -> ZCheckTagStyle(
        backgroundColor = if (isFocused) Color(0xff1f2b37) else Color(0xff18222c),
        borderColor = if (isFocused) Color(0xff3b78b8) else Color(0xff2a598a),
        textColor = Color(0xff409eff)
      )
      ZColorType.SUCCESS -> ZCheckTagStyle(
        backgroundColor = if (isFocused) Color(0xff202b1a) else Color(0xff1c2518),
        borderColor = if (isFocused) Color(0xff4e8e2f) else Color(0xff3e6b27),
        textColor = Color(0xff67c23a)
      )
      ZColorType.INFO -> ZCheckTagStyle(
        backgroundColor = if (isFocused) Color(0xff2b2d31) else Color(0xff26282c),
        borderColor = if (isFocused) Color(0xff6f7278) else Color(0xff5d6066),
        textColor = Color(0xffcfd3dc)
      )
      ZColorType.WARNING -> ZCheckTagStyle(
        backgroundColor = if (isFocused) Color(0xff2f2518) else Color(0xff292218),
        borderColor = if (isFocused) Color(0xffa77730) else Color(0xff7d5b28),
        textColor = Color(0xffe6a23c)
      )
      ZColorType.DANGER -> ZCheckTagStyle(
        backgroundColor = if (isFocused) Color(0xff311f1f) else Color(0xff2b1d1d),
        borderColor = if (isFocused) Color(0xffb25252) else Color(0xff854040),
        textColor = Color(0xfff56c6c)
      )
    }
  }

  return when (type) {
    ZColorType.DEFAULT, ZColorType.PRIMARY -> ZCheckTagStyle(
      backgroundColor = if (isFocused) Color(0xffe5f1ff) else Color(0xffecf5ff),
      borderColor = if (isFocused) Color(0xffb3d8ff) else Color(0xffd9ecff),
      textColor = Color(0xff409eff)
    )
    ZColorType.SUCCESS -> ZCheckTagStyle(
      backgroundColor = if (isFocused) Color(0xffe8f6e0) else Color(0xfff0f9eb),
      borderColor = if (isFocused) Color(0xff95d475) else Color(0xffb3e19d),
      textColor = Color(0xff67c23a)
    )
    ZColorType.INFO -> ZCheckTagStyle(
      backgroundColor = if (isFocused) Color(0xffeceef2) else Color(0xfff1f2f5),
      borderColor = if (isFocused) Color(0xffbabdc3) else Color(0xffc9ccd2),
      textColor = Color(0xff909399)
    )
    ZColorType.WARNING -> ZCheckTagStyle(
      backgroundColor = if (isFocused) Color(0xfff8ebd7) else Color(0xfffdf6ec),
      borderColor = if (isFocused) Color(0xffebb563) else Color(0xfff3d19e),
      textColor = Color(0xffe6a23c)
    )
    ZColorType.DANGER -> ZCheckTagStyle(
      backgroundColor = if (isFocused) Color(0xfffde2e2) else Color(0xfffef0f0),
      borderColor = if (isFocused) Color(0xfff78989) else Color(0xfffab6b6),
      textColor = Color(0xfff56c6c)
    )
  }
}

/**
 * ZCheckTag 默认常量。
 */
object ZCheckTagDefaults {
  /** 默认形状。 */
  val Shape = RoundedCornerShape(6.dp)

  /** 内容内边距。 */
  val ContentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

  /** 最小高度。 */
  val MinHeight = 32.dp

  /** 文本字号。 */
  val FontSize = 14.sp

  /** 文本最大宽度。 */
  val TextMaxWidth = 180.dp
}
