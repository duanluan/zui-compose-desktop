package top.zhjh.zui.composable

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Dialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window

object ZTooltipDefaults {
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
  const val EffectDark = "dark"
  const val EffectLight = "light"
  const val TriggerHover = "hover"
  const val TriggerClick = "click"
  const val TransitionNone = "none"
  const val TransitionFade = "fade"
  const val TransitionSlideFade = "slide-fade"

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

  val PopupGap = 8.dp
  val ArrowLongSide = 10.dp
  val ArrowShortSide = 6.dp
  val ArrowInset = 30.dp
  val ArrowVerticalInset = 8.dp
  val CornerRadius = 4.dp
  val Offset = 8.dp
  val ContentHorizontalPadding = 10.dp
  val ContentVerticalPadding = 6.dp
  val BorderWidth = 1.dp
  const val ShowAfterMillis = 0
  const val HideAfterMillis = 80
  const val AutoCloseMillis = 0
  val DarkBackgroundColor = Color(0xff303133)
  val DarkTextColor = Color.White
  val DarkBorderColor = Color.Transparent
  val LightBackgroundColor = Color(0xffffffff)
  val LightTextColor = Color(0xff303133)
  val LightBorderColor = Color(0xffe4e7ed)
  val SlideFadeOffsetX = 10.dp
}

private enum class ZTooltipDirection {
  Top,
  Bottom,
  Left,
  Right
}

private enum class ZTooltipAlign {
  Start,
  Center,
  End
}

private data class ZTooltipPlacement(
  val direction: ZTooltipDirection,
  val align: ZTooltipAlign
)

private data class ZTooltipStyle(
  val backgroundColor: Color,
  val textColor: Color,
  val borderColor: Color
)

private data class ZTooltipPopupGeometry(
  val placement: ZTooltipPlacement,
  val arrowShiftXPx: Float = 0f,
  val arrowShiftYPx: Float = 0f
)

private data class ZTooltipAnimationValues(
  val alpha: Float = 1f,
  val offsetX: Dp = 0.dp,
  val offsetY: Dp = 0.dp
)

private enum class ZTooltipTransition {
  NONE,
  FADE,
  SLIDE_FADE
}

private enum class ZTooltipTrigger {
  HOVER,
  CLICK
}

private data class ZTooltipTransitionSpec(
  val enterDurationMillis: Int,
  val exitDurationMillis: Int,
  val enterEasing: CubicBezierEasing,
  val exitEasing: CubicBezierEasing
)

private class TooltipShape(
  private val placement: ZTooltipPlacement,
  private val arrowBase: Float,
  private val arrowHeight: Float,
  private val arrowInset: Float,
  private val arrowShiftX: Float,
  private val arrowShiftY: Float,
  private val cornerRadius: Float
) : Shape {
  override fun createOutline(
    size: androidx.compose.ui.geometry.Size,
    layoutDirection: LayoutDirection,
    density: androidx.compose.ui.unit.Density
  ): Outline {
    var rectLeft = 0f
    var rectTop = 0f
    var rectRight = size.width
    var rectBottom = size.height

    when (placement.direction) {
      ZTooltipDirection.Top -> rectBottom -= arrowHeight
      ZTooltipDirection.Bottom -> rectTop += arrowHeight
      ZTooltipDirection.Left -> rectRight -= arrowHeight
      ZTooltipDirection.Right -> rectLeft += arrowHeight
    }

    val rectWidth = rectRight - rectLeft
    val rectHeight = rectBottom - rectTop

    val roundRectPath = Path().apply {
      addRoundRect(
        RoundRect(
          left = rectLeft,
          top = rectTop,
          right = rectRight,
          bottom = rectBottom,
          cornerRadius = CornerRadius(cornerRadius)
        )
      )
    }

    val arrowPath = Path().apply {
      val overlap = 1f
      when (placement.direction) {
        ZTooltipDirection.Top, ZTooltipDirection.Bottom -> {
          val maxX = (rectWidth - arrowBase).coerceAtLeast(0f)
          // top/bottom 下保留 start/end 的视觉差异：
          // - start：箭头偏左
          // - center：箭头居中
          // - end：箭头偏右
          val localX = when (placement.align) {
            ZTooltipAlign.Start -> arrowInset
            ZTooltipAlign.Center -> (rectWidth - arrowBase) / 2f
            ZTooltipAlign.End -> rectWidth - arrowBase - arrowInset
          }.plus(arrowShiftX).coerceIn(0f, maxX)
          val startX = rectLeft + localX

          if (placement.direction == ZTooltipDirection.Top) {
            moveTo(startX, rectBottom - overlap)
            lineTo(startX + arrowBase, rectBottom - overlap)
            lineTo(startX + arrowBase / 2f, size.height)
          } else {
            moveTo(startX, rectTop + overlap)
            lineTo(startX + arrowBase, rectTop + overlap)
            lineTo(startX + arrowBase / 2f, 0f)
          }
          close()
        }

        ZTooltipDirection.Left, ZTooltipDirection.Right -> {
          val maxY = (rectHeight - arrowBase).coerceAtLeast(0f)
          // left/right 下箭头保持贴近提示框中线，避免单行内容时视觉上过于靠上/靠下。
          val localY = ((rectHeight - arrowBase) / 2f).plus(arrowShiftY).coerceIn(0f, maxY)
          val startY = rectTop + localY

          if (placement.direction == ZTooltipDirection.Left) {
            moveTo(rectRight - overlap, startY)
            lineTo(rectRight - overlap, startY + arrowBase)
            lineTo(size.width, startY + arrowBase / 2f)
          } else {
            moveTo(rectLeft + overlap, startY)
            lineTo(rectLeft + overlap, startY + arrowBase)
            lineTo(0f, startY + arrowBase / 2f)
          }
          close()
        }
      }
    }

    val resultPath = Path().apply {
      op(roundRectPath, arrowPath, PathOperation.Union)
    }
    return Outline.Generic(resultPath)
  }
}

