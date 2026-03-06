package top.zhjh.zui.composable

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import top.zhjh.zui.theme.isAppInDarkTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 滑块提示气泡（Tooltip）的展示方向。
 *
 * 说明：
 * - `TOP` 为默认值，气泡显示在滑块上方；
 * - 其余方向主要用于演示或特殊布局需求。
 */
enum class ZSliderTooltipPlacement {
  TOP,
  BOTTOM,
  LEFT,
  RIGHT
}

/**
 * 滑块标记点（marks）配置项。
 *
 * @property label 标记下方的文本内容（例如 `37°C`）。
 * @property color 标记文本颜色；当为 `Color.Unspecified` 时使用组件默认色。
 */
@Immutable
data class ZSliderMark(
  val label: String,
  val color: Color = Color.Unspecified
)

/**
 * 内部使用的已解析标记：
 * - `fraction` 为 0~1 的相对位置；
 * - `mark` 保留原始标记配置。
 */
private data class ZSliderResolvedMark(
  val fraction: Float,
  val mark: ZSliderMark
)

private enum class ZSliderRangeThumb {
  START,
  END
}

/**
 * 范围滑块（双端点）版本。
 *
 * 适用场景：
 * - 价格区间、时间区间、评分区间等“最小值 + 最大值”输入。
 *
 * 参数说明（重点）：
 * - `value`：范围值数组，约定 `[start, end]`；
 * - `min`/`max`：最小值与最大值，优先于 `valueRange`，并支持自动纠正传反顺序；
 * - `step`：步长，`> 0` 时启用离散吸附；
 * - `showStops`：显示离散断点，仅显示“未被高亮覆盖”的断点；
 * - `marks`：显示标记点与文本，标记点不会被高亮覆盖隐藏；
 * - `showTooltip`：拖拽时显示气泡提示；
 * - `placement`：提示气泡位置；
 * - `formatTooltip`：提示文案格式化函数。
 *
 */
