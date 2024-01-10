package com.sd.lib.compose.nested

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.SubcomposeLayout
import com.sd.lib.compose.gesture.fPointer

@Composable
fun FNestedHeader(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by remember { mutableStateOf(NestedState()) }

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
            it.first().measure(cs)
        }.also {
            state.headerSize = it.height
        }

        val headerSize = headerPlaceable.height + offset
        val leftHeight = (cs.maxHeight - headerSize).coerceAtLeast(0)

        val contentPlaceable = subcompose(SlotId.Content, content).let {
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
    Box(
        modifier = Modifier.fPointer(
            onStart = {
                calculatePan = true
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

private class NestedState {
    var headerSize: Int = 0

    val maxOffset: Float = 0f
    val minOffset: Float get() = -headerSize.toFloat()

    var offset by mutableFloatStateOf(0f)

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
        if (value > 0) {
            if (offset < maxOffset) {
                val newOffset = offset + value
                offset = newOffset.coerceAtMost(maxOffset)
                return true
            }
        }
        return false
    }
}