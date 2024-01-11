package com.sd.lib.compose.nested

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import com.sd.lib.compose.gesture.fConsume
import com.sd.lib.compose.gesture.fPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun FNestedHeader(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val state by remember(coroutineScope) {
        mutableStateOf(NestedState(coroutineScope))
    }

    SubcomposeLayout(modifier = modifier) { cs ->
        val hasFixedWidth = cs.hasFixedWidth
        val hasFixedHeight = cs.hasFixedHeight
        @Suppress("NAME_SHADOWING")
        val cs = cs.copy(minWidth = 0, minHeight = 0)

        val headerPlaceable = (subcompose(SlotId.Header) { HeaderBox(state, header) }).let {
            check(it.size == 1)
            it.first().measure(cs.copy(maxHeight = Constraints.Infinity))
        }

        val contentPlaceable = (subcompose(SlotId.Content) { ContentBox(state, content) }).let {
            check(it.size == 1)
            it.first().measure(cs)
        }

        val width = if (hasFixedWidth) {
            cs.maxWidth
        } else {
            maxOf(headerPlaceable.width, contentPlaceable.width).coerceAtMost(cs.maxWidth)
        }

        val height = if (hasFixedHeight) {
            cs.maxHeight
        } else {
            maxOf(headerPlaceable.height, contentPlaceable.height).coerceAtMost(cs.maxHeight)
        }

        state.setSize(
            header = headerPlaceable.height,
            content = contentPlaceable.height,
            container = height,
        )

        layout(width, height) {
            val offset = state.offset.toInt()
            headerPlaceable.placeRelative(0, offset)
            contentPlaceable.placeRelative(0, headerPlaceable.height + offset)
        }
    }
}

private enum class SlotId {
    Header,
    Content,
}

@Composable
private fun HeaderBox(
    state: NestedState,
    header: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.nestedScroll(state.headerNestedScrollConnection)
    ) {
        Box(
            modifier = Modifier
                .nestedScroll(
                    connection = object : NestedScrollConnection {},
                    dispatcher = state.headerNestedScrollDispatcher,
                )
                .headerGesture(state),
        ) {
            header()
        }
    }
}

@Composable
private fun ContentBox(
    state: NestedState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.nestedScroll(state.contentNestedScrollConnection)
    ) {
        content()
    }
}

private fun Modifier.headerGesture(
    state: NestedState,
): Modifier {
    return if (state.isReady) {
        this.fPointer(
            onStart = {
                state.isHeaderDrag = false
                calculatePan = true
            },
            onCalculate = {
                if (currentEvent.changes.any { it.positionChanged() }) {
                    if (!state.isHeaderDrag) {
                        if (this.pan.x.absoluteValue >= this.pan.y.absoluteValue) {
                            cancelPointer()
                            return@fPointer
                        }
                    }

                    val y = this.pan.y
                    if (y == 0f) return@fPointer

                    state.isHeaderDrag = true
                    currentEvent.fConsume { it.positionChanged() }
                    state.headerNestedScrollDispatcher.dispatchScrollY(y, NestedScrollSource.Drag)
                }
            },
            onMove = {
                if (state.isHeaderDrag) {
                    velocityAdd(it)
                }
            },
            onUp = {
                if (state.isHeaderDrag && pointerCount == 1) {
                    val velocity = velocityGet(it.id)?.y ?: 0f
                    state.dispatchFling(velocity)
                }
            },
            onFinish = {
                state.isHeaderDrag = false
            }
        )
    } else {
        this
    }
}