@Composable
fun ZSlider(
  value: FloatArray,
  onValueChange: (FloatArray) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
  min: Float = valueRange.start,
  max: Float = valueRange.endInclusive,
  enabled: Boolean = true,
  activeColor: Color? = null,
  inactiveColor: Color? = null,
  step: Float = 0f,
  showStops: Boolean = false,
  range: Boolean = true,
  vertical: Boolean = false,
  marks: Map<Float, ZSliderMark> = emptyMap(),
  metrics: ZSliderMetrics = ZSliderDefaults.Metrics,
  showTooltip: Boolean = true,
  placement: ZSliderTooltipPlacement = ZSliderTooltipPlacement.TOP,
  formatTooltip: (Float) -> String = { it.roundToInt().toString() }
) {
  // min/max 优先级高于 valueRange；若传反（min > max）会自动纠正，保证后续逻辑稳定。
  val resolvedMin = kotlin.math.min(min, max)
  val resolvedMax = kotlin.math.max(min, max)

  if (!range) {
    // range = false 时复用单值版本，避免维护两套重复逻辑。
    val fallbackValue = value.firstOrNull() ?: resolvedMin
    ZSlider(
      value = fallbackValue,
      onValueChange = { single ->
        val end = value.getOrNull(1) ?: single
        onValueChange(floatArrayOf(single, end))
      },
      modifier = modifier,
      valueRange = resolvedMin..resolvedMax,
      min = resolvedMin,
      max = resolvedMax,
      enabled = enabled,
      activeColor = activeColor,
      inactiveColor = inactiveColor,
      step = step,
      showStops = showStops,
      vertical = vertical,
      marks = marks,
      metrics = metrics,
      showTooltip = showTooltip,
      placement = placement,
      formatTooltip = formatTooltip
    )
    return
  }

  val minValue = resolvedMin
  val maxValue = resolvedMax
  val resolvedMarks = resolveSliderMarks(marks = marks, minValue = minValue, maxValue = maxValue)
  val rangeSize = (maxValue - minValue).coerceAtLeast(0f)
  val normalizedStep = if (step > 0f && step <= rangeSize) step else 0f
  val hasDiscreteStep = normalizedStep > 0f && rangeSize > 0f
  val stopFractions = if (hasDiscreteStep) {
    resolveSliderStopFractions(rangeSize = rangeSize, step = normalizedStep)
  } else {
    emptyList()
  }

  // 将任意输入值“裁剪 + 步长吸附”到合法值。
  fun snapValue(rawValue: Float): Float {
    val clampedValue = rawValue.coerceIn(minValue, maxValue)
    if (!hasDiscreteStep) {
      return clampedValue
    }
    val stepIndex = ((clampedValue - minValue) / normalizedStep).roundToInt()
    return (minValue + stepIndex * normalizedStep).coerceIn(minValue, maxValue)
  }

  val rawStart = value.getOrNull(0) ?: minValue
  val rawEnd = value.getOrNull(1) ?: maxValue
  var coercedStart = snapValue(rawStart)
  var coercedEnd = snapValue(rawEnd)
  // 保证范围值始终满足 start <= end，外部即便传反也自动修正。
  if (coercedStart > coercedEnd) {
    val tmp = coercedStart
    coercedStart = coercedEnd
    coercedEnd = tmp
  }
  val startFraction = if (rangeSize == 0f) {
    0f
  } else {
    (coercedStart - minValue) / rangeSize
  }.coerceIn(0f, 1f)
  val endFraction = if (rangeSize == 0f) {
    0f
  } else {
    (coercedEnd - minValue) / rangeSize
  }.coerceIn(0f, 1f)

  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val isDarkTheme = isAppInDarkTheme()
  val style = resolveZSliderStyle(
    enabled = enabled,
    isHovered = isHovered,
    isDarkTheme = isDarkTheme,
    activeColor = activeColor,
    inactiveColor = inactiveColor
  )
  val defaultMarkColor = if (isDarkTheme) Color(0xffa3a6ad) else Color(0xff909399)
  val marksAreaHeight = if (resolvedMarks.isEmpty()) 0.dp else 28.dp
  val totalContainerHeight = metrics.containerHeight + marksAreaHeight

  var sliderWidthPx by remember { mutableFloatStateOf(0f) }
  var tooltipWidthPx by remember { mutableIntStateOf(0) }
  var tooltipHeightPx by remember { mutableIntStateOf(0) }
  var draggingThumb by remember { mutableStateOf<ZSliderRangeThumb?>(null) }
  var dragStartFraction by remember { mutableFloatStateOf(startFraction) }
  var dragEndFraction by remember { mutableFloatStateOf(endFraction) }

  LaunchedEffect(startFraction, endFraction, draggingThumb) {
    if (draggingThumb == null) {
      dragStartFraction = startFraction
      dragEndFraction = endFraction
    }
  }

  val density = LocalDensity.current
  val containerHeightPx = with(density) { metrics.containerHeight.toPx() }
  val touchHeightPx = with(density) { metrics.touchHeight.toPx() }
  val thumbSizePx = with(density) { metrics.thumbSize.toPx() }
  val thumbRadiusPx = thumbSizePx / 2f
  val thumbTopPx = (containerHeightPx - touchHeightPx) / 2f + (touchHeightPx - thumbSizePx) / 2f
  val trackUsableWidthPx = (sliderWidthPx - thumbSizePx).coerceAtLeast(0f)
  val sideTooltipGapPx = with(density) { 6.dp.toPx() }

  fun valueFromFraction(fraction: Float): Float {
    if (rangeSize == 0f) return minValue
    return minValue + fraction.coerceIn(0f, 1f) * rangeSize
  }

  fun fractionFromValue(v: Float): Float {
    if (rangeSize == 0f) return 0f
    return ((v - minValue) / rangeSize).coerceIn(0f, 1f)
  }

  val displayedStartFraction = if (draggingThumb != null) {
    if (hasDiscreteStep && rangeSize > 0f) {
      fractionFromValue(snapValue(valueFromFraction(dragStartFraction)))
    } else {
      dragStartFraction
    }
  } else {
    startFraction
  }
  val displayedEndFraction = if (draggingThumb != null) {
    if (hasDiscreteStep && rangeSize > 0f) {
      fractionFromValue(snapValue(valueFromFraction(dragEndFraction)))
    } else {
      dragEndFraction
    }
  } else {
    endFraction
  }
  val displayedStartValue = valueFromFraction(displayedStartFraction)
  val displayedEndValue = valueFromFraction(displayedEndFraction)

  if (vertical) {
    val verticalMarksAreaWidth = rememberVerticalMarksAreaWidth(resolvedMarks)
    BoxWithConstraints(modifier = modifier) {
      require(maxHeight != Dp.Infinity) {
        "ZSlider vertical range mode requires an explicit height. Example: modifier = Modifier.height(200.dp)"
      }

      val verticalDensity = LocalDensity.current
      val containerWidthPx = with(verticalDensity) { metrics.containerHeight.toPx() }
      val thumbSizePx = with(verticalDensity) { metrics.thumbSize.toPx() }
      val thumbRadiusPx = thumbSizePx / 2f
      val trackUsableHeightPx = (with(verticalDensity) { maxHeight.toPx() } - thumbSizePx).coerceAtLeast(0f)
      val activeTrackTopDp = with(verticalDensity) {
        (trackUsableHeightPx * (1f - displayedEndFraction)).toDp()
      }
      val activeTrackHeightDp = with(verticalDensity) {
        (trackUsableHeightPx * (displayedEndFraction - displayedStartFraction).coerceAtLeast(0f)).toDp()
      }
      val startThumbCenterYPx = thumbRadiusPx + trackUsableHeightPx * (1f - displayedStartFraction)
      val endThumbCenterYPx = thumbRadiusPx + trackUsableHeightPx * (1f - displayedEndFraction)
      val startThumbOffsetYPx = (startThumbCenterYPx - thumbRadiusPx).roundToInt()
      val endThumbOffsetYPx = (endThumbCenterYPx - thumbRadiusPx).roundToInt()

      fun valueFromPosition(positionY: Float): Float {
        if (rangeSize == 0f || trackUsableHeightPx <= 0f) {
          return minValue
        }
        val normalized = ((trackUsableHeightPx + thumbRadiusPx - positionY) / trackUsableHeightPx).coerceIn(0f, 1f)
        return snapValue(minValue + normalized * rangeSize)
      }

      fun nearestThumb(positionY: Float): ZSliderRangeThumb {
        return if (abs(positionY - startThumbCenterYPx) <= abs(positionY - endThumbCenterYPx)) {
          ZSliderRangeThumb.START
        } else {
          ZSliderRangeThumb.END
        }
      }

      fun emitRangeFromFractions(startFr: Float, endFr: Float) {
        val a = snapValue(valueFromFraction(startFr))
        val b = snapValue(valueFromFraction(endFr))
        if (a <= b) {
          onValueChange(floatArrayOf(a, b))
        } else {
          onValueChange(floatArrayOf(b, a))
        }
      }

      val draggableState = rememberDraggableState { delta ->
        if (!enabled || trackUsableHeightPx <= 0f || rangeSize == 0f) {
          return@rememberDraggableState
        }
        val activeThumb = draggingThumb ?: if (delta > 0f) ZSliderRangeThumb.START else ZSliderRangeThumb.END
        draggingThumb = activeThumb
        when (activeThumb) {
          ZSliderRangeThumb.START -> {
            val nextFraction = (dragStartFraction - delta / trackUsableHeightPx).coerceIn(0f, dragEndFraction)
            dragStartFraction = nextFraction
          }
          ZSliderRangeThumb.END -> {
            val nextFraction = (dragEndFraction - delta / trackUsableHeightPx).coerceIn(dragStartFraction, 1f)
            dragEndFraction = nextFraction
          }
        }
        emitRangeFromFractions(dragStartFraction, dragEndFraction)
      }

      val rootInteractiveModifier = if (enabled) {
        Modifier
          .hoverable(interactionSource = interactionSource)
          .pointerInput(rangeSize, minValue, maxValue, trackUsableHeightPx, normalizedStep, displayedStartFraction, displayedEndFraction) {
            detectTapGestures { offset ->
              val tappedValue = valueFromPosition(offset.y)
              val thumb = nearestThumb(offset.y)
              val nextStart = if (thumb == ZSliderRangeThumb.START) {
                kotlin.math.min(tappedValue, coercedEnd)
              } else {
                coercedStart
              }
              val nextEnd = if (thumb == ZSliderRangeThumb.END) {
                kotlin.math.max(tappedValue, coercedStart)
              } else {
                coercedEnd
              }
              onValueChange(floatArrayOf(nextStart, nextEnd))
            }
          }
          .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStarted = { startOffset ->
              dragStartFraction = startFraction
              dragEndFraction = endFraction
              draggingThumb = nearestThumb(startOffset.y)
            },
            onDragStopped = {
              draggingThumb = null
            }
          )
      } else {
        Modifier
      }

      Box(
        modifier = Modifier
          .zIndex(if (draggingThumb != null) 1f else 0f)
          .width(metrics.containerHeight + verticalMarksAreaWidth)
          .fillMaxHeight()
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .width(metrics.containerHeight)
            .fillMaxHeight()
            .then(rootInteractiveModifier)
        ) {
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .width(metrics.touchHeight)
              .fillMaxHeight()
          ) {
            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .padding(vertical = metrics.thumbSize / 2)
                .width(metrics.trackHeight)
                .background(style.inactiveTrackColor, RoundedCornerShape(percent = 50))
            )
            Box(
              modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = metrics.thumbSize / 2 + activeTrackTopDp)
                .width(metrics.trackHeight)
                .height(activeTrackHeightDp)
                .background(style.activeTrackColor, RoundedCornerShape(percent = 50))
            )
            if (showStops && stopFractions.isNotEmpty()) {
              val stopRadiusPx = with(verticalDensity) { (metrics.trackHeight / 2f).toPx() }
              val stopColor = Color.White
              Canvas(
                modifier = Modifier
                  .align(Alignment.Center)
                  .fillMaxHeight()
                  .width(metrics.touchHeight)
                  .padding(vertical = metrics.thumbSize / 2)
              ) {
                val centerX = this.size.width / 2f
                val startThreshold = displayedStartFraction - 0.0001f
                val endThreshold = displayedEndFraction + 0.0001f
                for (stopFraction in stopFractions) {
                  if (stopFraction in startThreshold..endThreshold) continue
                  val stopY = this.size.height * (1f - stopFraction)
                  drawCircle(
                    color = stopColor,
                    radius = stopRadiusPx,
                    center = Offset(centerX, stopY)
                  )
                }
              }
            }
            if (resolvedMarks.isNotEmpty()) {
              val markRadiusPx = with(verticalDensity) { (metrics.trackHeight / 2f).toPx() }
              Canvas(
                modifier = Modifier
                  .align(Alignment.Center)
                  .fillMaxHeight()
                  .width(metrics.touchHeight)
                  .padding(vertical = metrics.thumbSize / 2)
              ) {
                val centerX = this.size.width / 2f
                resolvedMarks.forEach { resolvedMark ->
                  val markerY = this.size.height * (1f - resolvedMark.fraction)
                  drawCircle(
                    color = Color.White,
                    radius = markRadiusPx,
                    center = Offset(centerX, markerY)
                  )
                }
              }
            }
            Box(
              modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(x = 0, y = startThumbOffsetYPx) }
                .size(metrics.thumbSize)
                .background(style.thumbColor, CircleShape)
                .border(
                  width = metrics.thumbBorderWidth,
                  color = style.thumbBorderColor,
                  shape = CircleShape
                )
            )
            Box(
              modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(x = 0, y = endThumbOffsetYPx) }
                .size(metrics.thumbSize)
                .background(style.thumbColor, CircleShape)
                .border(
                  width = metrics.thumbBorderWidth,
                  color = style.thumbBorderColor,
                  shape = CircleShape
                )
            )
          }

          if (showTooltip && enabled && draggingThumb != null) {
            val tooltipSpacingPx = with(verticalDensity) { 2.dp.roundToPx() }
            val bottomTooltipGapPx = with(verticalDensity) { 8.dp.roundToPx() }
            val resolvedTooltipWidthPx = if (tooltipWidthPx == 0) {
              with(verticalDensity) { 44.dp.roundToPx() }
            } else {
              tooltipWidthPx
            }
            val resolvedTooltipHeightPx = if (tooltipHeightPx == 0) {
              with(verticalDensity) { 34.dp.roundToPx() }
            } else {
              tooltipHeightPx
            }
            val tooltipAnchorCenterYPx = if (draggingThumb == ZSliderRangeThumb.START) {
              startThumbCenterYPx
            } else {
              endThumbCenterYPx
            }
            val tooltipAnchorValue = if (draggingThumb == ZSliderRangeThumb.START) {
              displayedStartValue
            } else {
              displayedEndValue
            }
            val thumbTopPx = tooltipAnchorCenterYPx - thumbRadiusPx
            val thumbBottomPx = tooltipAnchorCenterYPx + thumbRadiusPx
            val thumbCenterXPx = containerWidthPx / 2f
            val tooltipOffsetX = when (placement) {
              ZSliderTooltipPlacement.TOP,
              ZSliderTooltipPlacement.BOTTOM -> (thumbCenterXPx - resolvedTooltipWidthPx / 2f).roundToInt()
              ZSliderTooltipPlacement.LEFT -> (
                thumbCenterXPx - thumbRadiusPx - resolvedTooltipWidthPx - sideTooltipGapPx
                ).roundToInt()
              ZSliderTooltipPlacement.RIGHT -> (
                thumbCenterXPx + thumbRadiusPx + sideTooltipGapPx
                ).roundToInt()
            }
            val tooltipOffsetY = when (placement) {
              ZSliderTooltipPlacement.TOP -> (thumbTopPx - resolvedTooltipHeightPx - tooltipSpacingPx).roundToInt()
              ZSliderTooltipPlacement.BOTTOM -> (thumbBottomPx + bottomTooltipGapPx).roundToInt()
              ZSliderTooltipPlacement.LEFT,
              ZSliderTooltipPlacement.RIGHT -> (tooltipAnchorCenterYPx - resolvedTooltipHeightPx / 2f).roundToInt()
            }
            ZSliderTooltip(
              text = formatTooltip(tooltipAnchorValue),
              backgroundColor = style.tooltipBackgroundColor,
              textColor = style.tooltipTextColor,
              placement = placement,
              modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(tooltipOffsetX, tooltipOffsetY) }
                .wrapContentSize(unbounded = true)
                .onSizeChanged {
                  tooltipWidthPx = it.width
                  tooltipHeightPx = it.height
                }
            )
          }
        }
        if (resolvedMarks.isNotEmpty()) {
          ZSliderVerticalMarks(
            marks = resolvedMarks,
            defaultLabelColor = defaultMarkColor,
            thumbSize = metrics.thumbSize,
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(start = metrics.containerHeight + 8.dp)
              .fillMaxHeight()
          )
        }
      }
    }
    return
  }

  val startThumbCenterPx = thumbRadiusPx + trackUsableWidthPx * displayedStartFraction
  val endThumbCenterPx = thumbRadiusPx + trackUsableWidthPx * displayedEndFraction
  val startThumbOffsetXPx = (startThumbCenterPx - thumbRadiusPx).roundToInt()
  val endThumbOffsetXPx = (endThumbCenterPx - thumbRadiusPx).roundToInt()
  val activeTrackStartDp = with(density) { (trackUsableWidthPx * displayedStartFraction).toDp() }
  val activeTrackWidthDp = with(density) {
    (trackUsableWidthPx * (displayedEndFraction - displayedStartFraction).coerceAtLeast(0f)).toDp()
  }

  fun valueFromPosition(positionX: Float): Float {
    if (rangeSize == 0f || trackUsableWidthPx <= 0f) {
      return minValue
    }
    val normalized = ((positionX - thumbRadiusPx) / trackUsableWidthPx).coerceIn(0f, 1f)
    return snapValue(minValue + normalized * rangeSize)
  }

  fun nearestThumb(positionX: Float): ZSliderRangeThumb {
    return if (abs(positionX - startThumbCenterPx) <= abs(positionX - endThumbCenterPx)) {
      ZSliderRangeThumb.START
    } else {
      ZSliderRangeThumb.END
    }
  }

  fun emitRangeFromFractions(startFr: Float, endFr: Float) {
    val a = snapValue(valueFromFraction(startFr))
    val b = snapValue(valueFromFraction(endFr))
    if (a <= b) {
      onValueChange(floatArrayOf(a, b))
    } else {
      onValueChange(floatArrayOf(b, a))
    }
  }

  val draggableState = rememberDraggableState { delta ->
    if (!enabled || trackUsableWidthPx <= 0f || rangeSize == 0f) {
      return@rememberDraggableState
    }
    // 若未明确当前拖拽端点，按拖拽方向推断优先操作哪一端。
    val activeThumb = draggingThumb ?: if (delta < 0f) ZSliderRangeThumb.START else ZSliderRangeThumb.END
    draggingThumb = activeThumb
    when (activeThumb) {
      ZSliderRangeThumb.START -> {
        val nextFraction = (dragStartFraction + delta / trackUsableWidthPx).coerceIn(0f, dragEndFraction)
        dragStartFraction = nextFraction
      }
      ZSliderRangeThumb.END -> {
        val nextFraction = (dragEndFraction + delta / trackUsableWidthPx).coerceIn(dragStartFraction, 1f)
        dragEndFraction = nextFraction
      }
    }
    emitRangeFromFractions(dragStartFraction, dragEndFraction)
  }

  val rootInteractiveModifier = if (enabled) {
    Modifier
      .hoverable(interactionSource = interactionSource)
      .pointerInput(rangeSize, minValue, maxValue, trackUsableWidthPx, normalizedStep, displayedStartFraction, displayedEndFraction) {
        detectTapGestures { offset ->
          // 点击轨道时，仅移动距离点击点更近的端点。
          val tappedValue = valueFromPosition(offset.x)
          val thumb = nearestThumb(offset.x)
          val nextStart = if (thumb == ZSliderRangeThumb.START) {
            kotlin.math.min(tappedValue, coercedEnd)
          } else {
            coercedStart
          }
          val nextEnd = if (thumb == ZSliderRangeThumb.END) {
            kotlin.math.max(tappedValue, coercedStart)
          } else {
            coercedEnd
          }
          onValueChange(floatArrayOf(nextStart, nextEnd))
        }
      }
      .draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = { startOffset ->
          dragStartFraction = startFraction
          dragEndFraction = endFraction
          draggingThumb = nearestThumb(startOffset.x)
        },
        onDragStopped = {
          draggingThumb = null
        }
      )
  } else {
    Modifier
  }

  Box(
    modifier = Modifier
      .zIndex(if (draggingThumb != null) 1f else 0f)
      .fillMaxWidth()
      .height(totalContainerHeight)
      .then(modifier)
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.TopStart)
        .fillMaxWidth()
        .height(metrics.containerHeight)
        .onSizeChanged {
          sliderWidthPx = it.width.toFloat()
        }
        .then(rootInteractiveModifier)
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .fillMaxWidth()
          .height(metrics.touchHeight)
      ) {
        // 轨道底层：未激活区域
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxWidth()
            .padding(horizontal = metrics.thumbSize / 2)
            .height(metrics.trackHeight)
            .background(style.inactiveTrackColor, RoundedCornerShape(percent = 50))
        )
        // 轨道高亮层：已选中范围
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = metrics.thumbSize / 2 + activeTrackStartDp)
            .height(metrics.trackHeight)
            .background(style.activeTrackColor, RoundedCornerShape(percent = 50))
            .width(activeTrackWidthDp)
        )
        if (showStops && stopFractions.isNotEmpty()) {
          val stopRadiusPx = with(density) { (metrics.trackHeight / 2f).toPx() }
          val stopColor = Color.White
          Canvas(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .fillMaxWidth()
              .padding(horizontal = metrics.thumbSize / 2)
              .height(metrics.touchHeight)
          ) {
            val centerY = this.size.height / 2f
            val startThreshold = displayedStartFraction - 0.0001f
            val endThreshold = displayedEndFraction + 0.0001f
            for (stopFraction in stopFractions) {
              // 范围内（已被高亮覆盖）的 stop 不再绘制。
              if (stopFraction in startThreshold..endThreshold) continue
              val stopX = this.size.width * stopFraction
              drawCircle(
                color = stopColor,
                radius = stopRadiusPx,
                center = Offset(stopX, centerY)
              )
            }
          }
        }
        if (resolvedMarks.isNotEmpty()) {
          // marks 独立于 showStops：始终显示，不受高亮覆盖影响。
          val markRadiusPx = with(density) { (metrics.trackHeight / 2f).toPx() }
          Canvas(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .fillMaxWidth()
              .padding(horizontal = metrics.thumbSize / 2)
              .height(metrics.touchHeight)
          ) {
            val centerY = this.size.height / 2f
            resolvedMarks.forEach { resolvedMark ->
              val markerX = this.size.width * resolvedMark.fraction
              drawCircle(
                color = Color.White,
                radius = markRadiusPx,
                center = Offset(markerX, centerY)
              )
            }
          }
        }
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(x = startThumbOffsetXPx, y = 0) }
            .size(metrics.thumbSize)
            .background(style.thumbColor, CircleShape)
            .border(
              width = metrics.thumbBorderWidth,
              color = style.thumbBorderColor,
              shape = CircleShape
            )
        )
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(x = endThumbOffsetXPx, y = 0) }
            .size(metrics.thumbSize)
            .background(style.thumbColor, CircleShape)
            .border(
              width = metrics.thumbBorderWidth,
              color = style.thumbBorderColor,
              shape = CircleShape
            )
        )
      }

      if (showTooltip && enabled && draggingThumb != null) {
        val tooltipSpacingPx = with(density) { 2.dp.roundToPx() }
        val bottomTooltipGapPx = with(density) { 8.dp.roundToPx() }
        val resolvedTooltipWidthPx = if (tooltipWidthPx == 0) {
          with(density) { 44.dp.roundToPx() }
        } else {
          tooltipWidthPx
        }
        val resolvedTooltipHeightPx = if (tooltipHeightPx == 0) {
          with(density) { 34.dp.roundToPx() }
        } else {
          tooltipHeightPx
        }
        val tooltipAnchorCenterPx = if (draggingThumb == ZSliderRangeThumb.START) startThumbCenterPx else endThumbCenterPx
        val tooltipAnchorValue = if (draggingThumb == ZSliderRangeThumb.START) displayedStartValue else displayedEndValue
        val thumbCenterYPx = thumbTopPx + thumbRadiusPx
        val tooltipOffsetX = when (placement) {
          ZSliderTooltipPlacement.TOP,
          ZSliderTooltipPlacement.BOTTOM -> (tooltipAnchorCenterPx - resolvedTooltipWidthPx / 2f).roundToInt()
          ZSliderTooltipPlacement.LEFT -> (
            tooltipAnchorCenterPx - thumbRadiusPx - resolvedTooltipWidthPx - sideTooltipGapPx
            ).roundToInt()
          ZSliderTooltipPlacement.RIGHT -> (
            tooltipAnchorCenterPx + thumbRadiusPx + sideTooltipGapPx
            ).roundToInt()
        }
        val tooltipOffsetY = when (placement) {
          ZSliderTooltipPlacement.TOP -> (thumbTopPx - resolvedTooltipHeightPx - tooltipSpacingPx).roundToInt()
          ZSliderTooltipPlacement.BOTTOM -> (thumbTopPx + thumbSizePx + bottomTooltipGapPx).roundToInt()
          ZSliderTooltipPlacement.LEFT,
          ZSliderTooltipPlacement.RIGHT -> (thumbCenterYPx - resolvedTooltipHeightPx / 2f).roundToInt()
        }
        ZSliderTooltip(
          text = formatTooltip(tooltipAnchorValue),
          backgroundColor = style.tooltipBackgroundColor,
          textColor = style.tooltipTextColor,
          placement = placement,
          modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(tooltipOffsetX, tooltipOffsetY) }
            .wrapContentSize(unbounded = true)
            .onSizeChanged {
              tooltipWidthPx = it.width
              tooltipHeightPx = it.height
            }
        )
      }
    }
    if (resolvedMarks.isNotEmpty()) {
      ZSliderMarks(
        marks = resolvedMarks,
        defaultLabelColor = defaultMarkColor,
        thumbSize = metrics.thumbSize,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(top = metrics.containerHeight)
          .fillMaxWidth()
      )
    }
  }
}

