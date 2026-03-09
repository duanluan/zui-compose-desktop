package top.zhjh.zui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.zhjh.zui.enums.ZColorType
import top.zhjh.zui.theme.isAppInDarkTheme

object ZPopconfirmDefaults {
  // 位置字符串常量，保持与上层 API 一致。
  const val Top = "top"
  const val TopStart = "top-start"
  const val TopEnd = "top-end"
  const val Bottom = "bottom"
  const val BottomStart = "bottom-start"
  const val BottomEnd = "bottom-end"
  const val Left = "left"
  const val LeftStart = "left-start"
  const val LeftEnd = "left-end"
  const val Right = "right"
  const val RightStart = "right-start"
  const val RightEnd = "right-end"

  /**
   * 强类型位置枚举。
   *
   * 使用示例：
   * `placement = ZPopconfirmDefaults.Placements.TopStart`
   */
  enum class Placements(val value: String) {
    TopStart("top-start"),
    Top("top"),
    TopEnd("top-end"),
    BottomStart("bottom-start"),
    Bottom("bottom"),
    BottomEnd("bottom-end"),
    LeftStart("left-start"),
    Left("left"),
    LeftEnd("left-end"),
    RightStart("right-start"),
    Right("right"),
    RightEnd("right-end")
  }

  val PopupWidth = 220.dp
  val PopupMinWidth = 180.dp
  val PopupGap = 8.dp

  // 历史参数保留，便于外部按需复用。
  val ArrowLongSide = 14.dp
  val ArrowShortSide = 7.dp
  val ArrowInset = 20.dp
  val ArrowVerticalInset = 8.dp

  // 卡片视觉参数统一收口，避免散落的硬编码尺寸。
  val CardCornerRadius = 6.dp
  val CardBorderWidth = 1.2.dp
  val CardShadowElevation = 8.dp
  val CardContentHorizontalPadding = 14.dp
  val CardContentVerticalPadding = 14.dp

  // 明暗主题边框色统一管理，便于后续主题扩展。
  val LightBorderColor = Color(0xffe4e7ed)
  val DarkBorderColor = Color(0xff4c4d4f)
  val LightBackgroundColor = Color(0xffffffff)
  val LightTextColor = Color(0xff3e3f41)
  val DarkBackgroundColor = Color(0xff1d1e1f)
  val DarkTextColor = Color(0xffe5eaf3)

  const val HideAfterMillis = 200
  val DefaultIconColor = Color(0xffff9900)
  const val ConfirmButtonTypePrimary = "primary"
  const val CancelButtonTypeText = "text"
}

private data class ZPopconfirmStyle(
  val backgroundColor: Color,
  val textColor: Color
)

private enum class ZPopconfirmButtonType {
  PRIMARY,
  SUCCESS,
  WARNING,
  DANGER,
  INFO,
  TEXT
}

/**
 * 气泡确认框。
 *
 * 说明：
 * - 通过 `title` 配置提示文案；
 * - 通过 `placement` 配置展示位置，支持字符串或 `ZPopconfirmDefaults.Placements`；
 * - 通过 `icon` 配置图标，`iconColor` 配置图标颜色；
 * - 通过 `actions` 自定义底部操作区；
 * - 通过 `content` 可完全自定义弹层内容（高级用法）；
 * - 参考元素由 `reference` 插槽提供，点击元素时触发弹层展示。
 */
@Composable
fun ZPopconfirm(
  title: String,
  placement: ZPopconfirmDefaults.Placements,
  modifier: Modifier = Modifier,
  confirmButtonText: String = "Yes",
  cancelButtonText: String = "No",
  confirmButtonType: String = ZPopconfirmDefaults.ConfirmButtonTypePrimary,
  cancelButtonType: String = ZPopconfirmDefaults.CancelButtonTypeText,
  enabled: Boolean = true,
  width: Dp = ZPopconfirmDefaults.PopupWidth,
  icon: (@Composable (iconColor: Color) -> Unit)? = null,
  iconColor: Color = ZPopconfirmDefaults.DefaultIconColor,
  hideIcon: Boolean = false,
  hideAfter: Int = ZPopconfirmDefaults.HideAfterMillis,
  onConfirm: () -> Unit = {},
  onCancel: () -> Unit = {},
  onVisibleChange: (Boolean) -> Unit = {},
  actions: (@Composable (confirm: () -> Unit, cancel: () -> Unit) -> Unit)? = null,
  content: (@Composable (onConfirm: () -> Unit, onCancel: () -> Unit) -> Unit)? = null,
  reference: @Composable (showPopconfirm: () -> Unit) -> Unit
) {
  ZPopconfirm(
    title = title,
    modifier = modifier,
    placement = placement.value,
    confirmButtonText = confirmButtonText,
    cancelButtonText = cancelButtonText,
    confirmButtonType = confirmButtonType,
    cancelButtonType = cancelButtonType,
    enabled = enabled,
    width = width,
    icon = icon,
    iconColor = iconColor,
    hideIcon = hideIcon,
    hideAfter = hideAfter,
    onConfirm = onConfirm,
    onCancel = onCancel,
    onVisibleChange = onVisibleChange,
    actions = actions,
    content = content,
    reference = reference
  )
}

