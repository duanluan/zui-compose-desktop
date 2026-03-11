package top.zhjh.zui.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.X
import top.zhjh.zui.enums.ZColorType
import top.zhjh.zui.theme.isAppInDarkTheme

/**
 * ZTag（标签）组件。
 *
 * 设计目标：
 * 1. 对齐 Element Plus 的常用能力（类型、主题、圆角、关闭、尺寸）。
 * 2. 保持桌面端交互一致性（Hover/Focus/键盘可达性）。
 * 3. 在不增加调用方负担的前提下支持文本溢出 Tooltip。
 */
enum class ZTagSize {
  /** 大尺寸标签，适合标题附近或强调场景。 */
  Large,

  /** 默认尺寸标签，常规业务场景的首选。 */
  Default,

  /** 小尺寸标签，适合高密度信息布局。 */
  Small
}

enum class ZTagEffect {
  /** 深色实底主题。 */
  Dark,

  /** 浅色填充主题（默认）。 */
  Light,

  /** 透明底+描边主题。 */
  Plain
}

enum class ZTagTooltipMode {
  /** 不显示 Tooltip。 */
  None,

  /** 仅在文本发生视觉溢出时显示 Tooltip。 */
  OnlyOnOverflow,

  /** 始终显示 Tooltip。 */
  Always
}

/**
 * 文本标签重载（最常用入口）。
 *
 * 该重载内置：
 * - 文本单行省略；
 * - 最大文本宽度限制；
 * - 根据 [tooltipMode] 控制 Tooltip 显示策略。
 */
@Composable
fun ZTag(
  text: String,
  modifier: Modifier = Modifier,
  size: ZTagSize = ZTagSize.Default,
  effect: ZTagEffect = ZTagEffect.Light,
  type: ZColorType = ZColorType.PRIMARY,
  round: Boolean = false,
  closable: Boolean = false,
  hit: Boolean = false,
  color: Color = Color.Unspecified,
  disableTransitions: Boolean = false,
  onClick: (() -> Unit)? = null,
  tooltipMode: ZTagTooltipMode = ZTagTooltipMode.None,
  overflowTooltipPlacement: ZTooltipDefaults.Placements = ZTooltipDefaults.Placements.Top,
  closeContentDescription: String? = null,
  onClose: () -> Unit = {}
) {
  val metrics = remember(size) { getZTagMetrics(size) }
  // 通过 onTextLayout 实时感知“视觉溢出”，用于 OnlyOnOverflow 模式。
  var textOverflow by remember(text) { mutableStateOf(false) }

  // 参考节点：用于被 Tooltip 包裹，也可直接渲染。
  val tagReference: @Composable () -> Unit = {
    ZTag(
      modifier = modifier,
      size = size,
      effect = effect,
      type = type,
      round = round,
      closable = closable,
      hit = hit,
      color = color,
      disableTransitions = disableTransitions,
      onClick = onClick,
      closeContentDescription = closeContentDescription,
      onClose = onClose
    ) {
      ZText(
        text = text,
        modifier = Modifier.widthIn(max = ZTagDefaults.TextMaxWidth),
        color = LocalContentColor.current,
        fontSize = metrics.fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult ->
          val hasOverflow = layoutResult.hasVisualOverflow
          if (hasOverflow != textOverflow) {
            textOverflow = hasOverflow
          }
        }
      )
    }
  }

  // 统一的 Tooltip 显示判定入口，避免调用方同时维护多份条件。
  val shouldShowTooltip = when (tooltipMode) {
    ZTagTooltipMode.None -> false
    ZTagTooltipMode.OnlyOnOverflow -> textOverflow
    ZTagTooltipMode.Always -> true
  }
  if (shouldShowTooltip) {
    ZTooltip(
      content = text,
      placement = overflowTooltipPlacement,
      reference = tagReference
    )
  } else {
    tagReference()
  }
}