/**
 * 单值滑块版本。
 *
 * 适用场景：
 * - 一个连续值输入（音量、进度、阈值等）；
 * - 支持可选输入框（`showInput = true`）。
 *
 * 参数说明（重点）：
 * - `min`/`max`：最小值与最大值，优先于 `valueRange`；
 * - `showInput`：右侧显示输入框；
 * - `showInputControls`：输入框显示 `+/-` 按钮（仅 `showInput = true` 时生效）；
 * - `validateEvent`：输入过程是否实时提交值（默认 `true`）；
 * - `vertical`：垂直模式（必须为外层显式设置高度）；
 * - `marks`：标记点与标签（水平/垂直均支持）；
 * - `showTooltip`/`placement`：拖拽提示配置。
 */
@Composable
fun ZSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
  min: Float = valueRange.start,
  max: Float = valueRange.endInclusive,
  enabled: Boolean = true,
  activeColor: Color? = null,
  inactiveColor: Color? = null,
  step: Float = 0f,
  showStops: Boolean = false,
  showInput: Boolean = false,
  showInputControls: Boolean = true,
  validateEvent: Boolean = true,
  size: ZFormSize? = null,
  vertical: Boolean = false,
  marks: Map<Float, ZSliderMark> = emptyMap(),
  metrics: ZSliderMetrics = ZSliderDefaults.Metrics,
  showTooltip: Boolean = true,
  placement: ZSliderTooltipPlacement = ZSliderTooltipPlacement.TOP,
  formatTooltip: (Float) -> String = { it.roundToInt().toString() }
) {
  val minValue = kotlin.math.min(min, max)
  val maxValue = kotlin.math.max(min, max)
  val resolvedMarks = resolveSliderMarks(marks = marks, minValue = minValue, maxValue = maxValue)
  val rangeSize = (maxValue - minValue).coerceAtLeast(0f)
  val normalizedStep = if (step > 0f && step <= rangeSize) step else 0f
  val hasDiscreteStep = normalizedStep > 0f && rangeSize > 0f
  val stopFractions = if (hasDiscreteStep) {
    resolveSliderStopFractions(rangeSize = rangeSize, step = normalizedStep)
  } else {
    emptyList()
  }

  fun snapValue(rawValue: Float): Float {
    val clampedValue = rawValue.coerceIn(minValue, maxValue)
    if (!hasDiscreteStep) {
      return clampedValue
    }
    val stepIndex = ((clampedValue - minValue) / normalizedStep).roundToInt()
    return (minValue + stepIndex * normalizedStep).coerceIn(minValue, maxValue)
  }

  val coercedValue = snapValue(value)
  val valueFraction = if (rangeSize == 0f) {
    0f
  } else {
    (coercedValue - minValue) / rangeSize
  }.coerceIn(0f, 1f)

  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  val isDarkTheme = isAppInDarkTheme()
  val style = resolveZSliderStyle(
    enabled = enabled,
    isHovered = isHovered,
    isDarkTheme = isDarkTheme,
    activeColor = activeColor,
    inactiveColor = inactiveColor
  )
  val defaultMarkColor = if (isDarkTheme) Color(0xffa3a6ad) else Color(0xff909399)
  val marksAreaHeight = if (resolvedMarks.isEmpty()) 0.dp else 28.dp
  val totalContainerHeight = metrics.containerHeight + marksAreaHeight
  var sliderWidthPx by remember { mutableFloatStateOf(0f) }
  var tooltipWidthPx by remember { mutableIntStateOf(0) }
  var tooltipHeightPx by remember { mutableIntStateOf(0) }
  var isDragging by remember { mutableStateOf(false) }
  var dragFraction by remember { mutableFloatStateOf(valueFraction) }

  LaunchedEffect(valueFraction, isDragging) {
    if (!isDragging) {
      dragFraction = valueFraction
    }
  }

  if (vertical) {
    // 垂直模式先保持简洁交互：不与输入框组合使用。
    require(!showInput) {
      "ZSlider vertical mode does not support showInput."
    }
    val verticalMarksAreaWidth = rememberVerticalMarksAreaWidth(resolvedMarks)
    BoxWithConstraints(modifier = modifier) {
      // 垂直滑块必须有明确高度，否则无法计算可拖拽轨道长度。
      require(maxHeight != Dp.Infinity) {
        "ZSlider vertical mode requires an explicit height. Example: modifier = Modifier.height(200.dp)"
      }

      val density = LocalDensity.current
      val containerWidthPx = with(density) { metrics.containerHeight.toPx() }
      val thumbSizePx = with(density) { metrics.thumbSize.toPx() }
      val thumbRadiusPx = thumbSizePx / 2f
      val trackUsableHeightPx = (with(density) { maxHeight.toPx() } - thumbSizePx).coerceAtLeast(0f)
      // 拖拽中优先使用拖拽态，防止外部状态更新频率导致视觉抖动。
      val displayedFraction = if (isDragging) {
        if (hasDiscreteStep && rangeSize > 0f) {
          ((snapValue(minValue + dragFraction * rangeSize) - minValue) / rangeSize).coerceIn(0f, 1f)
        } else {
          dragFraction
        }
      } else {
        valueFraction
      }
      val displayedValue = minValue + displayedFraction * rangeSize
      val thumbCenterYPx = thumbRadiusPx + trackUsableHeightPx * (1f - displayedFraction)
      val thumbOffsetYPx = (thumbCenterYPx - thumbRadiusPx).roundToInt()
      val activeTrackHeightDp = with(density) { (trackUsableHeightPx * displayedFraction).toDp() }
      val sideTooltipGapPx = with(density) { 6.dp.toPx() }

      // 将点击/拖拽位置映射为值。垂直模式下顶部是最大值、底部是最小值。
      fun valueFromPosition(positionY: Float): Float {
        if (rangeSize == 0f || trackUsableHeightPx <= 0f) {
          return minValue
        }
        val normalized = ((trackUsableHeightPx + thumbRadiusPx - positionY) / trackUsableHeightPx).coerceIn(0f, 1f)
        return snapValue(minValue + normalized * rangeSize)
      }

      val draggableState = rememberDraggableState { delta ->
        if (!enabled || rangeSize == 0f || trackUsableHeightPx <= 0f) {
          return@rememberDraggableState
        }
        val nextFraction = (dragFraction - delta / trackUsableHeightPx).coerceIn(0f, 1f)
        val resolvedValue = snapValue(minValue + nextFraction * rangeSize)
        dragFraction = nextFraction
        onValueChange(resolvedValue)
      }

      val rootInteractiveModifier = if (enabled) {
        Modifier
          .hoverable(interactionSource = interactionSource)
          .pointerInput(rangeSize, minValue, trackUsableHeightPx, normalizedStep) {
            detectTapGestures { offset ->
              onValueChange(valueFromPosition(offset.y))
            }
          }
          .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStarted = {
              dragFraction = valueFraction
              isDragging = true
            },
            onDragStopped = {
              isDragging = false
            }
          )
      } else {
        Modifier
      }

      Box(
        modifier = Modifier
          .zIndex(if (isDragging) 1f else 0f)
          .width(metrics.containerHeight + verticalMarksAreaWidth)
          .fillMaxHeight()
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .width(metrics.containerHeight)
            .fillMaxHeight()
            .then(rootInteractiveModifier)
        ) {
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .width(metrics.touchHeight)
              .fillMaxHeight()
          ) {
            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .padding(vertical = metrics.thumbSize / 2)
                .width(metrics.trackHeight)
                .background(style.inactiveTrackColor, RoundedCornerShape(percent = 50))
            )
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = metrics.thumbSize / 2)
                .width(metrics.trackHeight)
                .height(activeTrackHeightDp)
                .background(style.activeTrackColor, RoundedCornerShape(percent = 50))
            )
            if (showStops && stopFractions.isNotEmpty()) {
              val stopRadiusPx = with(density) { (metrics.trackHeight / 2f).toPx() }
              val upcomingStopColor = Color.White
              Canvas(
                modifier = Modifier
                  .align(Alignment.Center)
                  .fillMaxHeight()
                  .width(metrics.touchHeight)
                  .padding(vertical = metrics.thumbSize / 2)
              ) {
                val centerX = this.size.width / 2f
                val coveredThreshold = displayedFraction + 0.0001f
                for (stopFraction in stopFractions) {
                  // 已被激活轨道覆盖的 stop 隐藏，仅保留未覆盖部分。
                  if (stopFraction <= coveredThreshold) continue
                  val stopY = this.size.height * (1f - stopFraction)
                  drawCircle(
                    color = upcomingStopColor,
                    radius = stopRadiusPx,
                    center = Offset(centerX, stopY)
                  )
                }
              }
            }
            if (resolvedMarks.isNotEmpty()) {
              val markRadiusPx = with(density) { (metrics.trackHeight / 2f).toPx() }
              Canvas(
                modifier = Modifier
                  .align(Alignment.Center)
                  .fillMaxHeight()
                  .width(metrics.touchHeight)
                  .padding(vertical = metrics.thumbSize / 2)
              ) {
                val centerX = this.size.width / 2f
                resolvedMarks.forEach { resolvedMark ->
                  val markerY = this.size.height * (1f - resolvedMark.fraction)
                  drawCircle(
                    color = Color.White,
                    radius = markRadiusPx,
                    center = Offset(centerX, markerY)
                  )
                }
              }
            }
            Box(
              modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(x = 0, y = thumbOffsetYPx) }
                .size(metrics.thumbSize)
                .background(style.thumbColor, CircleShape)
                .border(
                  width = metrics.thumbBorderWidth,
                  color = style.thumbBorderColor,
                  shape = CircleShape
                )
            )
          }

          // Tooltip 仅在拖拽过程中显示，避免静态状态遮挡内容。
          if (showTooltip && enabled && isDragging) {
            val tooltipSpacingPx = with(density) { 2.dp.roundToPx() }
            val bottomTooltipGapPx = with(density) { 8.dp.roundToPx() }
            val resolvedTooltipWidthPx = if (tooltipWidthPx == 0) {
              with(density) { 44.dp.roundToPx() }
            } else {
              tooltipWidthPx
            }
            val resolvedTooltipHeightPx = if (tooltipHeightPx == 0) {
              with(density) { 34.dp.roundToPx() }
            } else {
              tooltipHeightPx
            }
            val thumbTopPx = thumbCenterYPx - thumbRadiusPx
            val thumbBottomPx = thumbCenterYPx + thumbRadiusPx
            val thumbCenterXPx = containerWidthPx / 2f
            val tooltipOffsetX = when (placement) {
              ZSliderTooltipPlacement.TOP,
              ZSliderTooltipPlacement.BOTTOM -> (thumbCenterXPx - resolvedTooltipWidthPx / 2f).roundToInt()
              ZSliderTooltipPlacement.LEFT -> (
                thumbCenterXPx - thumbRadiusPx - resolvedTooltipWidthPx - sideTooltipGapPx
                ).roundToInt()
              ZSliderTooltipPlacement.RIGHT -> (
                thumbCenterXPx + thumbRadiusPx + sideTooltipGapPx
                ).roundToInt()
            }
            val tooltipOffsetY = when (placement) {
              ZSliderTooltipPlacement.TOP -> (thumbTopPx - resolvedTooltipHeightPx - tooltipSpacingPx).roundToInt()
              ZSliderTooltipPlacement.BOTTOM -> (thumbBottomPx + bottomTooltipGapPx).roundToInt()
              ZSliderTooltipPlacement.LEFT,
              ZSliderTooltipPlacement.RIGHT -> (thumbCenterYPx - resolvedTooltipHeightPx / 2f).roundToInt()
            }
            ZSliderTooltip(
              text = formatTooltip(displayedValue),
              backgroundColor = style.tooltipBackgroundColor,
              textColor = style.tooltipTextColor,
              placement = placement,
              modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(tooltipOffsetX, tooltipOffsetY) }
                .wrapContentSize(unbounded = true)
                .onSizeChanged {
                  tooltipWidthPx = it.width
                  tooltipHeightPx = it.height
                }
            )
          }
        }
        if (resolvedMarks.isNotEmpty()) {
          ZSliderVerticalMarks(
            marks = resolvedMarks,
            defaultLabelColor = defaultMarkColor,
            thumbSize = metrics.thumbSize,
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(start = metrics.containerHeight + 8.dp)
              .fillMaxHeight()
          )
        }
      }
    }
    return
  }

  val density = LocalDensity.current
  val containerHeightPx = with(density) { metrics.containerHeight.toPx() }
  val touchHeightPx = with(density) { metrics.touchHeight.toPx() }
  val thumbSizePx = with(density) { metrics.thumbSize.toPx() }
  val thumbRadiusPx = thumbSizePx / 2f
  val thumbTopPx = (containerHeightPx - touchHeightPx) / 2f + (touchHeightPx - thumbSizePx) / 2f
  val trackUsableWidthPx = (sliderWidthPx - thumbSizePx).coerceAtLeast(0f)
  val displayedFraction = if (isDragging) {
    if (hasDiscreteStep && rangeSize > 0f) {
      ((snapValue(minValue + dragFraction * rangeSize) - minValue) / rangeSize).coerceIn(0f, 1f)
    } else {
      dragFraction
    }
  } else {
    valueFraction
  }
  val displayedValue = minValue + displayedFraction * rangeSize
  val thumbCenterPx = thumbRadiusPx + trackUsableWidthPx * displayedFraction
  val thumbOffsetXPx = (thumbCenterPx - thumbRadiusPx).roundToInt()
  val activeTrackWidthDp = with(density) { (trackUsableWidthPx * displayedFraction).toDp() }
  val sideTooltipGapPx = with(density) { 6.dp.toPx() }

  fun valueFromPosition(positionX: Float): Float {
    if (rangeSize == 0f || trackUsableWidthPx <= 0f) {
      return minValue
    }
    val normalized = ((positionX - thumbRadiusPx) / trackUsableWidthPx).coerceIn(0f, 1f)
    return snapValue(minValue + normalized * rangeSize)
  }

  val draggableState = rememberDraggableState { delta ->
    if (!enabled || rangeSize == 0f || trackUsableWidthPx <= 0f) {
      return@rememberDraggableState
    }
    val nextFraction = (dragFraction + delta / trackUsableWidthPx).coerceIn(0f, 1f)
    val resolvedValue = snapValue(minValue + nextFraction * rangeSize)
    // Keep raw drag progression so small deltas can accumulate across events.
    dragFraction = nextFraction
    onValueChange(resolvedValue)
  }

  val rootInteractiveModifier = if (enabled) {
    Modifier
      .hoverable(interactionSource = interactionSource)
      .pointerInput(rangeSize, minValue, trackUsableWidthPx, normalizedStep) {
        detectTapGestures { offset ->
          onValueChange(valueFromPosition(offset.x))
        }
      }
      .draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        onDragStarted = {
          dragFraction = valueFraction
          isDragging = true
        },
        onDragStopped = {
          isDragging = false
        }
      )
  } else {
    Modifier
  }

  // 将轨道绘制封装为局部可组合函数，便于在 showInput 场景中复用。
  val sliderTrack: @Composable (Modifier) -> Unit = { trackModifier ->
    Box(
      modifier = Modifier
        .zIndex(if (isDragging) 1f else 0f)
        .fillMaxWidth()
        .height(totalContainerHeight)
        .then(trackModifier)
    ) {
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .fillMaxWidth()
          .height(metrics.containerHeight)
          .onSizeChanged {
            sliderWidthPx = it.width.toFloat()
          }
          .then(rootInteractiveModifier)
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxWidth()
            .height(metrics.touchHeight)
        ) {
          Box(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .fillMaxWidth()
              .padding(horizontal = metrics.thumbSize / 2)
              .height(metrics.trackHeight)
              .background(style.inactiveTrackColor, RoundedCornerShape(percent = 50))
          )
          Box(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .padding(start = metrics.thumbSize / 2)
              .height(metrics.trackHeight)
              .background(style.activeTrackColor, RoundedCornerShape(percent = 50))
              .width(activeTrackWidthDp)
          )
          if (showStops && stopFractions.isNotEmpty()) {
            val stopRadiusPx = with(density) { (metrics.trackHeight / 2f).toPx() }
            val upcomingStopColor = Color.White
            Canvas(
              modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = metrics.thumbSize / 2)
                .height(metrics.touchHeight)
            ) {
              val centerY = this.size.height / 2f
              val coveredThreshold = displayedFraction + 0.0001f
              for (stopFraction in stopFractions) {
                // 单值模式：已走过的 stop 隐藏，只显示前方 stop。
                if (stopFraction <= coveredThreshold) continue
                val stopX = this.size.width * stopFraction
                drawCircle(
                  color = upcomingStopColor,
                  radius = stopRadiusPx,
                  center = Offset(stopX, centerY)
                )
              }
            }
          }
          if (resolvedMarks.isNotEmpty()) {
            // marks 与 stop 分层绘制：marks 始终可见，用于强调业务刻度标签。
            val markRadiusPx = with(density) { (metrics.trackHeight / 2f).toPx() }
            Canvas(
              modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = metrics.thumbSize / 2)
                .height(metrics.touchHeight)
            ) {
              val centerY = this.size.height / 2f
              resolvedMarks.forEach { resolvedMark ->
                val markerX = this.size.width * resolvedMark.fraction
                drawCircle(
                  color = Color.White,
                  radius = markRadiusPx,
                  center = Offset(markerX, centerY)
                )
              }
            }
          }
          Box(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .offset { IntOffset(x = thumbOffsetXPx, y = 0) }
              .size(metrics.thumbSize)
              .background(style.thumbColor, CircleShape)
              .border(
                width = metrics.thumbBorderWidth,
                color = style.thumbBorderColor,
                shape = CircleShape
              )
          )
        }

        if (showTooltip && enabled && isDragging) {
          val tooltipSpacingPx = with(density) { 2.dp.roundToPx() }
          val bottomTooltipGapPx = with(density) { 8.dp.roundToPx() }
          val resolvedTooltipWidthPx = if (tooltipWidthPx == 0) {
            with(density) { 44.dp.roundToPx() }
          } else {
            tooltipWidthPx
          }
          val resolvedTooltipHeightPx = if (tooltipHeightPx == 0) {
            with(density) { 34.dp.roundToPx() }
          } else {
            tooltipHeightPx
          }
          val thumbCenterYPx = thumbTopPx + thumbRadiusPx
          val tooltipOffsetX = when (placement) {
            ZSliderTooltipPlacement.TOP,
            ZSliderTooltipPlacement.BOTTOM -> (thumbCenterPx - resolvedTooltipWidthPx / 2f).roundToInt()
            ZSliderTooltipPlacement.LEFT -> (
              thumbCenterPx - thumbRadiusPx - resolvedTooltipWidthPx - sideTooltipGapPx
              ).roundToInt()
            ZSliderTooltipPlacement.RIGHT -> (
              thumbCenterPx + thumbRadiusPx + sideTooltipGapPx
              ).roundToInt()
          }
          val tooltipOffsetY = when (placement) {
            ZSliderTooltipPlacement.TOP -> (thumbTopPx - resolvedTooltipHeightPx - tooltipSpacingPx).roundToInt()
            ZSliderTooltipPlacement.BOTTOM -> (thumbTopPx + thumbSizePx + bottomTooltipGapPx).roundToInt()
            ZSliderTooltipPlacement.LEFT,
            ZSliderTooltipPlacement.RIGHT -> (thumbCenterYPx - resolvedTooltipHeightPx / 2f).roundToInt()
          }
          ZSliderTooltip(
            text = formatTooltip(displayedValue),
            backgroundColor = style.tooltipBackgroundColor,
            textColor = style.tooltipTextColor,
            placement = placement,
            modifier = Modifier
              .align(Alignment.TopStart)
              .offset { IntOffset(tooltipOffsetX, tooltipOffsetY) }
              .wrapContentSize(unbounded = true)
              .onSizeChanged {
                tooltipWidthPx = it.width
                tooltipHeightPx = it.height
              }
          )
        }
      }
      if (resolvedMarks.isNotEmpty()) {
        // marks 文本放在轨道下方，容器高度已在 totalContainerHeight 中预留。
        ZSliderMarks(
          marks = resolvedMarks,
          defaultLabelColor = defaultMarkColor,
          thumbSize = metrics.thumbSize,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = metrics.containerHeight)
            .fillMaxWidth()
        )
      }
    }
  }

  if (showInput) {
    // 输入框模式：左侧轨道 + 右侧输入组件。
    Row(
      modifier = Modifier.fillMaxWidth().then(modifier),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      sliderTrack(Modifier.weight(1f))
      ZSliderInput(
        value = coercedValue,
        minValue = minValue,
        maxValue = maxValue,
        step = if (hasDiscreteStep) normalizedStep else 1f,
        size = size ?: LocalZFormSize.current ?: ZFormSize.DEFAULT,
        enabled = enabled,
        showControls = showInputControls,
        validateEvent = validateEvent,
        snapValue = { snapValue(it) },
        onValueChange = onValueChange,
        modifier = Modifier.width(130.dp)
      )
    }
  } else {
    sliderTrack(modifier)
  }
}

