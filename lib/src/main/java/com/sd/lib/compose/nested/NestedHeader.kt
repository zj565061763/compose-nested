package com.sd.lib.compose.nested

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlinx.coroutines.CancellationException
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
        this.density = LocalDensity.current
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
    val headerModifier = if (state.isReady) {
        Modifier
            .headerGesture(state = state, debug = debug)
            .nestedScroll(
                connection = object : NestedScrollConnection {},
                dispatcher = state.headerNestedScrollDispatcher,
            )
    } else {
        Modifier
    }

    Box(modifier = headerModifier) {
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
            .let { m ->
                if (state.isReady) {
                    m.pointerInput(state) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            if (state.cancelFling()) {
                                down.consume()
                            }
                        }
                    }
                } else {
                    m
                }
            }
    ) {
        content()
    }
}

private fun Modifier.headerGesture(
    state: NestedHeaderState,
    debug: Boolean,
): Modifier = this.composed {

    var hasDrag by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            logMsg(debug) { "header start hasDrag:${hasDrag}" }
            velocityTracker.resetTracking()

            // finishOrCancel，true表示正常结束，false表示取消
            val finishOrCancel = drag(down.id) { input ->
                val pan = input.positionChange()

                if (!hasDrag) {
                    if (pan.x.absoluteValue > pan.y.absoluteValue) {
                        logMsg(debug) { "header cancel x > y" }
                        throw CancellationException()
                    }
                    hasDrag = true
                    logMsg(debug) { "header drag" }
                }

                if (hasDrag) {
                    input.consume()
                    velocityTracker.addPointerInputChange(input)
                    state.dispatchNestedScroll(
                        available = pan.y,
                        source = NestedScrollSource.Drag,
                    )
                }
            }

            if (hasDrag) {
                if (finishOrCancel) {
                    val velocity = velocityTracker.calculateVelocity().y
                    state.dispatchFling(velocity)
                }
            }
            logMsg(debug) { "header finish" }
        }
    }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )

                logMsg(debug) { "header reset" }
                hasDrag = false

                val cancelFling = state.cancelFling()
                val cancelContentFling = state.cancelContentFling()
                if (cancelFling || cancelContentFling) {
                    down.consume()
                    hasDrag = true
                }
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