/**
 * 文字提示组件。
 *
 * 通过 [content] 配置 hover 时显示的提示信息，
 * 可通过 [tooltipContent] 自定义提示内容（支持多行和富文本排版），
 * 可通过 [visible] 受控显示状态（为 null 时采用内置 hover 触发逻辑），
 * 可通过 [virtualTriggering] + [virtualAnchorBounds] 开启虚拟触发锚点，
 * 可通过 [transition] 配置显示/隐藏动画，支持 `none` / `fade` / `slide-fade`。
 * 通过 [placement] 控制提示框相对参考元素的显示位置。
 * 通过 [effect] 切换主题，支持 `dark` / `light`，默认 `dark`。
 * 可通过 `backgroundColor/backgroundBrush/textColor/borderColor` 覆盖默认主题样式。
 */
@Composable
fun ZTooltip(
  content: String,
  placement: ZTooltipDefaults.Placements,
  modifier: Modifier = Modifier,
  effect: String = ZTooltipDefaults.EffectDark,
  transition: String = ZTooltipDefaults.TransitionNone,
  trigger: String = ZTooltipDefaults.TriggerHover,
  clickTriggerOnConsumed: Boolean = false,
  hostWindow: Window? = null,
  constrainToWindow: Boolean = true,
  offset: Dp = ZTooltipDefaults.Offset,
  showArrow: Boolean = true,
  enterable: Boolean = true,
  backgroundColor: Color? = null,
  backgroundBrush: Brush? = null,
  textColor: Color? = null,
  borderColor: Color? = null,
  tooltipContent: (@Composable (textColor: Color) -> Unit)? = null,
  visible: Boolean? = null,
  virtualTriggering: Boolean = false,
  virtualRef: IntRect? = null,
  virtualAnchorBounds: IntRect? = null,
  enabled: Boolean = true,
  showAfter: Int = ZTooltipDefaults.ShowAfterMillis,
  hideAfter: Int = ZTooltipDefaults.HideAfterMillis,
  autoClose: Int = ZTooltipDefaults.AutoCloseMillis,
  onVisibleChange: (Boolean) -> Unit = {},
  reference: @Composable () -> Unit
) {
  ZTooltip(
    content = content,
    modifier = modifier,
    placement = placement.value,
    effect = effect,
    transition = transition,
    trigger = trigger,
    clickTriggerOnConsumed = clickTriggerOnConsumed,
    hostWindow = hostWindow,
    constrainToWindow = constrainToWindow,
    offset = offset,
    showArrow = showArrow,
    enterable = enterable,
    backgroundColor = backgroundColor,
    backgroundBrush = backgroundBrush,
    textColor = textColor,
    borderColor = borderColor,
    tooltipContent = tooltipContent,
    visible = visible,
    virtualTriggering = virtualTriggering,
    virtualRef = virtualRef,
    virtualAnchorBounds = virtualAnchorBounds,
    enabled = enabled,
    showAfter = showAfter,
    hideAfter = hideAfter,
    autoClose = autoClose,
    onVisibleChange = onVisibleChange,
    reference = reference
  )
}

/**
 * 自定义内容版（强类型 placement）。
 */
@Composable
fun ZTooltip(
  placement: ZTooltipDefaults.Placements,
  modifier: Modifier = Modifier,
  effect: String = ZTooltipDefaults.EffectDark,
  transition: String = ZTooltipDefaults.TransitionNone,
  trigger: String = ZTooltipDefaults.TriggerHover,
  clickTriggerOnConsumed: Boolean = false,
  hostWindow: Window? = null,
  constrainToWindow: Boolean = true,
  offset: Dp = ZTooltipDefaults.Offset,
  showArrow: Boolean = true,
  enterable: Boolean = true,
  backgroundColor: Color? = null,
  backgroundBrush: Brush? = null,
  textColor: Color? = null,
  borderColor: Color? = null,
  visible: Boolean? = null,
  virtualTriggering: Boolean = false,
  virtualRef: IntRect? = null,
  virtualAnchorBounds: IntRect? = null,
  enabled: Boolean = true,
  showAfter: Int = ZTooltipDefaults.ShowAfterMillis,
  hideAfter: Int = ZTooltipDefaults.HideAfterMillis,
  autoClose: Int = ZTooltipDefaults.AutoCloseMillis,
  onVisibleChange: (Boolean) -> Unit = {},
  tooltipContent: @Composable (textColor: Color) -> Unit,
  reference: @Composable () -> Unit
) {
  ZTooltip(
    content = "",
    modifier = modifier,
    placement = placement.value,
    effect = effect,
    transition = transition,
    trigger = trigger,
    clickTriggerOnConsumed = clickTriggerOnConsumed,
    hostWindow = hostWindow,
    constrainToWindow = constrainToWindow,
    offset = offset,
    showArrow = showArrow,
    enterable = enterable,
    backgroundColor = backgroundColor,
    backgroundBrush = backgroundBrush,
    textColor = textColor,
    borderColor = borderColor,
    tooltipContent = tooltipContent,
    visible = visible,
    virtualTriggering = virtualTriggering,
    virtualRef = virtualRef,
    virtualAnchorBounds = virtualAnchorBounds,
    enabled = enabled,
    showAfter = showAfter,
    hideAfter = hideAfter,
    autoClose = autoClose,
    onVisibleChange = onVisibleChange,
    reference = reference
  )
}