/**
 * 基于真实步长生成 stop 比例列表（不包含两端端点）。
 *
 * 例如：
 * - range=100, step=10 -> 0.1..0.9
 * - range=95, step=10 -> 10/95..90/95
 */
private fun resolveSliderStopFractions(
  rangeSize: Float,
  step: Float
): List<Float> {
  if (rangeSize <= 0f || step <= 0f) return emptyList()
  val rangeSizeDouble = rangeSize.toDouble()
  val stepDouble = step.toDouble()
  // 使用 double + 微小 epsilon，避免 1/0.1 这类浮点误差导致少一个断点。
  val intervalCount = floor(rangeSizeDouble / stepDouble + 1e-9).toInt()
  if (intervalCount <= 0) return emptyList()
  val fractions = ArrayList<Float>(intervalCount)
  for (stepIndex in 1..intervalCount) {
    val stopValue = stepDouble * stepIndex.toDouble()
    if (stopValue >= rangeSizeDouble - 1e-7) continue
    fractions.add((stopValue / rangeSizeDouble).toFloat().coerceIn(0f, 1f))
  }
  return fractions
}

/**
 * 将业务层 marks（按值定义）解析为可渲染结构（按 0~1 比例定义）。
 *
 * 处理策略：
 * - 空 marks：直接返回空；
 * - 零区间（min == max）：全部压到起点；
 * - 非零区间：过滤范围外 marks，并按键值升序输出。
 */
