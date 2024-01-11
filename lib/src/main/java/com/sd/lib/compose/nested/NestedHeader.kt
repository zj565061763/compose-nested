package com.sd.lib.compose.nested

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.sd.lib.compose.gesture.fConsume
import com.sd.lib.compose.gesture.fPointer
import kotlin.math.absoluteValue

@Composable
fun FNestedHeader(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val state by remember(coroutineScope) {
        mutableStateOf(NestedHeaderState(coroutineScope))
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
    state: NestedHeaderState,
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
    state: NestedHeaderState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.nestedScroll(state.contentNestedScrollConnection)
    ) {
        content()
    }
}

private fun Modifier.headerGesture(
    state: NestedHeaderState,
): Modifier {
    return composed {

        var isDrag by remember { mutableStateOf(false) }

        if (state.isReady) {
            this.fPointer(
                onStart = {
                    state.isHeaderTouch = true
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
                    state.isHeaderTouch = false
                }
            )
        } else {
            this
        }
    }
}