/**
 * 字符串版 placement，兼容直接传入 `top-start` 等格式。
 */
@Composable
fun ZTooltip(
  content: String,
  modifier: Modifier = Modifier,
  placement: String = ZTooltipDefaults.Bottom,
  effect: String = ZTooltipDefaults.EffectDark,
  transition: String = ZTooltipDefaults.TransitionNone,
  trigger: String = ZTooltipDefaults.TriggerHover,
  clickTriggerOnConsumed: Boolean = false,
  hostWindow: Window? = null,
  constrainToWindow: Boolean = true,
  offset: Dp = ZTooltipDefaults.Offset,
  showArrow: Boolean = true,
  enterable: Boolean = true,
  backgroundColor: Color? = null,
  backgroundBrush: Brush? = null,
  textColor: Color? = null,
  borderColor: Color? = null,
  tooltipContent: (@Composable (textColor: Color) -> Unit)? = null,
  visible: Boolean? = null,
  virtualTriggering: Boolean = false,
  virtualRef: IntRect? = null,
  virtualAnchorBounds: IntRect? = null,
  enabled: Boolean = true,
  showAfter: Int = ZTooltipDefaults.ShowAfterMillis,
  hideAfter: Int = ZTooltipDefaults.HideAfterMillis,
  autoClose: Int = ZTooltipDefaults.AutoCloseMillis,
  onVisibleChange: (Boolean) -> Unit = {},
  reference: @Composable () -> Unit
) {
  var internalPopupVisible by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()
  var showJob by remember { mutableStateOf<Job?>(null) }
  var hideJob by remember { mutableStateOf<Job?>(null) }
  val resolvedPlacement = remember(placement) { parseZTooltipPlacement(placement) }
  val resolvedEffect = remember(effect) { parseZTooltipEffect(effect) }
  val resolvedTransition = remember(transition) { parseZTooltipTransition(transition) }
  val resolvedTrigger = remember(trigger) { parseZTooltipTrigger(trigger) }
  val transitionSpec = remember(resolvedTransition) { resolveZTooltipTransitionSpec(resolvedTransition) }
  val resolvedEnabled = enabled
  val resolvedVirtualAnchorBounds = virtualAnchorBounds ?: virtualRef
  val popupVisible = visible ?: internalPopupVisible
  val popupGapPx = with(LocalDensity.current) { offset.coerceAtLeast(0.dp).roundToPx() }
  val horizontalArrowCenterFromStartPx = with(LocalDensity.current) {
    (ZTooltipDefaults.ArrowInset + ZTooltipDefaults.ArrowLongSide / 2).roundToPx()
  }
  val resolvedShowAfter = showAfter.coerceAtLeast(0).toLong()
  val resolvedHideAfter = hideAfter.coerceAtLeast(0).toLong()
  val resolvedAutoClose = autoClose.coerceAtLeast(0).toLong()
  val popupGeometryState = remember { mutableStateOf(ZTooltipPopupGeometry(placement = resolvedPlacement)) }
  var popupGeometryResolved by remember { mutableStateOf(false) }

  LaunchedEffect(resolvedPlacement) {
    popupGeometryState.value = ZTooltipPopupGeometry(placement = resolvedPlacement)
    popupGeometryResolved = false
  }

  val triggerInteractionSource = remember { MutableInteractionSource() }
  val popupInteractionSource = remember { MutableInteractionSource() }
  val isTriggerHovered by triggerInteractionSource.collectIsHoveredAsState()
  val isPopupHovered by popupInteractionSource.collectIsHoveredAsState()
  val touchSlopPx = LocalViewConfiguration.current.touchSlop
  val hasVirtualAnchor = !virtualTriggering || resolvedVirtualAnchorBounds != null
  val targetPopupVisible = popupVisible && resolvedEnabled && hasVirtualAnchor
  LaunchedEffect(targetPopupVisible) {
    if (targetPopupVisible) {
      popupGeometryResolved = false
    }
  }
  var renderPopup by remember { mutableStateOf(targetPopupVisible) }
  var transitionVisible by remember { mutableStateOf(targetPopupVisible) }

  fun setPopupVisible(next: Boolean) {
    val current = popupVisible
    current
      .takeIf { it != next }
      ?.let {
        if (visible == null) {
          internalPopupVisible = next
        }
        onVisibleChange(next)
      }
  }

  fun showPopup(immediate: Boolean = false) {
    hideJob?.cancel()
    val delayMs = if (immediate) 0L else resolvedShowAfter
    showJob?.cancel()
    delayMs
      .takeIf { it > 0L }
      ?.let { actualDelay ->
        showJob = coroutineScope.launch {
          delay(actualDelay)
          setPopupVisible(true)
        }
      }
      ?: setPopupVisible(true)
  }

  fun hidePopup() {
    showJob?.cancel()
    hideJob?.cancel()
    resolvedHideAfter
      .takeIf { it > 0L }
      ?.let { delayMs ->
        hideJob = coroutineScope.launch {
          delay(delayMs)
          setPopupVisible(false)
        }
      }
      ?: setPopupVisible(false)
  }

  fun hidePopupImmediately() {
    showJob?.cancel()
    hideJob?.cancel()
    setPopupVisible(false)
  }

  LaunchedEffect(
    resolvedEnabled,
    isTriggerHovered,
    isPopupHovered,
    visible,
    virtualTriggering,
    resolvedTrigger,
    enterable,
    resolvedShowAfter
  ) {
    if (!resolvedEnabled) {
      showJob?.cancel()
      hideJob?.cancel()
      if (visible == null) {
        internalPopupVisible = false
      }
      return@LaunchedEffect
    }

    if (visible != null || virtualTriggering) {
      showJob?.cancel()
      hideJob?.cancel()
      return@LaunchedEffect
    }

    if (resolvedTrigger != ZTooltipTrigger.HOVER) {
      return@LaunchedEffect
    }

    if (isTriggerHovered || (enterable && isPopupHovered)) {
      hideJob?.cancel()
      showPopup()
    } else {
      hidePopup()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      showJob?.cancel()
      hideJob?.cancel()
    }
  }

  LaunchedEffect(targetPopupVisible, resolvedAutoClose) {
    if (!targetPopupVisible || resolvedAutoClose <= 0L) {
      return@LaunchedEffect
    }

    delay(resolvedAutoClose)
    setPopupVisible(false)
  }

  LaunchedEffect(targetPopupVisible, resolvedTransition, transitionSpec.exitDurationMillis) {
    if (targetPopupVisible) {
      renderPopup = true
      transitionVisible = true
    } else {
      transitionVisible = false
      if (resolvedTransition == ZTooltipTransition.NONE) {
        renderPopup = false
      } else {
        delay(transitionSpec.exitDurationMillis.toLong())
        renderPopup = false
      }
    }
  }

  val popupAnimationValues = resolveTooltipAnimationValues(
    visible = transitionVisible,
    transition = resolvedTransition,
    spec = transitionSpec,
    placementDirection = popupGeometryState.value.placement.direction
  )
  val currentVirtualAnchorBoundsState = rememberUpdatedState(
    if (virtualTriggering) resolvedVirtualAnchorBounds else null
  )
  val currentHostWindowState = rememberUpdatedState(hostWindow)
  val currentConstrainToWindowState = rememberUpdatedState(constrainToWindow)
  val popupPositionProvider = remember(
    resolvedPlacement,
    popupGapPx,
    horizontalArrowCenterFromStartPx
  ) {
    zTooltipPopupPositionProvider(
      placement = resolvedPlacement,
      gapPx = popupGapPx,
      horizontalArrowCenterFromStartPx = horizontalArrowCenterFromStartPx,
      virtualAnchorBoundsState = currentVirtualAnchorBoundsState,
      hostWindowState = currentHostWindowState,
      constrainToWindowState = currentConstrainToWindowState,
      onPopupResolved = { resolvedPopupPlacement, arrowShiftXPx, arrowShiftYPx ->
        val nextGeometry = ZTooltipPopupGeometry(
          placement = resolvedPopupPlacement,
          arrowShiftXPx = arrowShiftXPx.toFloat(),
          arrowShiftYPx = arrowShiftYPx.toFloat()
        )
        coroutineScope.launch {
          if (popupGeometryState.value != nextGeometry) {
            popupGeometryState.value = nextGeometry
          }
          if (!popupGeometryResolved) {
            popupGeometryResolved = true
          }
        }
      }
    )
  }

  val canUseInternalTrigger = resolvedEnabled && visible == null && !virtualTriggering
  val currentPopupVisible by rememberUpdatedState(popupVisible)
  val currentCanUseInternalTrigger by rememberUpdatedState(canUseInternalTrigger)
  val currentClickTriggerOnConsumed by rememberUpdatedState(clickTriggerOnConsumed)
  val triggerModifier = Modifier
    .then(
      if (canUseInternalTrigger && resolvedTrigger == ZTooltipTrigger.HOVER) {
        Modifier.hoverable(triggerInteractionSource)
      } else {
        Modifier
      }
    )
    .then(
      if (canUseInternalTrigger && resolvedTrigger == ZTooltipTrigger.CLICK) {
        Modifier.pointerInput(canUseInternalTrigger, touchSlopPx) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val downPosition = down.position
            val visibleOnDown = currentPopupVisible
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
            val consumedGuardPassed =
              currentClickTriggerOnConsumed || (up != null && !down.isConsumed && !up.isConsumed)
            if (up != null && consumedGuardPassed && currentCanUseInternalTrigger) {
              val delta = up.position - downPosition
              val movedEnough = delta.getDistanceSquared() > touchSlopPx * touchSlopPx
              if (movedEnough) {
                return@awaitEachGesture
              }
              if (visibleOnDown) {
                hidePopupImmediately()
              } else {
                showPopup(immediate = true)
              }
            }
          }
        }
      } else {
        Modifier
      }
    )

  Box(
    modifier = modifier.then(triggerModifier)
  ) {
    reference()

    if (renderPopup) {
      val dismissOnClickOutside = canUseInternalTrigger && resolvedTrigger == ZTooltipTrigger.CLICK
      Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = {
          if (visible != null) {
            onVisibleChange(false)
          } else {
            hidePopup()
          }
        },
        properties = PopupProperties(
          focusable = dismissOnClickOutside,
          dismissOnBackPress = dismissOnClickOutside,
          dismissOnClickOutside = dismissOnClickOutside,
          clippingEnabled = false
        )
      ) {
        val geometryReadyModifier = if (popupGeometryResolved) Modifier else Modifier.alpha(0f)
        Box(
          modifier = Modifier
            .offset(x = popupAnimationValues.offsetX, y = popupAnimationValues.offsetY)
            .alpha(popupAnimationValues.alpha)
            .then(geometryReadyModifier)
        ) {
          ZTooltipCard(
            content = content,
            tooltipContent = tooltipContent,
            placement = popupGeometryState.value.placement,
            effect = resolvedEffect,
            backgroundColor = backgroundColor,
            backgroundBrush = backgroundBrush,
            textColor = textColor,
            borderColor = borderColor,
            showArrow = showArrow,
            arrowShiftXPx = popupGeometryState.value.arrowShiftXPx,
            arrowShiftYPx = popupGeometryState.value.arrowShiftYPx,
            enterable = enterable,
            interactionSource = popupInteractionSource
          )
        }
      }
    }
  }
}