private fun resolveSliderMarks(
  marks: Map<Float, ZSliderMark>,
  minValue: Float,
  maxValue: Float
): List<ZSliderResolvedMark> {
  if (marks.isEmpty()) return emptyList()
  val inRangeMarks = marks.entries
    .filter { entry -> entry.key in minValue..maxValue }
    .sortedBy { it.key }
  val rangeSize = (maxValue - minValue).coerceAtLeast(0f)
  if (rangeSize == 0f) {
    return inRangeMarks.map { entry -> ZSliderResolvedMark(fraction = 0f, mark = entry.value) }
  }
  return inRangeMarks.map { entry ->
    ZSliderResolvedMark(
      fraction = ((entry.key - minValue) / rangeSize).coerceIn(0f, 1f),
      mark = entry.value
    )
  }
}

/**
 * 统一解析标记颜色：
 * - 若未指定颜色（`Color.Unspecified`），使用传入的回退色；
 * - 否则使用 mark 自定义颜色。
 */
private fun resolvedMarkColor(markColor: Color, fallbackColor: Color): Color {
  return if (markColor == Color.Unspecified) fallbackColor else markColor
}

/**
 * 绘制 marks 文本标签。
 *
 * 布局要点：
 * - 文本基于 `fraction` 居中定位；
 * - 在两端会自动做边界裁剪，避免标签超出组件可视区域。
 */
