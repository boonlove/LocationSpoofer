package com.suseoaa.locationspoofer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * BottomSheet 三态。
 *
 * offset(translationY) 约定：
 *   EXPANDED  -> 0           （Sheet 顶部最高，可见高度 = 屏幕一半）
 *   HALF      -> expanded - half
 *   COLLAPSED -> expanded - collapsed  （最大值，仅手柄 + collapsedContent 可见）
 */
enum class BottomSheetValue { COLLAPSED, HALF, EXPANDED }

@Stable
class BottomSheetState internal constructor(
    internal val anchoredDraggableState: AnchoredDraggableState<BottomSheetValue>
) {
    val currentValue: BottomSheetValue get() = anchoredDraggableState.currentValue
    val targetValue: BottomSheetValue get() = anchoredDraggableState.targetValue
    val isExpanded: Boolean get() = currentValue == BottomSheetValue.EXPANDED
    val isCollapsed: Boolean get() = currentValue == BottomSheetValue.COLLAPSED

    /** 当前 offset；anchors 未就位时为 NaN。 */
    val offset: Float get() = anchoredDraggableState.offset

    fun requireOffset(): Float = anchoredDraggableState.requireOffset()

    /** 锚点位置缓存，供 animateTo 使用。 */
    internal var anchorOffsets: Map<BottomSheetValue, Float> = emptyMap()

    internal fun updateAnchors(anchors: DraggableAnchors<BottomSheetValue>) {
        anchoredDraggableState.updateAnchors(anchors)
    }

    internal fun updateAnchorOffsets(offsets: Map<BottomSheetValue, Float>) {
        anchorOffsets = offsets
    }

    /** 供 NestedScrollConnection 调用：同步消费 delta，返回实际消费量。 */
    internal fun dispatchRawDelta(delta: Float): Float =
        anchoredDraggableState.dispatchRawDelta(delta)

    /**
     * 按运动方向吸附到锚点。
     * 快速 fling（|velocity| > velocityThreshold）取运动方向上的下一个锚点；
     * 否则取最近锚点。统一用 animateTo + 承接手势动量的 spring 实现，避免 decay 动画在中速时缓慢移动。
     */
    internal suspend fun snapToAnchor(velocity: Float, velocityThreshold: Float) {
        val o = requireOffset()
        if (o.isNaN()) return
        val target = computeFlingTarget(velocity, velocityThreshold) ?: findNearestAnchor(o)
        animateTo(target, velocity)
    }

    /**
     * 快速 fling 时计算下一个目标锚点。
     * - 下拉：找比当前 offset 大的最近锚点（HALF 或 COLLAPSED）
     * - 上推：找比当前 offset 小的最近锚点（HALF 或 EXPANDED）
     * 当前 offset 已越过 HALF 时跳过 HALF。
     */
    internal fun computeFlingTarget(velocity: Float, velocityThreshold: Float): BottomSheetValue? {
        if (anchorOffsets.isEmpty()) return null
        val o = requireOffset()
        if (o.isNaN()) return null
        return when {
            velocity > velocityThreshold -> {
                // 下拉：找比当前 offset 大的最近锚点
                anchorOffsets.entries
                    .filter { it.value > o + 0.5f }
                    .minByOrNull { it.value }
                    ?.key
            }
            velocity < -velocityThreshold -> {
                // 上推：找比当前 offset 小的最近锚点
                anchorOffsets.entries
                    .filter { it.value < o - 0.5f }
                    .maxByOrNull { it.value }
                    ?.key
            }
            else -> null
        }
    }

    /**
     * 找离当前 offset 最近的锚点（用于慢速拖拽 snap）。
     */
    internal fun findNearestAnchor(currentOffset: Float): BottomSheetValue {
        if (anchorOffsets.isEmpty()) return currentValue
        return anchorOffsets.entries
            .minByOrNull { abs(it.value - currentOffset) }
            ?.key ?: currentValue
    }

    /**
     * 直接动画到指定锚点（用于快速 fling 跨越多个锚点）。
     * 用 Animatable + dispatchRawDelta 实现，不依赖 anchoredDrag API。
     *
     * @param initialVelocity 手势速度（px/s），传递给 spring 使动画承接手势动量，
     *   实现"动量连续"——快速 fling 时动画起步快，慢速拖拽时柔和到位。
     *   NoBouncy + StiffnessMedium 保证平滑无回弹、~80-130ms 收敛，既不生硬也不拖沓。
     */
    internal suspend fun animateTo(target: BottomSheetValue, initialVelocity: Float = 0f) {
        val targetOffset = anchorOffsets[target] ?: return
        val startOffset = requireOffset()
        if (startOffset.isNaN() || startOffset == targetOffset) return
        val animatable = Animatable(startOffset)
        animatable.animateTo(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialVelocity = initialVelocity
        ) {
            val current = requireOffset()
            val delta = value - current
            if (delta != 0f) dispatchRawDelta(delta)
        }
    }
}