/**
 * 自定义内容版（字符串 placement）。
 */
@Composable
fun ZTooltip(
  modifier: Modifier = Modifier,
  placement: String = ZTooltipDefaults.Bottom,
  effect: String = ZTooltipDefaults.EffectDark,
  transition: String = ZTooltipDefaults.TransitionNone,
  trigger: String = ZTooltipDefaults.TriggerHover,
  clickTriggerOnConsumed: Boolean = false,
  hostWindow: Window? = null,
  constrainToWindow: Boolean = true,
  offset: Dp = ZTooltipDefaults.Offset,
  showArrow: Boolean = true,
  enterable: Boolean = true,
  backgroundColor: Color? = null,
  backgroundBrush: Brush? = null,
  textColor: Color? = null,
  borderColor: Color? = null,
  visible: Boolean? = null,
  virtualTriggering: Boolean = false,
  virtualRef: IntRect? = null,
  virtualAnchorBounds: IntRect? = null,
  enabled: Boolean = true,
  showAfter: Int = ZTooltipDefaults.ShowAfterMillis,
  hideAfter: Int = ZTooltipDefaults.HideAfterMillis,
  autoClose: Int = ZTooltipDefaults.AutoCloseMillis,
  onVisibleChange: (Boolean) -> Unit = {},
  tooltipContent: @Composable (textColor: Color) -> Unit,
  reference: @Composable () -> Unit
) {
  ZTooltip(
    content = "",
    modifier = modifier,
    placement = placement,
    effect = effect,
    transition = transition,
    trigger = trigger,
    clickTriggerOnConsumed = clickTriggerOnConsumed,
    hostWindow = hostWindow,
    constrainToWindow = constrainToWindow,
    offset = offset,
    showArrow = showArrow,
    enterable = enterable,
    backgroundColor = backgroundColor,
    backgroundBrush = backgroundBrush,
    textColor = textColor,
    borderColor = borderColor,
    tooltipContent = tooltipContent,
    visible = visible,
    virtualTriggering = virtualTriggering,
    virtualRef = virtualRef,
    virtualAnchorBounds = virtualAnchorBounds,
    enabled = enabled,
    showAfter = showAfter,
    hideAfter = hideAfter,
    autoClose = autoClose,
    onVisibleChange = onVisibleChange,
    reference = reference
  )
}