/**
 * 插槽版标签重载。
 *
 * 使用场景：
 * - 需要自定义内容（图标 + 文本、富文本等）；
 * - 需要复用统一的关闭动画、交互反馈和配色系统。
 */
@Composable
fun ZTag(
  modifier: Modifier = Modifier,
  size: ZTagSize = ZTagSize.Default,
  effect: ZTagEffect = ZTagEffect.Light,
  type: ZColorType = ZColorType.PRIMARY,
  round: Boolean = false,
  closable: Boolean = false,
  hit: Boolean = false,
  color: Color = Color.Unspecified,
  disableTransitions: Boolean = false,
  onClick: (() -> Unit)? = null,
  closeContentDescription: String? = null,
  onClose: () -> Unit = {},
  content: @Composable RowScope.() -> Unit
) {
  // 1) 解析基础样式（主题 + 类型 + 深浅色）
  val isDarkTheme = isAppInDarkTheme()
  val baseStyle = remember(type, effect, isDarkTheme) {
    getZTagStyle(type = type, effect = effect, isDarkTheme = isDarkTheme)
  }
  val style = remember(baseStyle, effect, hit, color) {
    resolveZTagStyle(baseStyle = baseStyle, effect = effect, hit = hit, color = color)
  }
  // 2) 尺寸、形状与交互状态
  val metrics = remember(size) { getZTagMetrics(size) }
  val shape = remember(round) { if (round) ZTagDefaults.RoundShape else ZTagDefaults.Shape }
  val tagInteractionSource = remember { MutableInteractionSource() }
  val isHovered by tagInteractionSource.collectIsHoveredAsState()
  val isFocused by tagInteractionSource.collectIsFocusedAsState()
  val visibilityState = remember { androidx.compose.animation.core.MutableTransitionState(true) }
  var closeRequested by remember { mutableStateOf(false) }
  var closeDispatched by remember { mutableStateOf(false) }
  val onCloseState by rememberUpdatedState(onClose)
  val clickableOnClick = onClick
  val isInteractive = clickableOnClick != null && !closeRequested
  val interactiveBorderWidth = if (isInteractive && isFocused) 2.dp else ZTagDefaults.BorderWidth
  val interactiveStyle = remember(style, isHovered, isFocused, isInteractive, isDarkTheme) {
    resolveInteractiveZTagStyle(
      baseStyle = style,
      isHovered = isHovered && isInteractive,
      isFocused = isFocused && isInteractive,
      isDarkTheme = isDarkTheme
    )
  }
  // 3) 关闭时的退场动画：可通过 disableTransitions 关闭。
  val exitTransition = remember(disableTransitions) {
    val durationMillis = if (disableTransitions) {
      0
    } else {
      ZTagDefaults.CloseTransitionDurationMillis
    }
    fadeOut(animationSpec = tween(durationMillis = durationMillis)) +
      shrinkHorizontally(
        animationSpec = tween(durationMillis = durationMillis),
        shrinkTowards = Alignment.End
      ) +
      shrinkVertically(
        animationSpec = tween(durationMillis = durationMillis),
        shrinkTowards = Alignment.CenterVertically
      )
  }

  // 4) 在动画结束后派发 onClose，保证“视觉退场”与“数据删除”时机一致。
  LaunchedEffect(closeRequested, closeDispatched, visibilityState.isIdle, visibilityState.currentState) {
    // Guard against duplicate close dispatches during exit animation/recomposition.
    if (closeRequested && !closeDispatched && visibilityState.isIdle && !visibilityState.currentState) {
      closeDispatched = true
      onCloseState()
    }
  }

  CompositionLocalProvider(LocalContentColor provides style.textColor) {
    AnimatedVisibility(
      visibleState = visibilityState,
      enter = EnterTransition.None,
      exit = exitTransition,
      modifier = modifier
    ) {
      Row(
        modifier = Modifier
          .defaultMinSize(minHeight = metrics.minHeight)
          .clip(shape)
          .background(interactiveStyle.backgroundColor)
          .border(width = interactiveBorderWidth, color = interactiveStyle.borderColor, shape = shape)
          .then(
            if (isInteractive) {
              Modifier
                .hoverable(interactionSource = tagInteractionSource)
                .focusable(interactionSource = tagInteractionSource)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                  interactionSource = tagInteractionSource,
                  indication = null,
                  role = Role.Button
                ) {
                  clickableOnClick()
                }
            } else {
              Modifier
            }
          )
          .padding(metrics.contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        content()
        if (closable) {
          Spacer(modifier = Modifier.width(metrics.closeIconSpacing))
          ZTagCloseButton(
            tint = style.textColor,
            size = metrics.closeButtonSize,
            iconSize = metrics.closeIconSize,
            contentDescription = closeContentDescription,
            enabled = !closeRequested,
            onClick = {
              if (!closeRequested) {
                closeRequested = true
                visibilityState.targetState = false
              }
            }
          )
        }
      }
    }
  }
}