@Composable
private fun ZSliderMarks(
  marks: List<ZSliderResolvedMark>,
  defaultLabelColor: Color,
  thumbSize: Dp,
  modifier: Modifier = Modifier
) {
  if (marks.isEmpty()) return

  val density = LocalDensity.current
  BoxWithConstraints(
    modifier = modifier
      .height(28.dp)
      .padding(horizontal = thumbSize / 2)
  ) {
    val trackWidthPx = with(density) { maxWidth.toPx() }
    val labelTopOffsetPx = with(density) { 4.dp.roundToPx() }
    marks.forEach { resolvedMark ->
      key(resolvedMark.fraction, resolvedMark.mark.label) {
        var labelWidthPx by remember { mutableIntStateOf(0) }
        val centeredOffsetPx = trackWidthPx * resolvedMark.fraction - labelWidthPx / 2f
        // 将标签限制在轨道可视区间内，防止首尾标签被裁切。
        val clampedOffsetPx = centeredOffsetPx.coerceIn(
          minimumValue = 0f,
          maximumValue = (trackWidthPx - labelWidthPx).coerceAtLeast(0f)
        ).roundToInt()
        ZText(
          text = resolvedMark.mark.label,
          color = resolvedMarkColor(
            markColor = resolvedMark.mark.color,
            fallbackColor = defaultLabelColor
          ),
          fontSize = 12.sp,
          modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(x = clampedOffsetPx, y = labelTopOffsetPx) }
            .onSizeChanged { size ->
              labelWidthPx = size.width
            }
        )
      }
    }
  }
}

