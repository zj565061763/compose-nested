package com.sd.lib.compose.nested

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.sd.lib.compose.gesture.fConsume
import com.sd.lib.compose.gesture.fPointer
import kotlin.math.absoluteValue

@Composable
fun FNestedHeader(
    modifier: Modifier = Modifier,
    debug: Boolean = false,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val state = remember(coroutineScope) { NestedHeaderState(coroutineScope) }.apply {
        this.debug = debug
    }

    SubcomposeLayout(modifier = modifier) { cs ->
        val hasFixedWidth = cs.hasFixedWidth
        val hasFixedHeight = cs.hasFixedHeight
        @Suppress("NAME_SHADOWING")
        val cs = cs.copy(minWidth = 0, minHeight = 0)

        val headerPlaceable = (subcompose(SlotId.Header) {
            HeaderBox(
                state = state,
                debug = debug,
                header = header,
            )
        }).let {
            check(it.size == 1)
            it.first().measure(cs.copy(maxHeight = Constraints.Infinity))
        }

        val contentPlaceable = (subcompose(SlotId.Content) {
            ContentBox(
                state = state,
                content = content,
            )
        }).let {
            check(it.size == 1)
            it.first().measure(cs)
        }

        val width = if (hasFixedWidth) {
            cs.maxWidth
        } else {
            cs.constrainWidth(maxOf(headerPlaceable.width, contentPlaceable.width))
        }

        val height = if (hasFixedHeight) {
            cs.maxHeight
        } else {
            cs.constrainHeight(headerPlaceable.height + contentPlaceable.height)
        }

        state.setSize(
            header = headerPlaceable.height,
            content = contentPlaceable.height,
            container = height,
        )

        logMsg(debug) {
            "header:${headerPlaceable.height} content:${contentPlaceable.height} container:${height}"
        }

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
    debug: Boolean,
    header: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .nestedScroll(
                connection = object : NestedScrollConnection {},
                dispatcher = state.headerNestedScrollDispatcher,
            )
            .headerGesture(state = state, debug = debug),
    ) {
        header()
    }
}

@Composable
private fun ContentBox(
    state: NestedHeaderState,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .nestedScroll(state.contentNestedScrollConnection)
            .fPointer(
                pass = PointerEventPass.Initial,
                onDown = { input ->
                    if (pointerCount == 1 && state.cancelFling()) {
                        input.consume()
                    }
                    cancelPointer()
                },
            )
    ) {
        content()
    }
}

private fun Modifier.headerGesture(
    state: NestedHeaderState,
    debug: Boolean,
): Modifier {
    return composed {

        var isDrag by remember { mutableStateOf(false) }

        if (state.isReady) {
            this.fPointer(
                touchSlop = 0f,
                onStart = {
                    logMsg(debug) { "header start" }
                    isDrag = false
                    calculatePan = true
                },
                onDown = {
                    if (pointerCount == 1) {
                        val cancelFling = state.cancelFling()
                        val cancelContentFling = state.cancelContentFling()
                        if (cancelFling || cancelContentFling) {
                            it.consume()
                            isDrag = true
                            logMsg(debug) { "header drag" }
                        }
                    }
                },
                onCalculate = {
                    if (!isDrag) {
                        val positionChanged = currentEvent.changes.any { it.positionChanged() }
                        if (!positionChanged) {
                            logMsg(debug) { "header cancel consumed" }
                            cancelPointer()
                            return@fPointer
                        }
                        if (this.pan.x.absoluteValue >= this.pan.y.absoluteValue) {
                            logMsg(debug) { "header cancel x >= y" }
                            cancelPointer()
                            return@fPointer
                        }
                        isDrag = true
                        logMsg(debug) { "header drag" }
                    }

                    currentEvent.fConsume { it.positionChanged() }

                    state.headerNestedScrollDispatcher.dispatchScroll(
                        available = Offset(0f, this.pan.y),
                        source = NestedScrollSource.Drag,
                    ) { left ->
                        val leftValue = left.y
                        when {
                            leftValue < 0 -> state.dispatchHide(leftValue)
                            leftValue > 0 -> state.dispatchShow(leftValue)
                            else -> false
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
                onFinish = {
                    logMsg(debug) { "header finish" }
                    isDrag = false
                }
            )
        } else {
            this
        }
    }
}

internal inline fun logMsg(
    debug: Boolean,
    block: () -> Any?
) {
    if (debug) {
        Log.i("FNestedHeader", block().toString())
    }
}