private fun resolveZTagStyle(
  baseStyle: ZTagStyle,
  effect: ZTagEffect,
  hit: Boolean,
  color: Color
): ZTagStyle {
  // 自定义背景色优先级最高：忽略 type/effect 的背景，并自动推导可读文本色。
  if (color != Color.Unspecified) {
    val textColor = if (color.luminance() > 0.6f) {
      Color(0xff303133)
    } else {
      Color.White
    }
    val borderColor = if (hit) {
      lerp(color, Color.Black, if (textColor == Color.White) 0.15f else 0.25f)
    } else {
      color
    }
    return ZTagStyle(
      backgroundColor = color,
      borderColor = borderColor,
      textColor = textColor
    )
  }

  if (!hit) {
    return baseStyle
  }

  val borderColor = when (effect) {
    ZTagEffect.Dark -> lerp(baseStyle.backgroundColor, Color.Black, 0.18f)
    ZTagEffect.Light, ZTagEffect.Plain -> lerp(baseStyle.borderColor, baseStyle.textColor, 0.30f)
  }
  return baseStyle.copy(borderColor = borderColor)
}

/**
 * 解析交互态样式（Hover / Focus）。
 *
 * 策略：
 * - 暗色主题向白色方向混色；
 * - 亮色主题向黑色方向混色；
 * - Focus 的混色强度高于 Hover，便于键盘导航时识别焦点。
 */
private fun resolveInteractiveZTagStyle(
  baseStyle: ZTagStyle,
  isHovered: Boolean,
  isFocused: Boolean,
  isDarkTheme: Boolean
): ZTagStyle {
  if (!isHovered && !isFocused) {
    return baseStyle
  }

  val backgroundMixTarget = if (isDarkTheme) Color.White else Color.Black
  val borderMixTarget = if (isDarkTheme) Color.White else Color.Black
  val backgroundMixRatio = when {
    isFocused -> if (isDarkTheme) 0.12f else 0.06f
    else -> if (isDarkTheme) 0.08f else 0.04f
  }
  val borderMixRatio = when {
    isFocused -> if (isDarkTheme) 0.25f else 0.15f
    else -> if (isDarkTheme) 0.18f else 0.10f
  }

  return baseStyle.copy(
    backgroundColor = lerp(baseStyle.backgroundColor, backgroundMixTarget, backgroundMixRatio),
    borderColor = lerp(baseStyle.borderColor, borderMixTarget, borderMixRatio)
  )
}

/**
 * 标签右侧关闭按钮。
 *
 * 交互说明：
 * - 支持 Hover、Focus、键盘点击；
 * - `enabled=false` 时仅渲染静态图标，不注册交互语义。
 */