@Composable
fun rememberBottomSheetState(
    initialValue: BottomSheetValue,
    anchors: DraggableAnchors<BottomSheetValue>
): BottomSheetState {
    val density = LocalDensity.current
    @Suppress("DEPRECATION")
    val state = remember(density, anchors) {
        AnchoredDraggableState(
            initialValue = initialValue,
            anchors = anchors,
            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            decayAnimationSpec = exponentialDecay<Float>()
        )
    }
    return remember(state) { BottomSheetState(state) }
}

/**
 * 自定义可拖拽 BottomSheet。
 *
 * @param collapsedContent 始终可见的内容（COLLAPSED 时唯一可见），一般放 CoordinateInputCard。
 * @param content          其余可滚动内容，内部应自行使用 verticalScroll。
 * @param scrollState      可滚动区的滚动状态，由外部传入以便提升和持久化。
 */
@Composable
fun DraggableBottomSheet(
    sheetState: BottomSheetState,
    modifier: Modifier = Modifier,
    sheetShape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    scrimColor: Color = MaterialTheme.colorScheme.background,
    dragHandle: @Composable (() -> Unit)? = { DefaultDragHandle() },
    expandedFraction: Float = 0.50f,
    halfFraction: Float = 0.375f,
    scrollState: ScrollState = rememberScrollState(),
    collapsedContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val expandedHeightPx = screenHeightPx * expandedFraction
        val halfHeightPx = screenHeightPx * halfFraction

        // 测量 collapsedContent 的高度 + 顶部 padding，作为 COLLAPSED 锚点
        // coerceIn 防止 collapsedContent 过高导致锚点乱序（COLLAPSED ≤ HALF）
        var collapsedContentHeightPx by remember { mutableFloatStateOf(0f) }
        val topPaddingPx = with(density) { 20.dp.toPx() }
        val collapsedHeightPx = (collapsedContentHeightPx + topPaddingPx).coerceIn(0f, halfHeightPx - 1f)

        val anchors = remember(expandedHeightPx, halfHeightPx, collapsedHeightPx) {
            DraggableAnchors {
                BottomSheetValue.EXPANDED at 0f
                BottomSheetValue.HALF at (expandedHeightPx - halfHeightPx)
                BottomSheetValue.COLLAPSED at (expandedHeightPx - collapsedHeightPx)
            }
        }
        LaunchedEffect(anchors) {
            // 单参 updateAnchors 保持当前 offset，不 snap；COLLAPSED 锚点静默加入
            sheetState.updateAnchors(anchors)
            // 缓存锚点位置供 animateTo 使用
            sheetState.updateAnchorOffsets(
                mapOf(
                    BottomSheetValue.EXPANDED to 0f,
                    BottomSheetValue.HALF to (expandedHeightPx - halfHeightPx),
                    BottomSheetValue.COLLAPSED to (expandedHeightPx - collapsedHeightPx)
                )
            )
        }

        val velocityThresholdPx = with(density) { 125.dp.toPx() }
        val connection = remember(sheetState, velocityThresholdPx) {
            sheetState.preUpPostDownNestedScrollConnection(velocityThresholdPx)
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { expandedHeightPx.toDp() })
                .offset {
                    val o = sheetState.offset
                    IntOffset(0, if (o.isNaN()) 0 else o.roundToInt())
                }
                .clip(sheetShape)
                .background(scrimColor)
                .nestedScroll(connection)
                .anchoredDraggable(
                    state = sheetState.anchoredDraggableState,
                    orientation = Orientation.Vertical
                )
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // 可滚动区：collapsedContent + content 共享同一个 verticalScroll
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 20.dp)) {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                    ) {
                        // collapsedContent 作为可滚动区的第一个子元素，跟随 content 一起滚动
                        Column(
                            Modifier.onGloballyPositioned { coords ->
                                val h = coords.size.height.toFloat()
                                if (h > 0f && h != collapsedContentHeightPx) collapsedContentHeightPx = h
                            }
                        ) {
                            collapsedContent()
                        }
                        // content 区
                        content()
                    }
                    // COLLAPSED 时确保 collapsedContent 可见（滚动到顶部）
                    LaunchedEffect(sheetState.currentValue) {
                        if (sheetState.currentValue == BottomSheetValue.COLLAPSED) {
                            scrollState.animateScrollTo(0)
                        }
                    }
                }
            }
        }

        // dragHandle 浮在 Sheet 之上（z-order 顶层），fillMaxWidth 扩大触摸区
        if (dragHandle != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .offset {
                        val o = sheetState.offset
                        val offsetY = if (o.isNaN()) 0f else o
                        // 定位到 sheet 可见区域顶部
                        IntOffset(0, -(expandedHeightPx - offsetY).roundToInt())
                    }
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    val velocity = tracker.calculateVelocity().y
                                    // 按运动方向吸附锚点（快速 fling 跨越，慢速回最近锚点）
                                    sheetState.snapToAnchor(velocity, velocityThresholdPx)
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    // 取消视为零速度，吸附到最近锚点
                                    sheetState.snapToAnchor(0f, velocityThresholdPx)
                                }
                            }
                        ) { change, dragAmount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            sheetState.dispatchRawDelta(dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                dragHandle()
            }
        }
    }
}