/**
 * 绘制垂直模式下的 marks 文本标签（默认在轨道右侧）。
 */
@Composable
private fun ZSliderVerticalMarks(
  marks: List<ZSliderResolvedMark>,
  defaultLabelColor: Color,
  thumbSize: Dp,
  modifier: Modifier = Modifier
) {
  if (marks.isEmpty()) return

  val density = LocalDensity.current
  BoxWithConstraints(modifier = modifier) {
    val containerHeightPx = with(density) { maxHeight.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val thumbRadiusPx = thumbSizePx / 2f
    val trackUsableHeightPx = (containerHeightPx - thumbSizePx).coerceAtLeast(0f)
    marks.forEach { resolvedMark ->
      key(resolvedMark.fraction, resolvedMark.mark.label) {
        var labelHeightPx by remember { mutableIntStateOf(0) }
        val labelCenterYPx = thumbRadiusPx + trackUsableHeightPx * (1f - resolvedMark.fraction)
        val centeredOffsetPx = labelCenterYPx - labelHeightPx / 2f
        val clampedOffsetPx = centeredOffsetPx.coerceIn(
          minimumValue = 0f,
          maximumValue = (containerHeightPx - labelHeightPx).coerceAtLeast(0f)
        ).roundToInt()
        ZText(
          text = resolvedMark.mark.label,
          color = resolvedMarkColor(
            markColor = resolvedMark.mark.color,
            fallbackColor = defaultLabelColor
          ),
          fontSize = 12.sp,
          modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(x = 0, y = clampedOffsetPx) }
            .onSizeChanged { size ->
              labelHeightPx = size.height
            }
        )
      }
    }
  }
}

@Composable
private fun rememberVerticalMarksAreaWidth(
  marks: List<ZSliderResolvedMark>
): Dp {
  if (marks.isEmpty()) return 0.dp
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val maxLabelWidthPx = marks.maxOfOrNull { resolvedMark ->
    textMeasurer.measure(
      text = AnnotatedString(resolvedMark.mark.label),
      style = TextStyle(fontSize = 12.sp)
    ).size.width
  } ?: 0
  // 预留少量右侧呼吸空间，避免文字贴边。
  return with(density) { maxLabelWidthPx.toDp() } + 8.dp
}

/**
 * Tooltip 内容与箭头组合。
 *
 * 该函数只负责外观；位置偏移由调用方根据 thumb 坐标计算。
 */
@Composable
private fun ZSliderTooltip(
  text: String,
  backgroundColor: Color,
  textColor: Color,
  placement: ZSliderTooltipPlacement,
  modifier: Modifier = Modifier
) {
  val bubble: @Composable () -> Unit = {
    Box(
      modifier = Modifier
        .background(backgroundColor, RoundedCornerShape(4.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
      ZText(
        text = text,
        color = textColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }

  when (placement) {
    ZSliderTooltipPlacement.TOP -> {
      Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        bubble()
        ZSliderTooltipArrow(
          direction = ZSliderTooltipArrowDirection.DOWN,
          color = backgroundColor
        )
      }
    }
    ZSliderTooltipPlacement.BOTTOM -> {
      Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        ZSliderTooltipArrow(
          direction = ZSliderTooltipArrowDirection.UP,
          color = backgroundColor
        )
        bubble()
      }
    }
    ZSliderTooltipPlacement.LEFT -> {
      Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
      ) {
        bubble()
        ZSliderTooltipArrow(
          direction = ZSliderTooltipArrowDirection.RIGHT,
          color = backgroundColor
        )
      }
    }
    ZSliderTooltipPlacement.RIGHT -> {
      Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
      ) {
        ZSliderTooltipArrow(
          direction = ZSliderTooltipArrowDirection.LEFT,
          color = backgroundColor
        )
        bubble()
      }
    }
  }
}

private enum class ZSliderTooltipArrowDirection {
  UP,
  DOWN,
  LEFT,
  RIGHT
}

/**
 * 绘制 Tooltip 三角箭头。
 */
@Composable
private fun ZSliderTooltipArrow(
  direction: ZSliderTooltipArrowDirection,
  color: Color
) {
  val sizeModifier = when (direction) {
    ZSliderTooltipArrowDirection.UP,
    ZSliderTooltipArrowDirection.DOWN -> Modifier.requiredSize(width = 10.dp, height = 5.dp)
    ZSliderTooltipArrowDirection.LEFT,
    ZSliderTooltipArrowDirection.RIGHT -> Modifier.requiredSize(width = 5.dp, height = 10.dp)
  }
  Canvas(modifier = sizeModifier) {
    val path = Path().apply {
      when (direction) {
        ZSliderTooltipArrowDirection.DOWN -> {
          moveTo(1.2f, 0f)
          lineTo(size.width / 2f, size.height)
          lineTo(size.width - 1.2f, 0f)
        }
        ZSliderTooltipArrowDirection.UP -> {
          moveTo(1.2f, size.height)
          lineTo(size.width / 2f, 0f)
          lineTo(size.width - 1.2f, size.height)
        }
        ZSliderTooltipArrowDirection.LEFT -> {
          moveTo(size.width, 1.2f)
          lineTo(0f, size.height / 2f)
          lineTo(size.width, size.height - 1.2f)
        }
        ZSliderTooltipArrowDirection.RIGHT -> {
          moveTo(0f, 1.2f)
          lineTo(size.width, size.height / 2f)
          lineTo(0f, size.height - 1.2f)
        }
      }
      close()
    }
    drawPath(path = path, color = color)
  }
}

/**
 * 滑块输入框（showInput 模式）：
 * - 支持 `+/-` 控制按钮显隐；
 * - 支持输入过程实时提交（`validateEvent = true`）；
 * - 失焦后统一做一次合法化提交（吸附步长 + 边界裁剪）。
 */