@Composable
private fun ZTagCloseButton(
  tint: Color,
  size: Dp,
  iconSize: Dp,
  contentDescription: String?,
  enabled: Boolean,
  onClick: () -> Unit
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val isFocused by interactionSource.collectIsFocusedAsState()
  val isActive = enabled && (isHovered || isFocused)

  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .background(if (isActive) tint.copy(alpha = 0.85f) else Color.Transparent)
      .then(
        if (enabled) {
          Modifier
            .hoverable(interactionSource = interactionSource)
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
              interactionSource = interactionSource,
              indication = null,
              role = Role.Button,
              onClick = onClick
            )
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = FeatherIcons.X,
      contentDescription = contentDescription,
      tint = if (isActive) Color.White else tint.copy(alpha = 0.8f),
      modifier = Modifier.size(iconSize)
    )
  }
}

@Immutable
private data class ZTagStyle(
  /** 标签背景色。 */
  val backgroundColor: Color,

  /** 标签边框色。 */
  val borderColor: Color,

  /** 标签内容色（文本/图标）。 */
  val textColor: Color
)

@Immutable
private data class ZTagMetrics(
  /** 组件最小高度。 */
  val minHeight: Dp,

  /** 组件内容内边距。 */
  val contentPadding: PaddingValues,

  /** 正文文字字号。 */
  val fontSize: TextUnit,

  /** 关闭图标尺寸。 */
  val closeIconSize: Dp,

  /** 关闭按钮点击热区尺寸。 */
  val closeButtonSize: Dp,

  /** 文本与关闭按钮之间间距。 */
  val closeIconSpacing: Dp
)

/** 根据 [ZTagSize] 解析尺寸度量。 */
private fun getZTagMetrics(size: ZTagSize): ZTagMetrics {
  return when (size) {
    ZTagSize.Large -> ZTagMetrics(
      minHeight = ZTagDefaults.LargeMinHeight,
      contentPadding = ZTagDefaults.LargeContentPadding,
      fontSize = ZTagDefaults.LargeFontSize,
      closeIconSize = ZTagDefaults.LargeCloseIconSize,
      closeButtonSize = ZTagDefaults.LargeCloseButtonSize,
      closeIconSpacing = ZTagDefaults.LargeCloseIconSpacing
    )

    ZTagSize.Small -> ZTagMetrics(
      minHeight = ZTagDefaults.SmallMinHeight,
      contentPadding = ZTagDefaults.SmallContentPadding,
      fontSize = ZTagDefaults.SmallFontSize,
      closeIconSize = ZTagDefaults.SmallCloseIconSize,
      closeButtonSize = ZTagDefaults.SmallCloseButtonSize,
      closeIconSpacing = ZTagDefaults.SmallCloseIconSpacing
    )

    ZTagSize.Default -> ZTagMetrics(
      minHeight = ZTagDefaults.MinHeight,
      contentPadding = ZTagDefaults.ContentPadding,
      fontSize = ZTagDefaults.FontSize,
      closeIconSize = ZTagDefaults.CloseIconSize,
      closeButtonSize = ZTagDefaults.CloseButtonSize,
      closeIconSpacing = ZTagDefaults.CloseIconSpacing
    )
  }
}

/**
 * 解析基础配色（不含交互态叠加）。
 *
 * 维度：
 * - 主题：Light / Dark；
 * - 效果：Dark / Light / Plain；
 * - 语义色：Default / Primary / Success / Info / Warning / Danger。
 */
