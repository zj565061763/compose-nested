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

internal class NestedHeaderState(
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

internal fun NestedScrollDispatcher.dispatchScrollY(y: Float, source: NestedScrollSource) {
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