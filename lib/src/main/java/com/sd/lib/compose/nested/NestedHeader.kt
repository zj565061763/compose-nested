package com.sd.lib.compose.nested

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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun NestedHeader(
   modifier: Modifier = Modifier,
   state: NestedHeaderState = rememberNestedHeaderState(),
   header: @Composable () -> Unit,
   content: @Composable () -> Unit,
) {
   state.density = LocalDensity.current

   @Suppress("NAME_SHADOWING")
   SubcomposeLayout(modifier = modifier) { cs ->
      val hasFixedWidth = cs.hasFixedWidth
      val hasFixedHeight = cs.hasFixedHeight
      val cs = cs.copy(minWidth = 0, minHeight = 0)

      val headerPlaceable = (subcompose(SlotId.Header) {
         HeaderBox(
            state = state,
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

@Composable
private fun HeaderBox(
   state: NestedHeaderState,
   header: @Composable () -> Unit,
) {
   val modifier = if (state.isReady) {
      Modifier
         .headerGesture(state)
         .nestedScroll(
            connection = state.headerNestedScrollConnection,
            dispatcher = state.headerNestedScrollDispatcher,
         )
   } else {
      Modifier
   }

   Box(modifier = modifier) {
      header()
   }
}

private fun Modifier.headerGesture(
   state: NestedHeaderState,
): Modifier = this.composed {
   var hasDrag by remember { mutableStateOf(false) }
   val velocityTracker = remember { VelocityTracker() }
   val coroutineScope = rememberCoroutineScope()
   pointerInput(state) {
      awaitEachGesture {
         val down = awaitFirstDown(requireUnconsumed = false)

         state.logMsg { "header start hasDrag:${hasDrag}" }
         velocityTracker.resetTracking()

         // finishNormally，true表示正常结束，false表示取消
         val finishNormally = drag(down.id) { input ->
            val pan = input.positionChange()

            if (!hasDrag) {
               if (pan.x.absoluteValue > pan.y.absoluteValue) {
                  state.logMsg { "header cancel x > y" }
                  throw CancellationException()
               }
               hasDrag = true
               state.logMsg { "header drag" }
            }

            if (hasDrag) {
               input.consume()
               velocityTracker.addPointerInputChange(input)
               state.dispatchNestedScroll(
                  available = pan.y,
                  source = NestedScrollSource.UserInput,
               )
            }
         }

         if (hasDrag && finishNormally) {
            val velocity = velocityTracker.calculateVelocity().y
            coroutineScope.launch {
               state.dispatchFling(velocity)
            }
         }

         state.logMsg { "header finish" }
      }
   }.pointerInput(state) {
      awaitEachGesture {
         val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
         )

         state.logMsg { "header reset" }
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