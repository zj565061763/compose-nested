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
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
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
        modifier = modifier.nestedScroll(state.nestedScrollConnection)
    ) { cs ->
        val hasFixedWidth = cs.hasFixedWidth
        val hasFixedHeight = cs.hasFixedHeight
        @Suppress("NAME_SHADOWING")
        val cs = cs.copy(minWidth = 0, minHeight = 0)

        val offset = state.offset.toInt()

        val headerPlaceable = (subcompose(SlotId.Header) { HeaderBox(state, header) }).let {
            check(it.size == 1)
            it.first().measure(cs.copy(maxHeight = Constraints.Infinity))
        }.also {
            state.headerSize = it.height.toFloat()
        }

        val headerDisplaySize = headerPlaceable.height + offset
        val leftHeight = (cs.maxHeight - headerDisplaySize).coerceAtLeast(0)

        val contentPlaceable = (subcompose(SlotId.Content) { content() }).let {
            check(it.size == 1)
            it.first().measure(cs.copy(maxHeight = leftHeight))
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
        modifier = Modifier.fPointer(
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

                    val centroidY = this.centroid.y
                    if (centroidY >= 0 && centroidY < this.size.height) {
                        isDrag = true
                        val y = this.pan.y
                        when {
                            y > 0 -> state.dispatchShow(y)
                            y < 0 -> state.dispatchHide(y)
                        }
                        currentEvent.fConsume { it.positionChanged() }
                    }
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
        )
    ) {
        header()
    }
}

private enum class SlotId {
    Header,
    Content,
}

private class NestedState(
    private val coroutineScope: CoroutineScope,
) {
    var headerSize: Float = 0f

    val maxOffset: Float = 0f
    val minOffset: Float get() = -headerSize

    var offset by mutableFloatStateOf(0f)

    private val _anim = Animatable(0f)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val y = available.y
            return if (dispatchHide(y)) {
                available.copy(y = y)
            } else {
                super.onPreScroll(available, source)
            }
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val y = available.y
            return if (dispatchShow(y)) {
                available.copy(y = y)
            } else {
                super.onPostScroll(consumed, available, source)
            }
        }
    }

    fun dispatchHide(value: Float): Boolean {
        if (headerSize <= 0) return false
        cancelAnim()
        if (value < 0) {
            if (offset > minOffset) {
                val newOffset = offset + value
                offset = newOffset.coerceAtLeast(minOffset)
                return true
            }
        }
        return false
    }

    fun dispatchShow(value: Float): Boolean {
        if (headerSize <= 0) return false
        cancelAnim()
        if (value > 0) {
            if (offset < maxOffset) {
                val newOffset = offset + value
                offset = newOffset.coerceAtMost(maxOffset)
                return true
            }
        }
        return false
    }

    fun dispatchFling(velocity: Float) {
        if (velocity == 0f) return
        coroutineScope.launch {
            _anim.apply {
                snapTo(offset)
                updateBounds(lowerBound = minOffset, upperBound = maxOffset)
            }
            _anim.animateDecay(
                initialVelocity = velocity,
                animationSpec = exponentialDecay(frictionMultiplier = 2f),
            ) {
                offset = value
            }
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