@Composable
private fun ZTooltipCard(
  content: String,
  tooltipContent: (@Composable (textColor: Color) -> Unit)?,
  placement: ZTooltipPlacement,
  effect: ZTooltipEffect,
  backgroundColor: Color?,
  backgroundBrush: Brush?,
  textColor: Color?,
  borderColor: Color?,
  showArrow: Boolean,
  arrowShiftXPx: Float,
  arrowShiftYPx: Float,
  enterable: Boolean,
  interactionSource: MutableInteractionSource
) {
  val baseStyle = remember(effect) { resolveZTooltipStyle(effect) }
  val resolvedBackgroundColor = backgroundColor ?: baseStyle.backgroundColor
  val resolvedTextColor = textColor ?: baseStyle.textColor
  val resolvedBorderColor = borderColor ?: baseStyle.borderColor
  val density = LocalDensity.current
  val arrowBasePx = with(density) { ZTooltipDefaults.ArrowLongSide.toPx() }
  val arrowHeightPx = with(density) { ZTooltipDefaults.ArrowShortSide.toPx() }
  val arrowInsetPx = with(density) {
    when (placement.direction) {
      ZTooltipDirection.Top, ZTooltipDirection.Bottom -> ZTooltipDefaults.ArrowInset.toPx()
      ZTooltipDirection.Left, ZTooltipDirection.Right -> ZTooltipDefaults.ArrowVerticalInset.toPx()
    }
  }
  val cornerRadiusPx = with(density) { ZTooltipDefaults.CornerRadius.toPx() }

  val shape = remember(
    showArrow,
    placement,
    arrowBasePx,
    arrowHeightPx,
    arrowInsetPx,
    arrowShiftXPx,
    arrowShiftYPx,
    cornerRadiusPx
  ) {
    if (showArrow) {
      TooltipShape(
        placement = placement,
        arrowBase = arrowBasePx,
        arrowHeight = arrowHeightPx,
        arrowInset = arrowInsetPx,
        arrowShiftX = arrowShiftXPx,
        arrowShiftY = arrowShiftYPx,
        cornerRadius = cornerRadiusPx
      )
    } else {
      RoundedCornerShape(ZTooltipDefaults.CornerRadius)
    }
  }

  val contentPaddingModifier = if (!showArrow) {
    Modifier
  } else {
    when (placement.direction) {
      ZTooltipDirection.Top -> Modifier.padding(bottom = ZTooltipDefaults.ArrowShortSide)
      ZTooltipDirection.Bottom -> Modifier.padding(top = ZTooltipDefaults.ArrowShortSide)
      ZTooltipDirection.Left -> Modifier.padding(end = ZTooltipDefaults.ArrowShortSide)
      ZTooltipDirection.Right -> Modifier.padding(start = ZTooltipDefaults.ArrowShortSide)
    }
  }

  Box(
    modifier = Modifier
      .then(
        if (enterable) {
          Modifier.hoverable(interactionSource)
        } else {
          Modifier
        }
      )
      .then(
        if (backgroundBrush != null) {
          Modifier.background(brush = backgroundBrush, shape = shape)
        } else {
          Modifier.background(color = resolvedBackgroundColor, shape = shape)
        }
      )
      .border(
        width = ZTooltipDefaults.BorderWidth,
        color = resolvedBorderColor,
        shape = shape
      )
      .then(contentPaddingModifier)
      .padding(
        horizontal = ZTooltipDefaults.ContentHorizontalPadding,
        vertical = ZTooltipDefaults.ContentVerticalPadding
      )
  ) {
    tooltipContent?.invoke(resolvedTextColor)
      ?: ZText(
        text = content,
        color = resolvedTextColor
      )
  }
}