private fun getZTagStyle(type: ZColorType, effect: ZTagEffect, isDarkTheme: Boolean): ZTagStyle {
  return if (isDarkTheme) {
    when (effect) {
      ZTagEffect.Dark -> when (type) {
        ZColorType.DEFAULT -> ZTagStyle(
          backgroundColor = Color(0xff909399),
          borderColor = Color(0xff909399),
          textColor = Color.White
        )
        ZColorType.PRIMARY -> ZTagStyle(
          backgroundColor = Color(0xff409eff),
          borderColor = Color(0xff409eff),
          textColor = Color.White
        )
        ZColorType.SUCCESS -> ZTagStyle(
          backgroundColor = Color(0xff67c23a),
          borderColor = Color(0xff67c23a),
          textColor = Color.White
        )
        ZColorType.INFO -> ZTagStyle(
          backgroundColor = Color(0xff909399),
          borderColor = Color(0xff909399),
          textColor = Color.White
        )
        ZColorType.WARNING -> ZTagStyle(
          backgroundColor = Color(0xffe6a23c),
          borderColor = Color(0xffe6a23c),
          textColor = Color.White
        )
        ZColorType.DANGER -> ZTagStyle(
          backgroundColor = Color(0xfff56c6c),
          borderColor = Color(0xfff56c6c),
          textColor = Color.White
        )
      }

      ZTagEffect.Plain -> when (type) {
        ZColorType.DEFAULT -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xff4c4d4f),
          textColor = Color(0xffcfd3dc)
        )
        ZColorType.PRIMARY -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xff2a598a),
          textColor = Color(0xff409eff)
        )
        ZColorType.SUCCESS -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xff3e6b27),
          textColor = Color(0xff67c23a)
        )
        ZColorType.INFO -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xff4c4d4f),
          textColor = Color(0xffa6a9ad)
        )
        ZColorType.WARNING -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xff7d5b28),
          textColor = Color(0xffe6a23c)
        )
        ZColorType.DANGER -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xff854040),
          textColor = Color(0xfff56c6c)
        )
      }

      ZTagEffect.Light -> when (type) {
        ZColorType.DEFAULT -> ZTagStyle(
          backgroundColor = Color(0xff202121),
          borderColor = Color(0xff4c4d4f),
          textColor = Color(0xffcfd3dc)
        )
        ZColorType.PRIMARY -> ZTagStyle(
          backgroundColor = Color(0xff18222c),
          borderColor = Color(0xff2a598a),
          textColor = Color(0xff409eff)
        )
        ZColorType.SUCCESS -> ZTagStyle(
          backgroundColor = Color(0xff1c2518),
          borderColor = Color(0xff3e6b27),
          textColor = Color(0xff67c23a)
        )
        ZColorType.INFO -> ZTagStyle(
          backgroundColor = Color(0xff202121),
          borderColor = Color(0xff2d2d2f),
          textColor = Color(0xffa6a9ad)
        )
        ZColorType.WARNING -> ZTagStyle(
          backgroundColor = Color(0xff292218),
          borderColor = Color(0xff3e301c),
          textColor = Color(0xffe6a23c)
        )
        ZColorType.DANGER -> ZTagStyle(
          backgroundColor = Color(0xff2b1d1d),
          borderColor = Color(0xff412626),
          textColor = Color(0xfff56c6c)
        )
      }
    }
  } else {
    when (effect) {
      ZTagEffect.Dark -> when (type) {
        ZColorType.DEFAULT -> ZTagStyle(
          backgroundColor = Color(0xff909399),
          borderColor = Color(0xff909399),
          textColor = Color.White
        )
        ZColorType.PRIMARY -> ZTagStyle(
          backgroundColor = Color(0xff409eff),
          borderColor = Color(0xff409eff),
          textColor = Color.White
        )
        ZColorType.SUCCESS -> ZTagStyle(
          backgroundColor = Color(0xff67c23a),
          borderColor = Color(0xff67c23a),
          textColor = Color.White
        )
        ZColorType.INFO -> ZTagStyle(
          backgroundColor = Color(0xff909399),
          borderColor = Color(0xff909399),
          textColor = Color.White
        )
        ZColorType.WARNING -> ZTagStyle(
          backgroundColor = Color(0xffe6a23c),
          borderColor = Color(0xffe6a23c),
          textColor = Color.White
        )
        ZColorType.DANGER -> ZTagStyle(
          backgroundColor = Color(0xfff56c6c),
          borderColor = Color(0xfff56c6c),
          textColor = Color.White
        )
      }

      ZTagEffect.Plain -> when (type) {
        ZColorType.DEFAULT -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xffdcdfe6),
          textColor = Color(0xff606266)
        )
        ZColorType.PRIMARY -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xffa0cfff),
          textColor = Color(0xff409eff)
        )
        ZColorType.SUCCESS -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xffb3e19d),
          textColor = Color(0xff67c23a)
        )
        ZColorType.INFO -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xffc8c9cc),
          textColor = Color(0xff909399)
        )
        ZColorType.WARNING -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xfff3d19e),
          textColor = Color(0xffe6a23c)
        )
        ZColorType.DANGER -> ZTagStyle(
          backgroundColor = Color.Transparent,
          borderColor = Color(0xfffab6b6),
          textColor = Color(0xfff56c6c)
        )
      }

      ZTagEffect.Light -> when (type) {
        ZColorType.DEFAULT -> ZTagStyle(
          backgroundColor = Color(0xfff4f4f5),
          borderColor = Color(0xffe9e9eb),
          textColor = Color(0xff606266)
        )
        ZColorType.PRIMARY -> ZTagStyle(
          backgroundColor = Color(0xffecf5ff),
          borderColor = Color(0xffd9ecff),
          textColor = Color(0xff409eff)
        )
        ZColorType.SUCCESS -> ZTagStyle(
          backgroundColor = Color(0xfff0f9eb),
          borderColor = Color(0xffe1f3d8),
          textColor = Color(0xff67c23a)
        )
        ZColorType.INFO -> ZTagStyle(
          backgroundColor = Color(0xfff4f4f5),
          borderColor = Color(0xffe9e9eb),
          textColor = Color(0xff909399)
        )
        ZColorType.WARNING -> ZTagStyle(
          backgroundColor = Color(0xfffdf6ec),
          borderColor = Color(0xfffaecd8),
          textColor = Color(0xffe6a23c)
        )
        ZColorType.DANGER -> ZTagStyle(
          backgroundColor = Color(0xfffef0f0),
          borderColor = Color(0xfffde2e2),
          textColor = Color(0xfff56c6c)
        )
      }
    }
  }
}

