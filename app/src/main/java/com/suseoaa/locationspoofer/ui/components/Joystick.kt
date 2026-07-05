package com.suseoaa.locationspoofer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * 摇杆方向输出。
 *
 * @property x 归一化横坐标分量（-1 ~ 1），向右为正
 * @property y 归一化纵坐标分量（-1 ~ 1），向下为正（与屏幕坐标系一致）
 * @property angle 弧度（-PI ~ PI），atan2(y, x)
 * @property intensity 强度（0 ~ 1），thumb 距 base 的距离 / (radius - thumbRadius)
 * @property active 是否处于拖拽状态
 */
data class JoystickValue(
    val x: Float = 0f,
    val y: Float = 0f,
    val angle: Float = 0f,
    val intensity: Float = 0f,
    val active: Boolean = false
)

/**
 * 摇杆可视化状态。
 *
 * 持有 base（外圈圆心）与 thumb（拇指圆心）两个位置，单位均为相对组件左上角的像素坐标。
 * 通过 [rememberJoystickState] 创建。
 */
@Stable
class JoystickState {
    /** 外圈圆心（相对组件左上角）。Offset.Zero 表示尚未初始化。 */
    var base: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** 拇指圆心（相对组件左上角）。 */
    var thumb: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** 是否处于拖拽中。 */
    var active: Boolean by mutableStateOf(false)
        internal set

    /** 是否处于"锁定保持"状态：拖拽松开后 thumb 不复位，输出持续有效。
     *  通过点按 thumb 切换；关闭时 thumb 回到 base。 */
    var isHolding: Boolean by mutableStateOf(false)
        internal set
}

@Composable
fun rememberJoystickState(): JoystickState = remember { JoystickState() }

/**
 * 自定义虚拟摇杆（轮盘）。
 *
 * 特性：
 * 1. 360° 任意方向输出，方向向量归一化（-1 ~ 1）
 * 2. 限制半径：thumb 外圈不超出 [radius]（即 thumb 圆心距 base 不超过 radius - thumbRadius）
 * 3. 固定基座：base 固定在组件中心，拖动过程中外圈不移动
 * 4. 平滑插值：thumb 位置使用 lerp 平滑过渡
 * 5. 锁定保持（可选）：点按 thumb 在 Lock / LockOpen 间切换。
 *    Lock 状态下拖拽松开后 thumb 保持在当前位置，输出持续有效；
 *    LockOpen 状态下拖拽松开后 thumb 回弹至 base，输出归零。
 *
 * @param radius 外圈半径
 * @param thumbRadius 拇指半径（同时为 Lock 图标尺寸的一半）
 * @param smoothFactor 平滑系数（0 ~ 1），值越小越平滑
 * @param holdEnabled 是否开启"锁定保持"功能
 * @param baseColor 外圈颜色
 * @param baseStrokeColor 外圈描边颜色
 * @param thumbColor 拇指（Lock 图标）颜色
 * @param thumbBackgroundColor 拇指背景圆颜色（图标背后的填充圆）
 * @param onValueChange 方向值变化回调（仅在拖拽中触发）
 */