private fun parseZTooltipPlacement(rawPlacement: String): ZTooltipPlacement {
  val normalized = rawPlacement
    .trim()
    .lowercase()
    .ifEmpty { ZTooltipDefaults.Top }

  val direction = when (normalized.substringBefore("-")) {
    "bottom" -> ZTooltipDirection.Bottom
    "left" -> ZTooltipDirection.Left
    "right" -> ZTooltipDirection.Right
    else -> ZTooltipDirection.Top
  }
  val align = when (normalized.substringAfter("-", missingDelimiterValue = "null")) {
    "start" -> ZTooltipAlign.Start
    "end" -> ZTooltipAlign.End
    else -> ZTooltipAlign.Center
  }
  return ZTooltipPlacement(direction = direction, align = align)
}

private fun parseZTooltipTransition(rawTransition: String): ZTooltipTransition {
  return when (rawTransition.trim().lowercase()) {
    ZTooltipDefaults.TransitionFade -> ZTooltipTransition.FADE
    ZTooltipDefaults.TransitionSlideFade -> ZTooltipTransition.SLIDE_FADE
    else -> ZTooltipTransition.NONE
  }
}

private fun parseZTooltipTrigger(rawTrigger: String): ZTooltipTrigger {
  return when (rawTrigger.trim().lowercase()) {
    ZTooltipDefaults.TriggerClick -> ZTooltipTrigger.CLICK
    else -> ZTooltipTrigger.HOVER
  }
}

private fun resolveZTooltipTransitionSpec(transition: ZTooltipTransition): ZTooltipTransitionSpec {
  return when (transition) {
    ZTooltipTransition.NONE -> ZTooltipTransitionSpec(
      enterDurationMillis = 0,
      exitDurationMillis = 0,
      enterEasing = CubicBezierEasing(0f, 0f, 1f, 1f),
      exitEasing = CubicBezierEasing(0f, 0f, 1f, 1f)
    )

    ZTooltipTransition.FADE -> ZTooltipTransitionSpec(
      enterDurationMillis = 180,
      exitDurationMillis = 120,
      enterEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
      exitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    )

    ZTooltipTransition.SLIDE_FADE -> ZTooltipTransitionSpec(
      enterDurationMillis = 300,
      exitDurationMillis = 800,
      enterEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f),
      exitEasing = CubicBezierEasing(1f, 0.5f, 0.8f, 1f)
    )
  }
}

@Composable
private fun resolveTooltipAnimationValues(
  visible: Boolean,
  transition: ZTooltipTransition,
  spec: ZTooltipTransitionSpec,
  placementDirection: ZTooltipDirection
): ZTooltipAnimationValues {
  if (transition == ZTooltipTransition.NONE) {
    return ZTooltipAnimationValues(alpha = 1f)
  }

  val durationMillis = if (visible) spec.enterDurationMillis else spec.exitDurationMillis
  val easing = if (visible) spec.enterEasing else spec.exitEasing
  val alphaTarget = if (visible) 1f else 0f
  val hiddenOffset = resolveTooltipHiddenOffset(
    transition = transition,
    placementDirection = placementDirection
  )
  val offsetXTarget = if (visible) 0.dp else hiddenOffset.x
  val offsetYTarget = if (visible) 0.dp else hiddenOffset.y

  val animatedAlpha by animateFloatAsState(
    targetValue = alphaTarget,
    animationSpec = tween(durationMillis = durationMillis, easing = easing),
    label = "z_tooltip_alpha"
  )
  val animatedOffsetX by animateDpAsState(
    targetValue = offsetXTarget,
    animationSpec = tween(durationMillis = durationMillis, easing = easing),
    label = "z_tooltip_offset_x"
  )
  val animatedOffsetY by animateDpAsState(
    targetValue = offsetYTarget,
    animationSpec = tween(durationMillis = durationMillis, easing = easing),
    label = "z_tooltip_offset_y"
  )

  return ZTooltipAnimationValues(
    alpha = animatedAlpha,
    offsetX = animatedOffsetX,
    offsetY = animatedOffsetY
  )
}