@Composable
private fun ZSliderInput(
  value: Float,
  minValue: Float,
  maxValue: Float,
  step: Float,
  size: ZFormSize,
  enabled: Boolean,
  showControls: Boolean,
  validateEvent: Boolean,
  snapValue: (Float) -> Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  var inputText by remember { mutableStateOf(formatSliderInputValue(value)) }
  var isFocused by remember { mutableStateOf(false) }
  LaunchedEffect(value, isFocused) {
    if (!isFocused) {
      inputText = formatSliderInputValue(value)
    }
  }

  val isDarkTheme = isAppInDarkTheme()
  val borderColor = when {
    enabled && isDarkTheme -> Color(0xff4c4d4f)
    enabled -> Color(0xffdcdfe6)
    isDarkTheme -> Color(0xff3d4045)
    else -> Color(0xffe4e7ed)
  }
  val inputBackgroundColor = if (isDarkTheme) Color(0xff1f1f1f) else Color.White
  val buttonBackgroundColor = if (isDarkTheme) Color(0xff2b2d30) else Color(0xfff5f7fa)
  val textColor = when {
    enabled && isDarkTheme -> Color(0xffe5eaf3)
    enabled -> Color(0xff606266)
    isDarkTheme -> Color(0xff7a7f86)
    else -> Color(0xffc0c4cc)
  }
  val inputMetrics = resolveSliderInputMetrics(size)

  val canDecrease = enabled && value > minValue + 0.0001f
  val canIncrease = enabled && value < maxValue - 0.0001f

  fun updateFromText(rawText: String) {
    // validateEvent=false 时，仅更新输入显示，不实时回写滑块值。
    if (!validateEvent) return
    val parsed = rawText.toFloatOrNull() ?: return
    onValueChange(snapValue(parsed).coerceIn(minValue, maxValue))
  }

  fun commitText(rawText: String) {
    // 失焦提交：无论 validateEvent 是否开启，最终都落到合法值。
    val parsed = rawText.toFloatOrNull()
    if (parsed == null) {
      inputText = formatSliderInputValue(value)
      return
    }
    val resolved = snapValue(parsed).coerceIn(minValue, maxValue)
    onValueChange(resolved)
    inputText = formatSliderInputValue(resolved)
  }

  Row(
    modifier = modifier
      .height(inputMetrics.height)
      .clip(RoundedCornerShape(4.dp))
      .background(inputBackgroundColor)
      .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (showControls) {
      ZSliderInputButton(
        symbol = "-",
        enabled = canDecrease,
        textColor = textColor,
        backgroundColor = buttonBackgroundColor,
        width = inputMetrics.buttonWidth,
        symbolFontSize = inputMetrics.symbolFontSize,
        onClick = {
          val nextValue = snapValue(value - step).coerceIn(minValue, maxValue)
          onValueChange(nextValue)
          inputText = formatSliderInputValue(nextValue)
        }
      )
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .width(1.dp)
          .background(borderColor)
      )
    }
    ZTextField(
      value = inputText,
      onValueChange = { newText ->
        val sanitized = sanitizeSliderInput(newText)
        inputText = sanitized
        updateFromText(sanitized)
      },
      enabled = enabled,
      readOnly = !enabled,
      size = size,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      textStyle = TextStyle(
        fontSize = inputMetrics.valueFontSize,
        textAlign = TextAlign.Center
      ),
      onFocusChanged = { focused ->
        isFocused = focused
        if (!focused) {
          commitText(inputText)
        }
      },
      modifier = if (showControls) {
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .background(inputBackgroundColor)
          .zMaskBorder(inputBackgroundColor)
      } else {
        Modifier
          .fillMaxSize()
          .background(inputBackgroundColor)
          .zMaskBorder(inputBackgroundColor)
      }
    )
    if (showControls) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .width(1.dp)
          .background(borderColor)
      )
      ZSliderInputButton(
        symbol = "+",
        enabled = canIncrease,
        textColor = textColor,
        backgroundColor = buttonBackgroundColor,
        width = inputMetrics.buttonWidth,
        symbolFontSize = inputMetrics.symbolFontSize,
        onClick = {
          val nextValue = snapValue(value + step).coerceIn(minValue, maxValue)
          onValueChange(nextValue)
          inputText = formatSliderInputValue(nextValue)
        }
      )
    }
  }
}

/**
 * 输入框两侧的步进按钮（`+` / `-`）。
 */
@Composable
private fun ZSliderInputButton(
  symbol: String,
  enabled: Boolean,
  textColor: Color,
  backgroundColor: Color,
  width: Dp,
  symbolFontSize: TextUnit,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxHeight()
      .width(width)
      .background(backgroundColor)
      .clickable(
        enabled = enabled,
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        onClick()
      },
    contentAlignment = Alignment.Center
  ) {
    ZText(
      text = symbol,
      color = if (enabled) textColor else textColor.copy(alpha = 0.45f),
      fontSize = symbolFontSize,
      fontWeight = FontWeight.Medium
    )
  }
}

private data class ZSliderInputMetrics(
  val height: Dp,
  val buttonWidth: Dp,
  val valueFontSize: TextUnit,
  val symbolFontSize: TextUnit
)

/**
 * 按表单尺寸映射输入框尺寸规格。
 */
private fun resolveSliderInputMetrics(size: ZFormSize): ZSliderInputMetrics {
  return when (size) {
    ZFormSize.LARGE -> ZSliderInputMetrics(
      height = 40.dp,
      buttonWidth = 40.dp,
      valueFontSize = 18.sp,
      symbolFontSize = 20.sp
    )
    ZFormSize.SMALL -> ZSliderInputMetrics(
      height = 24.dp,
      buttonWidth = 30.dp,
      valueFontSize = 14.sp,
      symbolFontSize = 16.sp
    )
    ZFormSize.DEFAULT -> ZSliderInputMetrics(
      height = 32.dp,
      buttonWidth = 36.dp,
      valueFontSize = 16.sp,
      symbolFontSize = 18.sp
    )
  }
}

/**
 * 遮罩输入框边缘 1dp，避免与外层边框叠加造成“加粗边线”观感。
 */
private fun Modifier.zMaskBorder(maskColor: Color): Modifier {
  return this.drawWithContent {
    drawContent()
    val edge = 1.dp.toPx()
    drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(size.width, edge))
    drawRect(color = maskColor, topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge))
    drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(edge, size.height))
    drawRect(color = maskColor, topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height))
  }
}

/**
 * 过滤输入字符：
 * - 允许数字；
 * - 仅允许首位 `-`；
 * - 仅允许一个 `.`。
 */
private fun sanitizeSliderInput(rawValue: String): String {
  val builder = StringBuilder()
  var hasDot = false
  rawValue.forEachIndexed { index, char ->
    when {
      char.isDigit() -> builder.append(char)
      char == '-' && index == 0 -> builder.append(char)
      char == '.' && !hasDot -> {
        builder.append(char)
        hasDot = true
      }
    }
  }
  return builder.toString()
}

/**
 * 统一格式化滑块输入显示：
 * - 整数按整数展示；
 * - 小数最多保留 3 位。
 */
private fun formatSliderInputValue(value: Float): String {
  val roundedInt = value.roundToInt().toFloat()
  if (abs(value - roundedInt) < 0.0001f) {
    return roundedInt.toInt().toString()
  }
  val rounded = (value * 1000f).roundToInt() / 1000f
  return rounded.toString()
}

@Immutable
private data class ZSliderStyle(
  val activeTrackColor: Color,
  val inactiveTrackColor: Color,
  val thumbColor: Color,
  val thumbBorderColor: Color,
  val tooltipBackgroundColor: Color,
  val tooltipTextColor: Color
)

/**
 * 滑块尺寸指标集合。
 *
 * 建议：
 * - `containerHeight`：组件整体高度；
 * - `touchHeight`：可交互热区高度；
 * - `trackHeight`：轨道厚度；
 * - `thumbSize`：滑块圆点尺寸；
 * - `thumbBorderWidth`：圆点边框宽度。
 */
@Immutable
data class ZSliderMetrics(
  val containerHeight: Dp,
  val touchHeight: Dp,
  val trackHeight: Dp,
  val thumbSize: Dp,
  val thumbBorderWidth: Dp
)

/**
 * 根据状态解析当前滑块配色：
 * - 默认/禁用；
 * - hover 态提亮；
 * - 暗黑/亮色主题差异。
 */
private fun resolveZSliderStyle(
  enabled: Boolean,
  isHovered: Boolean,
  isDarkTheme: Boolean,
  activeColor: Color?,
  inactiveColor: Color?
): ZSliderStyle {
  val resolvedActiveColor = activeColor ?: Color(0xff409eff)
  val resolvedInactiveColor = inactiveColor ?: if (isDarkTheme) Color(0xff4c4d4f) else Color(0xffdcdfe6)
  val hoveredActiveColor = if (isHovered && enabled) {
    lerp(resolvedActiveColor, Color.White, if (isDarkTheme) 0.10f else 0.16f)
  } else {
    resolvedActiveColor
  }
  val hoveredInactiveColor = if (isHovered && enabled) {
    lerp(resolvedInactiveColor, Color.White, if (isDarkTheme) 0.05f else 0.12f)
  } else {
    resolvedInactiveColor
  }
  return if (enabled) {
    ZSliderStyle(
      activeTrackColor = hoveredActiveColor,
      inactiveTrackColor = hoveredInactiveColor,
      thumbColor = if (isDarkTheme) Color(0xff1f1f1f) else Color.White,
      thumbBorderColor = hoveredActiveColor,
      tooltipBackgroundColor = Color(0xff303133),
      tooltipTextColor = Color.White
    )
  } else {
    val disabledBase = if (isDarkTheme) Color(0xff5a5f66) else Color(0xffc0c4cc)
    ZSliderStyle(
      activeTrackColor = disabledBase,
      inactiveTrackColor = if (isDarkTheme) Color(0xff3d4045) else Color(0xffe4e7ed),
      thumbColor = if (isDarkTheme) Color(0xff2a2d33) else Color(0xfff5f7fa),
      thumbBorderColor = disabledBase,
      tooltipBackgroundColor = Color(0xff303133),
      tooltipTextColor = Color.White
    )
  }
}

/**
 * 滑块默认尺寸配置。
 */
object ZSliderDefaults {
  val Metrics = ZSliderMetrics(
    containerHeight = 54.dp,
    touchHeight = 24.dp,
    trackHeight = 6.dp,
    thumbSize = 24.dp,
    thumbBorderWidth = 2.dp
  )
}