@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    radius: Dp = 120.dp,
    thumbRadius: Dp = 40.dp,
    smoothFactor: Float = 0.6f,
    holdEnabled: Boolean = false,
    baseColor: Color = Color.Gray.copy(alpha = 0.25f),
    baseStrokeColor: Color = Color.Gray.copy(alpha = 0.6f),
    thumbColor: Color = Color.White.copy(alpha = 0.9f),
    thumbBackgroundColor: Color = thumbColor.copy(alpha = 0.25f),
    onValueChange: (JoystickValue) -> Unit = {}
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    // thumb 圆心距 base 的最大距离：使 thumb 外圈刚好贴在 radius 外圈内侧
    val maxThumbDistance = (radiusPx - thumbRadiusPx).coerceAtLeast(0f)

    val state = rememberJoystickState()

    // 用于平滑插值的内部 thumb 目标位置
    var thumbTarget by remember { mutableStateOf(Offset.Zero) }

    // holdEnabled 关闭时清除保持状态
    LaunchedEffect(holdEnabled) {
        if (!holdEnabled && state.isHolding) {
            state.isHolding = false
            state.thumb = state.base
            thumbTarget = state.base
            onValueChange(JoystickValue(active = false))
        }
    }

    Box(
        modifier = modifier
            .size(radius * 2 + thumbRadius)
            .onGloballyPositioned { coords ->
                // base 固定在组件中心，拖动过程中不再移动
                val center = Offset(coords.size.width / 2f, coords.size.height / 2f)
                state.base = center
                // 未拖拽且未保持时 thumb 跟随 base 回到中心
                if (!state.active && !state.isHolding) {
                    state.thumb = center
                    thumbTarget = center
                }
            }
            .pointerInput(radiusPx, smoothFactor, holdEnabled, thumbRadiusPx) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val downPos = down.position
                    // 是否点按在 thumb 上（用于判定切换保持状态）
                    val onThumb = (downPos - state.thumb).getDistance() <= thumbRadiusPx
                    var dragStarted = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null) {
                            // 指针消失（被父级抢占等）：按取消处理
                            if (dragStarted) {
                                state.active = false
                                thumbTarget = state.base
                                state.thumb = state.base
                                onValueChange(JoystickValue(active = false))
                            }
                            break
                        }

                        if (!change.pressed) {
                            // 手指抬起
                            change.consume()
                            if (!dragStarted) {
                                // 点按（未越过 touch slop）
                                if (holdEnabled && onThumb) {
                                    state.isHolding = !state.isHolding
                                    if (!state.isHolding) {
                                        // 解锁：thumb 回弹，输出归零
                                        thumbTarget = state.base
                                        state.thumb = state.base
                                        onValueChange(JoystickValue(active = false))
                                    }
                                    // 锁定时无需额外输出：等待下一次拖拽
                                }
                            } else {
                                // 拖拽结束
                                state.active = false
                                if (holdEnabled && state.isHolding) {
                                    // 保持模式：thumb 留在当前位置，输出由最后一次 onDrag 的值继续驱动
                                } else {
                                    // 回弹：thumb 回到 base，输出归零
                                    thumbTarget = state.base
                                    state.thumb = state.base
                                    onValueChange(JoystickValue(active = false))
                                }
                            }
                            break
                        }

                        // 检测是否越过 touch slop（判定拖拽开始）
                        if (!dragStarted) {
                            val totalMovement = (change.position - downPos).getDistance()
                            if (totalMovement > touchSlop) {
                                dragStarted = true
                                state.active = true
                                // base 已固定在组件中心，thumb 从 base 出发
                                state.thumb = state.base
                                thumbTarget = state.base
                                onValueChange(JoystickValue(active = true))
                            }
                        }

                        if (dragStarted) {
                            change.consume()

                            val finger = change.position

                            // === 1. 计算 thumb 目标位置（限幅 + 三点共线）===
                            // base 固定，thumb 永远在 base → finger 方向上；
                            // 限幅使 thumb 外圈不超出 radius（即圆心距 base ≤ radius - thumbRadius）
                            val deltaFromBase = finger - state.base
                            val distanceFromBase = deltaFromBase.getDistance()
                            val clampedDelta = if (distanceFromBase > maxThumbDistance) {
                                deltaFromBase * (maxThumbDistance / distanceFromBase)
                            } else {
                                deltaFromBase
                            }
                            thumbTarget = state.base + clampedDelta

                            // === 2. 平滑插值 ===
                            // lerp(thumb, thumbTarget, smoothFactor)
                            state.thumb = lerp(state.thumb, thumbTarget, smoothFactor)

                            // === 3. 输出归一化方向 ===
                            val thumbDelta = state.thumb - state.base
                            val thumbDistance = thumbDelta.getDistance()
                            val (outX, outY, outIntensity) = if (thumbDistance <= 0f) {
                                Triple(0f, 0f, 0f)
                            } else {
                                // 强度按 [0, maxThumbDistance] 映射到 [0, 1]
                                // （thumb 推到外圈贴边时强度为 1）
                                val effectiveRange = maxThumbDistance.coerceAtLeast(0.0001f)
                                val intensity = (thumbDistance / effectiveRange).coerceIn(0f, 1f)
                                val directionX = thumbDelta.x / thumbDistance
                                val directionY = thumbDelta.y / thumbDistance
                                Triple(directionX * intensity, directionY * intensity, intensity)
                            }

                            onValueChange(
                                JoystickValue(
                                    x = outX,
                                    y = outY,
                                    angle = atan2(thumbDelta.y, thumbDelta.x),
                                    intensity = outIntensity,
                                    active = true
                                )
                            )
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            // base 初始位置：若未拖拽则画在画布中心
            val baseCenter = if (state.base == Offset.Zero) canvasCenter else state.base

            // 外圈填充
            drawCircle(
                color = baseColor,
                radius = radiusPx,
                center = baseCenter
            )
            // 外圈描边
            drawCircle(
                color = baseStrokeColor,
                radius = radiusPx,
                center = baseCenter,
                style = Stroke(width = 2f)
            )
        }

        // thumb：holdEnabled 时为 Lock / LockOpen 图标 + 背景圆；否则为纯色圆
        val thumbPos = if (state.thumb == Offset.Zero) state.base else state.thumb
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (thumbPos.x - thumbRadiusPx).roundToInt(),
                        (thumbPos.y - thumbRadiusPx).roundToInt()
                    )
                }
                .size(thumbRadius * 2)
                .background(
                    color = if (holdEnabled) thumbBackgroundColor else thumbColor,
                    shape = CircleShape
                )
        ) {
            if (holdEnabled) {
                val thumbIcon = if (state.isHolding) Icons.Rounded.Lock else Icons.Rounded.LockOpen
                Icon(
                    imageVector = thumbIcon,
                    contentDescription = null,
                    tint = thumbColor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** 线性插值。t 范围 0~1，t=0 返回 start，t=1 返回 stop。 */
private fun lerp(start: Offset, stop: Offset, t: Float): Offset {
    val tt = t.coerceIn(0f, 1f)
    return Offset(
        start.x + (stop.x - start.x) * tt,
        start.y + (stop.y - start.y) * tt
    )
}