private fun resolveTooltipHiddenOffset(
  transition: ZTooltipTransition,
  placementDirection: ZTooltipDirection
): DpOffset {
  if (transition != ZTooltipTransition.SLIDE_FADE) {
    return DpOffset(0.dp, 0.dp)
  }

  return when (placementDirection) {
    ZTooltipDirection.Top -> DpOffset(0.dp, ZTooltipDefaults.SlideFadeOffsetX)
    ZTooltipDirection.Bottom -> DpOffset(0.dp, -ZTooltipDefaults.SlideFadeOffsetX)
    ZTooltipDirection.Left -> DpOffset(ZTooltipDefaults.SlideFadeOffsetX, 0.dp)
    ZTooltipDirection.Right -> DpOffset(-ZTooltipDefaults.SlideFadeOffsetX, 0.dp)
  }
}

private enum class ZTooltipEffect {
  DARK,
  LIGHT
}

private fun parseZTooltipEffect(rawEffect: String): ZTooltipEffect {
  return when (rawEffect.trim().lowercase()) {
    ZTooltipDefaults.EffectLight -> ZTooltipEffect.LIGHT
    else -> ZTooltipEffect.DARK
  }
}

private fun zTooltipPopupPositionProvider(
  placement: ZTooltipPlacement,
  gapPx: Int,
  horizontalArrowCenterFromStartPx: Int,
  virtualAnchorBoundsState: State<IntRect?>,
  hostWindowState: State<Window?>,
  constrainToWindowState: State<Boolean>,
  onPopupResolved: (ZTooltipPlacement, Int, Int) -> Unit = { _, _, _ -> }
): PopupPositionProvider {
  return object : PopupPositionProvider {
    override fun calculatePosition(
      anchorBounds: IntRect,
      windowSize: IntSize,
      layoutDirection: LayoutDirection,
      popupContentSize: IntSize
    ): IntOffset {
      val resolvedAnchorBounds = virtualAnchorBoundsState.value ?: anchorBounds
      val constraintBounds = resolveTooltipConstraintBounds(
        windowSize = windowSize,
        hostWindow = hostWindowState.value,
        constrainToWindow = constrainToWindowState.value
      )
      val resolvedPlacement = resolveTooltipAutoFlippedPlacement(
        placement = placement,
        anchorBounds = resolvedAnchorBounds,
        constraintBounds = constraintBounds,
        popupContentSize = popupContentSize,
        gapPx = gapPx
      )
      val alignedX = resolveTooltipAlignedX(
        anchorBounds = resolvedAnchorBounds,
        popupContentSize = popupContentSize,
        align = resolvedPlacement.align,
        layoutDirection = layoutDirection,
        arrowCenterFromStartPx = horizontalArrowCenterFromStartPx
      )
      val alignedY = resolveTooltipAlignedY(
        anchorBounds = resolvedAnchorBounds,
        popupContentSize = popupContentSize,
        align = resolvedPlacement.align
      )

      val rawX: Int
      val rawY: Int
      when (resolvedPlacement.direction) {
        ZTooltipDirection.Top -> {
          rawX = alignedX
          rawY = resolvedAnchorBounds.top - popupContentSize.height - gapPx
        }

        ZTooltipDirection.Bottom -> {
          rawX = alignedX
          rawY = resolvedAnchorBounds.bottom + gapPx
        }

        ZTooltipDirection.Left -> {
          rawX = resolvedAnchorBounds.left - popupContentSize.width - gapPx
          rawY = alignedY
        }

        ZTooltipDirection.Right -> {
          rawX = resolvedAnchorBounds.right + gapPx
          rawY = alignedY
        }
      }

      val maxX = (constraintBounds.right - popupContentSize.width).coerceAtLeast(constraintBounds.left)
      val maxY = (constraintBounds.bottom - popupContentSize.height).coerceAtLeast(constraintBounds.top)
      val clampedX = rawX.coerceIn(constraintBounds.left, maxX)
      val clampedY = rawY.coerceIn(constraintBounds.top, maxY)
      val arrowShiftXPx = rawX - clampedX
      val arrowShiftYPx = rawY - clampedY
      onPopupResolved(resolvedPlacement, arrowShiftXPx, arrowShiftYPx)
      return IntOffset(
        x = clampedX,
        y = clampedY
      )
    }
  }
}