private class NestedState(
    private val coroutineScope: CoroutineScope,
) {
    var isReady by mutableStateOf(false)
        private set

    var isHeaderDrag: Boolean = false

    var offset by mutableFloatStateOf(0f)

    private var _minOffset: Float = 0f
    private val _maxOffset: Float = 0f

    private val _anim = Animatable(0f)

    val headerNestedScrollDispatcher = NestedScrollDispatcher()
    val headerNestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val y = available.y
            return if (dispatchHide(y, source)) {
                available.copy(y = y)
            } else {
                super.onPreScroll(available, source)
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val y = available.y
            return if (dispatchShow(y, source)) {
                available.copy(y = y)
            } else {
                super.onPostScroll(consumed, available, source)
            }
        }
    }

    val contentNestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val y = available.y
            return if (dispatchHide(y, source)) {
                available.copy(y = y)
            } else {
                super.onPreScroll(available, source)
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val y = available.y
            return if (dispatchShow(y, source)) {
                available.copy(y = y)
            } else {
                super.onPostScroll(consumed, available, source)
            }
        }
    }

    fun setSize(header: Int, content: Int, container: Int) {
        isReady = header > 0
        _minOffset = if (content < container) {
            val bottom = header + content
            val delta = container - bottom
            delta.toFloat().coerceAtMost(0f)
        } else {
            -header.toFloat()
        }
    }

    fun dispatchHide(value: Float, source: NestedScrollSource): Boolean {
        if (!isReady) return false
        if (source == NestedScrollSource.Drag) cancelAnim()
        if (value < 0) {
            if (offset > _minOffset) {
                val newOffset = offset + value
                offset = newOffset.coerceAtLeast(_minOffset)
                return true
            }
        }
        return false
    }

    fun dispatchShow(value: Float, source: NestedScrollSource): Boolean {
        if (!isReady) return false
        if (source == NestedScrollSource.Drag) cancelAnim()
        if (value > 0) {
            if (offset < _maxOffset) {
                val newOffset = offset + value
                offset = newOffset.coerceAtMost(_maxOffset)
                return true
            }
        }
        return false
    }

    fun dispatchFling(velocity: Float) {
        coroutineScope.launch {
            val available = Velocity(0f, velocity)
            val consumed = headerNestedScrollDispatcher.dispatchPreFling(available).consumedCoerceIn(available)

            val left = available - consumed
            if (left != Velocity.Zero) {
                var lastValue = offset
                _anim.updateBounds(lowerBound = _minOffset, upperBound = _maxOffset)
                _anim.snapTo(offset)
                _anim.animateDecay(
                    initialVelocity = left.y,
                    animationSpec = exponentialDecay(frictionMultiplier = 2f),
                ) {
                    val delta = value - lastValue
                    lastValue = value
                    headerNestedScrollDispatcher.dispatchScrollY(delta, NestedScrollSource.Fling)
                }
            }

            headerNestedScrollDispatcher.dispatchPostFling(left, Velocity.Zero)
        }
    }

    private fun cancelAnim() {
        if (_anim.isRunning) {
            coroutineScope.launch {
                _anim.stop()
            }
        }
    }
}

private fun NestedScrollDispatcher.dispatchScrollY(y: Float, source: NestedScrollSource) {
    if (y == 0f) return

    val available = Offset(0f, y)

    val consumed = dispatchPreScroll(
        available = available,
        source = source,
    ).consumedCoerceIn(available)

    dispatchPostScroll(
        consumed = Offset.Zero,
        available = available - consumed,
        source = source,
    )
}

private fun Offset.consumedCoerceIn(available: Offset): Offset {
    val legalX = this.x.consumedCoerceIn(available.x)
    val legalY = this.y.consumedCoerceIn(available.y)
    return if (this.x == legalX && this.y == legalY) {
        this
    } else {
        this.copy(x = legalX, y = legalY)
    }
}

private fun Velocity.consumedCoerceIn(available: Velocity): Velocity {
    val legalX = this.x.consumedCoerceIn(available.x)
    val legalY = this.y.consumedCoerceIn(available.y)
    return if (this.x == legalX && this.y == legalY) {
        this
    } else {
        this.copy(x = legalX, y = legalY)
    }
}

private fun Float.consumedCoerceIn(available: Float): Float {
    return when {
        available > 0f -> this.coerceIn(0f, available)
        available < 0f -> this.coerceIn(available, 0f)
        else -> 0f
    }
}