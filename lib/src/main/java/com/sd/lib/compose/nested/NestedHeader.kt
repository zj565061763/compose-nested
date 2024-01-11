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

    SubcomposeLayout(
        modifier = modifier.nestedScroll(state.nestedScrollConnection),
    ) { cs ->
        val hasFixedWidth = cs.hasFixedWidth
        val hasFixedHeight = cs.hasFixedHeight
        @Suppress("NAME_SHADOWING")
        val cs = cs.copy(minWidth = 0, minHeight = 0)

        val headerPlaceable = (subcompose(SlotId.Header) { HeaderBox(state, header) }).let {
            check(it.size == 1)
            it.first().measure(cs.copy(maxHeight = Constraints.Infinity))
        }.also {
            state.setHeaderSize(it.height)
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

        layout(width, height) {
            val offset = state.offset.toInt()
            headerPlaceable.placeRelative(0, offset)
            contentPlaceable.placeRelative(0, headerPlaceable.height + offset)
        }
    }
}

@Composable
private fun HeaderBox(
    state: NestedState,
    header: @Composable () -> Unit,
) {
    var isDrag by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .nestedScroll(
                connection = object : NestedScrollConnection {},
                dispatcher = state.headerNestedScrollDispatcher,
            )
            .let { m ->
                if (state.isReady) {
                    m.fPointer(
                        onStart = {
                            isDrag = false
                            calculatePan = true
                        },
                        onCalculate = {
                            if (currentEvent.changes.any { it.positionChanged() }) {
                                if (!isDrag) {
                                    if (this.pan.x.absoluteValue >= this.pan.y.absoluteValue) {
                                        cancelPointer()
                                        return@fPointer
                                    }
                                }

                                val y = this.pan.y
                                if (y == 0f) return@fPointer

                                isDrag = true
                                currentEvent.fConsume { it.positionChanged() }
                                state.headerNestedScrollDispatcher.dispatchScrollY(y, NestedScrollSource.Drag)
                            }
                        },
                        onMove = {
                            if (isDrag) {
                                velocityAdd(it)
                            }
                        },
                        onUp = {
                            if (isDrag && pointerCount == 1) {
                                val velocity = velocityGet(it.id)?.y ?: 0f
                                state.dispatchFling(velocity)
                            }
                        },
                        onFinish = {
                            isDrag = false
                        }
                    )
                } else {
                    m
                }
            },

        ) {
        header()
    }
}

@Composable
private fun ContentBox(
    state: NestedState,
    content: @Composable () -> Unit,
) {
    Box {
        content()
    }
}

private enum class SlotId {
    Header,
    Content,
}

private class NestedState(
    private val coroutineScope: CoroutineScope,
) {
    var offset by mutableFloatStateOf(0f)

    var isReady by mutableStateOf(false)
        private set

    private var _headerSize: Float = 0f
        set(value) {
            field = value
            isReady = value > 0f
            _anim.updateBounds(lowerBound = _minOffset, upperBound = _maxOffset)
        }

    private val _minOffset: Float get() = -_headerSize
    private val _maxOffset: Float = 0f

    private val _anim = Animatable(0f)

    val nestedScrollConnection = object : NestedScrollConnection {
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

    val headerNestedScrollDispatcher = NestedScrollDispatcher()

    fun setHeaderSize(size: Int) {
        _headerSize = size.toFloat()
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

            headerNestedScrollDispatcher.dispatchPostFling(consumed, left)
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
        consumed = consumed,
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