private fun resolveTooltipAutoFlippedPlacement(
  placement: ZTooltipPlacement,
  anchorBounds: IntRect,
  constraintBounds: IntRect,
  popupContentSize: IntSize,
  gapPx: Int
): ZTooltipPlacement {
  val canShowTop = anchorBounds.top - popupContentSize.height - gapPx >= constraintBounds.top
  val canShowBottom = anchorBounds.bottom + popupContentSize.height + gapPx <= constraintBounds.bottom
  val canShowLeft = anchorBounds.left - popupContentSize.width - gapPx >= constraintBounds.left
  val canShowRight = anchorBounds.right + popupContentSize.width + gapPx <= constraintBounds.right

  val resolvedDirection = when (placement.direction) {
    ZTooltipDirection.Top -> {
      if (!canShowTop && canShowBottom) ZTooltipDirection.Bottom else ZTooltipDirection.Top
    }

    ZTooltipDirection.Bottom -> {
      if (!canShowBottom && canShowTop) ZTooltipDirection.Top else ZTooltipDirection.Bottom
    }

    ZTooltipDirection.Left -> {
      if (!canShowLeft && canShowRight) ZTooltipDirection.Right else ZTooltipDirection.Left
    }

    ZTooltipDirection.Right -> {
      if (!canShowRight && canShowLeft) ZTooltipDirection.Left else ZTooltipDirection.Right
    }
  }

  // left/right 下 tooltip 的箭头始终走中线策略，避免单行内容时 start/end 产生明显上/下偏移。
  val resolvedAlign = when (resolvedDirection) {
    ZTooltipDirection.Left, ZTooltipDirection.Right -> ZTooltipAlign.Center
    ZTooltipDirection.Top, ZTooltipDirection.Bottom -> placement.align
  }

  return if (resolvedDirection == placement.direction && resolvedAlign == placement.align) {
    placement
  } else {
    placement.copy(direction = resolvedDirection, align = resolvedAlign)
  }
}

private fun resolveTooltipConstraintBounds(
  windowSize: IntSize,
  hostWindow: Window?,
  constrainToWindow: Boolean
): IntRect {
  val windowBounds = IntRect(0, 0, windowSize.width, windowSize.height)
  if (constrainToWindow) {
    return windowBounds
  }
  val resolvedHostWindow = resolveTooltipHostWindow(hostWindow) ?: return windowBounds
  val graphicsConfiguration = resolvedHostWindow.graphicsConfiguration ?: return windowBounds
  val screenBounds = graphicsConfiguration.bounds
  val screenInsets = runCatching {
    Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
  }.getOrNull()

  val usableLeft = screenBounds.x + (screenInsets?.left ?: 0)
  val usableTop = screenBounds.y + (screenInsets?.top ?: 0)
  val usableRight = screenBounds.x + screenBounds.width - (screenInsets?.right ?: 0)
  val usableBottom = screenBounds.y + screenBounds.height - (screenInsets?.bottom ?: 0)

  if (usableRight <= usableLeft || usableBottom <= usableTop) {
    return windowBounds
  }

  return IntRect(
    left = usableLeft - resolvedHostWindow.x,
    top = usableTop - resolvedHostWindow.y,
    right = usableRight - resolvedHostWindow.x,
    bottom = usableBottom - resolvedHostWindow.y
  )
}

private fun resolveTooltipHostWindow(hostWindow: Window?): Window? {
  if (hostWindow != null && hostWindow.isShowing) {
    return hostWindow
  }

  val windows = Window.getWindows().filter { it.isShowing && (it is Frame || it is Dialog) }
  if (windows.isEmpty()) {
    return null
  }

  val pointerLocation = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
  val pointerWindow = pointerLocation?.let { pointer ->
    resolveWindowAtPoint(windows, pointer)
  }
  if (pointerWindow != null) {
    return pointerWindow
  }

  val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
  val activeWindow = focusManager.activeWindow
  if (activeWindow != null && activeWindow.isShowing && (activeWindow is Frame || activeWindow is Dialog)) {
    return activeWindow
  }

  return windows.firstOrNull { it.isFocused } ?: windows.first()
}

private fun resolveWindowAtPoint(windows: List<Window>, point: Point): Window? {
  return windows
    .asReversed()
    .firstOrNull { window -> window.bounds.contains(point) }
}

private fun resolveTooltipAlignedX(
  anchorBounds: IntRect,
  popupContentSize: IntSize,
  align: ZTooltipAlign,
  layoutDirection: LayoutDirection,
  arrowCenterFromStartPx: Int
): Int {
  val centerX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
  val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
  val startArrowCenterFromLeft = if (layoutDirection == LayoutDirection.Rtl) {
    popupContentSize.width - arrowCenterFromStartPx
  } else {
    arrowCenterFromStartPx
  }
  val endArrowCenterFromLeft = popupContentSize.width - startArrowCenterFromLeft
  return when (align) {
    ZTooltipAlign.Start -> anchorCenterX - startArrowCenterFromLeft
    ZTooltipAlign.Center -> centerX
    ZTooltipAlign.End -> anchorCenterX - endArrowCenterFromLeft
  }
}

private fun resolveTooltipAlignedY(
  anchorBounds: IntRect,
  popupContentSize: IntSize,
  align: ZTooltipAlign
): Int {
  val centerY = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
  return when (align) {
    ZTooltipAlign.Start, ZTooltipAlign.Center, ZTooltipAlign.End -> centerY
  }
}

private fun resolveZTooltipStyle(effect: ZTooltipEffect): ZTooltipStyle {
  return when (effect) {
    ZTooltipEffect.DARK -> ZTooltipStyle(
      backgroundColor = ZTooltipDefaults.DarkBackgroundColor,
      textColor = ZTooltipDefaults.DarkTextColor,
      borderColor = ZTooltipDefaults.DarkBorderColor
    )

    ZTooltipEffect.LIGHT -> ZTooltipStyle(
      backgroundColor = ZTooltipDefaults.LightBackgroundColor,
      textColor = ZTooltipDefaults.LightTextColor,
      borderColor = ZTooltipDefaults.LightBorderColor
    )
  }
}