/**
 * 字符串版位置参数，兼容现有调用方式。
 */
@Composable
fun ZPopconfirm(
  title: String,
  modifier: Modifier = Modifier,
  placement: String = ZPopconfirmDefaults.Top,
  confirmButtonText: String = "Yes",
  cancelButtonText: String = "No",
  confirmButtonType: String = ZPopconfirmDefaults.ConfirmButtonTypePrimary,
  cancelButtonType: String = ZPopconfirmDefaults.CancelButtonTypeText,
  enabled: Boolean = true,
  width: Dp = ZPopconfirmDefaults.PopupWidth,
  icon: (@Composable (iconColor: Color) -> Unit)? = null,
  iconColor: Color = ZPopconfirmDefaults.DefaultIconColor,
  hideIcon: Boolean = false,
  hideAfter: Int = ZPopconfirmDefaults.HideAfterMillis,
  onConfirm: () -> Unit = {},
  onCancel: () -> Unit = {},
  onVisibleChange: (Boolean) -> Unit = {},
  actions: (@Composable (confirm: () -> Unit, cancel: () -> Unit) -> Unit)? = null,
  content: (@Composable (onConfirm: () -> Unit, onCancel: () -> Unit) -> Unit)? = null,
  reference: @Composable (showPopconfirm: () -> Unit) -> Unit
) {
  var popupVisible by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  var hideJob by remember { mutableStateOf<Job?>(null) }
  val resolvedWidth = width.coerceAtLeast(ZPopconfirmDefaults.PopupMinWidth)
  val resolvedHideAfter = hideAfter.coerceAtLeast(0).toLong()

  val isDarkTheme = isAppInDarkTheme()
  val style = remember(isDarkTheme) { resolveZPopconfirmStyle(isDarkTheme) }
  val borderColor = if (isDarkTheme) {
    ZPopconfirmDefaults.DarkBorderColor
  } else {
    ZPopconfirmDefaults.LightBorderColor
  }

  fun setPopupVisible(next: Boolean) {
    popupVisible
      .takeIf { it != next }
      ?.let {
        popupVisible = next
        onVisibleChange(next)
      }
  }

  fun showPopup() {
    hideJob?.cancel()
    setPopupVisible(true)
  }

  fun hidePopup(immediate: Boolean = false) {
    hideJob?.cancel()
    val delayMs = if (immediate) 0L else resolvedHideAfter
    delayMs
      .takeIf { it > 0L }
      ?.let {
        hideJob = coroutineScope.launch {
          delay(it)
          setPopupVisible(false)
        }
      }
      ?: setPopupVisible(false)
  }

  val onConfirmAndHide = {
    onConfirm()
    hidePopup()
  }

  val onCancelAndHide = {
    onCancel()
    hidePopup()
  }

  LaunchedEffect(enabled) {
    if (!enabled && popupVisible) {
      hidePopup(immediate = true)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      hideJob?.cancel()
    }
  }

  ZTooltip(
    modifier = modifier,
    placement = placement,
    trigger = ZTooltipDefaults.TriggerClick,
    visible = popupVisible,
    onVisibleChange = { next ->
      if (next) {
        showPopup()
      } else {
        hidePopup()
      }
    },
    enabled = enabled,
    backgroundColor = style.backgroundColor,
    borderColor = borderColor,
    elevation = ZPopconfirmDefaults.CardShadowElevation,
    contentPadding = PaddingValues(
      horizontal = ZPopconfirmDefaults.CardContentHorizontalPadding,
      vertical = ZPopconfirmDefaults.CardContentVerticalPadding
    ),
    cornerRadius = ZPopconfirmDefaults.CardCornerRadius,
    borderWidth = ZPopconfirmDefaults.CardBorderWidth,
    effect = if (isDarkTheme) ZTooltipDefaults.EffectDark else ZTooltipDefaults.EffectLight,
    transition = ZTooltipDefaults.TransitionFade,
    tooltipContent = {
      Box(
        modifier = Modifier
          .width(resolvedWidth)
          .widthIn(min = ZPopconfirmDefaults.PopupMinWidth)
      ) {
        if (content != null) {
          content(onConfirmAndHide, onCancelAndHide)
        } else {
          Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (!hideIcon) {
                if (icon != null) {
                  icon(iconColor)
                } else {
                  ZPopconfirmDefaultIcon(iconColor = iconColor)
                }
              }
              ZText(
                text = title,
                color = style.textColor
              )
            }

            if (actions != null) {
              actions(onConfirmAndHide, onCancelAndHide)
            } else {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  ZPopconfirmDefaultActionButton(
                    text = cancelButtonText,
                    buttonType = cancelButtonType,
                    onClick = onCancelAndHide
                  )
                  ZPopconfirmDefaultActionButton(
                    text = confirmButtonText,
                    buttonType = confirmButtonType,
                    onClick = onConfirmAndHide
                  )
                }
              }
            }
          }
        }
      }
    },
    reference = {
      reference {
        if (enabled) {
          showPopup()
        }
      }
    }
  )
}