/**
 * Tag 组件默认常量。
 *
 * 所有尺寸、间距、动画时长统一集中定义，便于后续主题化与规模化调整。
 */
object ZTagDefaults {
  /** 默认圆角形状。 */
  val Shape = RoundedCornerShape(4.dp)

  /** 圆形标签形状。 */
  val RoundShape = RoundedCornerShape(999.dp)

  /** 默认边框宽度。 */
  val BorderWidth = 1.dp

  /** 大尺寸内容内边距。 */
  val LargeContentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)

  /** 默认内容内边距。 */
  val ContentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp)

  /** 小尺寸内容内边距。 */
  val SmallContentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp)

  /** 大尺寸最小高度。 */
  val LargeMinHeight = 32.dp

  /** 默认最小高度。 */
  val MinHeight = 24.dp

  /** 小尺寸最小高度。 */
  val SmallMinHeight = 20.dp

  /** 大尺寸字号。 */
  val LargeFontSize = 14.sp

  /** 默认字号。 */
  val FontSize = 12.sp

  /** 小尺寸字号。 */
  val SmallFontSize = 12.sp

  /** 大尺寸关闭图标尺寸。 */
  val LargeCloseIconSize = 14.dp

  /** 默认关闭图标尺寸。 */
  val CloseIconSize = 12.dp

  /** 小尺寸关闭图标尺寸。 */
  val SmallCloseIconSize = 10.dp

  /** 大尺寸关闭按钮尺寸。 */
  val LargeCloseButtonSize = 16.dp

  /** 默认关闭按钮尺寸。 */
  val CloseButtonSize = 14.dp

  /** 小尺寸关闭按钮尺寸。 */
  val SmallCloseButtonSize = 12.dp

  /** 大尺寸图标间距。 */
  val LargeCloseIconSpacing = 5.dp

  /** 默认图标间距。 */
  val CloseIconSpacing = 4.dp

  /** 小尺寸图标间距。 */
  val SmallCloseIconSpacing = 3.dp

  /** 文本最大宽度，超出将触发省略。 */
  val TextMaxWidth = 180.dp

  /** 关闭动画时长（毫秒）。 */
  const val CloseTransitionDurationMillis = 200
}
