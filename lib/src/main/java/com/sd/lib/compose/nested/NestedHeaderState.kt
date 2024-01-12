package com.sd.lib.compose.nested

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

internal class NestedHeaderState(
    private val coroutineScope: CoroutineScope,
) {
    var isReady by mutableStateOf(false)
        private set

    var offset by mutableFloatStateOf(0f)

    /** Header是否被触摸 */
    var isTouchHeader: Boolean = false

    var debug: Boolean = false

    private var _minOffset: Float = 0f
    private val _maxOffset: Float = 0f

    private val _animFling = Animatable(0f)

    val headerNestedScrollDispatcher = NestedScrollDispatcher()

    val contentNestedScrollConnection: NestedScrollConnection = NestedScrollConnectionY(
        onPreScroll = { value, _ ->
            if (isTouchHeader) {
                false
            } else {
                dispatchHide(value)
            }
        },
        onPostScroll = { value, _ ->
            if (isTouchHeader) {
                false
            } else {
                dispatchShow(value)
            }
        }
    )

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

    fun dispatchHide(value: Float): Boolean {
        if (!isReady) return false
        if (value < 0) {
            if (offset > _minOffset) {
                val newOffset = offset + value
                offset = newOffset.coerceAtLeast(_minOffset)
                return true
            }
        }
        return false
    }

    fun dispatchShow(value: Float): Boolean {
        if (!isReady) return false
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
            val uuid = if (debug) UUID.randomUUID().toString() else ""
            logMsg(debug) { "fling start velocity:${velocity} $uuid" }

            val available = Velocity(0f, velocity)
            val preConsumed = headerNestedScrollDispatcher.dispatchPreFling(available).consumedCoerceIn(available)

            val left = available - preConsumed
            logMsg(debug) { "fling preConsumed:${preConsumed.y} left:${left.y} $uuid" }

            if (left != Velocity.Zero) {
                _animFling.updateBounds(lowerBound = _minOffset, upperBound = _maxOffset)
                _animFling.snapTo(offset)

                var lastValue = _animFling.value
                _animFling.animateDecay(
                    initialVelocity = left.y,
                    animationSpec = exponentialDecay(frictionMultiplier = 1.5f),
                ) {
                    val delta = value - lastValue
                    lastValue = value
                    headerNestedScrollDispatcher.dispatchScrollY(delta, NestedScrollSource.Fling)
                }
            }

            val postConsumed = headerNestedScrollDispatcher.dispatchPostFling(left, Velocity.Zero)
            logMsg(debug) { "fling end postConsumed:${postConsumed.y} $uuid" }
        }
    }

    fun cancelFling(): Boolean {
        return _animFling.isRunning.also { isRunning ->
            if (isRunning) {
                coroutineScope.launch {
                    logMsg(debug) { "fling cancel" }
                    _animFling.stop()
                }
            }
        }
    }
}

private class NestedScrollConnectionY(
    val onPreScroll: (Float, NestedScrollSource) -> Boolean,
    val onPostScroll: (Float, NestedScrollSource) -> Boolean,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val y = available.y
        return if (onPreScroll(y, source)) {
            available.copy(y = y)
        } else {
            super.onPreScroll(available, source)
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val y = available.y
        return if (onPostScroll(y, source)) {
            available.copy(y = y)
        } else {
            super.onPostScroll(consumed, available, source)
        }
    }
}

internal fun NestedScrollDispatcher.dispatchScrollY(
    value: Float,
    source: NestedScrollSource,
) {
    val available = Offset(0f, value)

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

internal fun Offset.consumedCoerceIn(available: Offset): Offset {
    val legalX = this.x.consumedCoerceIn(available.x)
    val legalY = this.y.consumedCoerceIn(available.y)
    return if (this.x == legalX && this.y == legalY) {
        this
    } else {
        this.copy(x = legalX, y = legalY)
    }
}

internal fun Velocity.consumedCoerceIn(available: Velocity): Velocity {
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