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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.math.absoluteValue

internal class NestedHeaderState(
    private val coroutineScope: CoroutineScope,
) {
    var debug: Boolean = false

    var isReady by mutableStateOf(false)
        private set

    var offset by mutableFloatStateOf(0f)

    private var _minOffset: Float = 0f
    private val _maxOffset: Float = 0f

    private val _animFling = Animatable(0f)
    private var _flingJob: Job? = null

    val headerNestedScrollDispatcher = NestedScrollDispatcher()

    private var _contentFlingContext: CoroutineContext? = null
    val contentNestedScrollConnection: NestedScrollConnection = NestedScrollConnectionY(
        onPreScroll = { value, _ ->
            dispatchHide(value)
        },
        onPostScroll = { value, _ ->
            dispatchShow(value)
        },
        onPreFling = {
            _contentFlingContext = currentCoroutineContext()
            false
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
        if (!isReady) return
        if (velocity.absoluteValue < 100) return
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

                    headerNestedScrollDispatcher.dispatchScroll(
                        available = Offset(0f, delta),
                        source = NestedScrollSource.Fling,
                    ) { left ->
                        val leftValue = left.y
                        when {
                            leftValue < 0 -> dispatchHide(leftValue)
                            leftValue > 0 -> dispatchShow(leftValue)
                            else -> false
                        }
                    }
                }
            }

            val postConsumed = headerNestedScrollDispatcher.dispatchPostFling(left, Velocity.Zero)
            logMsg(debug) { "fling end postConsumed:${postConsumed.y} $uuid" }
        }.also {
            _flingJob = it
        }
    }

    fun cancelFling(): Boolean {
        val fling = _flingJob ?: return false
        return fling.isActive.also { isActive ->
            if (isActive) {
                logMsg(debug) { "fling cancel" }
                fling.cancel()
            }
            _flingJob = null
        }
    }

    fun cancelContentFling(): Boolean {
        val fling = _contentFlingContext ?: return false
        return fling.isActive.also { isActive ->
            if (isActive) {
                logMsg(debug) { "content fling cancel" }
                fling.cancel()
            }
            _contentFlingContext = null
        }
    }
}

private class NestedScrollConnectionY(
    val onPreScroll: (Float, NestedScrollSource) -> Boolean,
    val onPostScroll: (Float, NestedScrollSource) -> Boolean,
    val onPreFling: suspend (Float) -> Boolean,
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

    override suspend fun onPreFling(available: Velocity): Velocity {
        val y = available.y
        return if (onPreFling(y)) {
            available.copy(y = y)
        } else {
            super.onPreFling(available)
        }
    }
}

internal fun NestedScrollDispatcher.dispatchScroll(
    available: Offset,
    source: NestedScrollSource,
    onScroll: (Offset) -> Boolean,
) {
    val preConsumed = dispatchPreScroll(
        available = available,
        source = source,
    ).consumedCoerceIn(available)

    val left = available - preConsumed
    val isConsumed = if (left != Offset.Zero) {
        onScroll(left)
    } else {
        false
    }

    dispatchPostScroll(
        consumed = if (isConsumed) left else Offset.Zero,
        available = if (isConsumed) Offset.Zero else left,
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