@Composable
private fun ZPopconfirmDefaultActionButton(
  text: String,
  buttonType: String,
  onClick: () -> Unit
) {
  val resolvedButtonType = parseZPopconfirmButtonType(buttonType)
  when (resolvedButtonType) {
    ZPopconfirmButtonType.TEXT -> {
      ZButton(
        size = ZButtonSize.Small,
        type = ZColorType.DEFAULT,
        buttonType = ZButtonType.Text,
        onClick = onClick
      ) {
        ZText(text)
      }
    }

    else -> {
      ZButton(
        size = ZButtonSize.Small,
        type = toZColorType(resolvedButtonType),
        onClick = onClick
      ) {
        ZText(text)
      }
    }
  }
}

@Composable
private fun ZPopconfirmDefaultIcon(
  iconColor: Color
) {
  Box(
    modifier = Modifier
      .size(16.dp)
      .clip(CircleShape)
      .background(iconColor),
    contentAlignment = Alignment.Center
  ) {
    ZText(
      text = "i",
      color = Color.White,
      fontWeight = FontWeight.Bold,
      fontSize = 11.sp,
      lineHeight = 11.sp
    )
  }
}

private fun parseZPopconfirmButtonType(rawType: String): ZPopconfirmButtonType {
  return when (rawType.trim().lowercase()) {
    ZPopconfirmDefaults.ConfirmButtonTypePrimary -> ZPopconfirmButtonType.PRIMARY
    "success" -> ZPopconfirmButtonType.SUCCESS
    "warning" -> ZPopconfirmButtonType.WARNING
    "danger" -> ZPopconfirmButtonType.DANGER
    "info" -> ZPopconfirmButtonType.INFO
    ZPopconfirmDefaults.CancelButtonTypeText -> ZPopconfirmButtonType.TEXT
    else -> ZPopconfirmButtonType.PRIMARY
  }
}

private fun toZColorType(buttonType: ZPopconfirmButtonType): ZColorType {
  return when (buttonType) {
    ZPopconfirmButtonType.PRIMARY -> ZColorType.PRIMARY
    ZPopconfirmButtonType.SUCCESS -> ZColorType.SUCCESS
    ZPopconfirmButtonType.WARNING -> ZColorType.WARNING
    ZPopconfirmButtonType.DANGER -> ZColorType.DANGER
    ZPopconfirmButtonType.INFO -> ZColorType.INFO
    ZPopconfirmButtonType.TEXT -> ZColorType.DEFAULT
  }
}

private fun resolveZPopconfirmStyle(isDarkTheme: Boolean): ZPopconfirmStyle {
  return if (isDarkTheme) {
    ZPopconfirmStyle(
      backgroundColor = ZPopconfirmDefaults.DarkBackgroundColor,
      textColor = ZPopconfirmDefaults.DarkTextColor
    )
  } else {
    ZPopconfirmStyle(
      backgroundColor = ZPopconfirmDefaults.LightBackgroundColor,
      textColor = ZPopconfirmDefaults.LightTextColor
    )
  }
}