/**
 * 上推优先展开、下拉到顶后收起的嵌套滚动连接。
 *
 * 符号约定：available.y > 0 = 手指下拽（收起）；available.y < 0 = 手指上推（展开）。
 * offset 越大越收起，dispatchRawDelta(delta) 把 delta 加到 offset，方向与 available.y 同号，无需取反。
 */
private fun BottomSheetState.preUpPostDownNestedScrollConnection(
    velocityThreshold: Float
): NestedScrollConnection =
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            val o = offset
            if (o.isNaN()) return Offset.Zero
            // COLLAPSED 态：阻断向下滚动，防止 collapsedContent 滚出视野
            if (currentValue == BottomSheetValue.COLLAPSED && delta > 0f) {
                return Offset(0f, delta)
            }
            // 上推且未到 EXPANDED：先展开 Sheet，再交内容滚动
            if (delta < 0f && o > 0f) {
                val consumed = dispatchRawDelta(delta)
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset, available: Offset, source: NestedScrollSource
        ): Offset {
            val delta = available.y
            if (delta > 0f) {
                // EXPANDED 态 + fling：不消费剩余下拽量，sheet 保持不动
                // （慢速 Drag 仍正常收起）
                if (currentValue == BottomSheetValue.EXPANDED && source == NestedScrollSource.Fling) {
                    return Offset.Zero
                }
                // 内容已到顶、剩余下拽量：收起 Sheet
                val consumedBySheet = dispatchRawDelta(delta)
                return Offset(0f, consumedBySheet)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val v = available.y
            val o = offset
            if (o.isNaN()) return Velocity.Zero
            // EXPANDED 态 + 下滑：先让内容消费 fling，不收起 sheet
            if (currentValue == BottomSheetValue.EXPANDED && v > velocityThreshold) {
                return Velocity.Zero
            }
            // 快速 fling 跨越锚点，或 sheet 已偏移需吸附
            if (computeFlingTarget(v, velocityThreshold) != null || o > 0f) {
                snapToAnchor(v, velocityThreshold)
                return Velocity(0f, v)
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            val v = available.y
            val o = offset
            if (o.isNaN()) return Velocity.Zero
            // EXPANDED 态：fling 余量丢弃；若 sheet 在 Drag 阶段被移动了则吸附锚点
            if (currentValue == BottomSheetValue.EXPANDED) {
                if (o > 0f) snapToAnchor(v, velocityThreshold)
                return Velocity(0f, v)
            }
            // 非 EXPANDED：offset 已跨越阈值（committed）时按运动方向吸附
            if (o > 0f && currentValue != targetValue) {
                snapToAnchor(v, velocityThreshold)
                return Velocity(0f, v)
            }
            return Velocity.Zero
        }
    }

@Composable
private fun DefaultDragHandle() {
    Box(
        Modifier
            .padding(top = 8.dp, bottom = 8.dp)
            .width(48.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
    )
}
