package com.sd.lib.compose.nested

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID
import kotlin.math.absoluteValue

@Composable
fun rememberNestedHeaderState(
   initialOffset: Float = 0f,
   debug: Boolean = false,
): NestedHeaderState {
   return rememberSaveable(saver = NestedHeaderState.Saver) {
      NestedHeaderState(initialOffset = initialOffset)
   }.also {
      it.debug = debug
   }
}

class NestedHeaderState internal constructor(
   initialOffset: Float = 0f,
) {
   var isReady by mutableStateOf(false)
      private set

   var offset by mutableFloatStateOf(initialOffset)

   private val _anim = Animatable(0f)
   private var _minOffset = 0f
   private val _maxOffset = 0f

   private var _flingJob: Job? = null
   private var _contentFlingJob: Job? = null

   internal var debug: Boolean = false
   internal lateinit var density: Density

   internal val headerNestedScrollConnection = object : NestedScrollConnection {}
   internal val headerNestedScrollDispatcher = NestedScrollDispatcher()

   internal val contentNestedScrollConnection: NestedScrollConnection = NestedScrollConnectionY(
      onPreScroll = { value, _ ->
         scrollToHide(value)
      },
      onPostScroll = { value, _ ->
         scrollToShow(value)
      },
      onPreFling = {
         _contentFlingJob = currentCoroutineContext()[Job]
         false
      }
   )

   internal fun setSize(header: Int, content: Int, container: Int) {
      logMsg { "setSize header:${header} content:${content} container:${container}" }
      isReady = header > 0
      _minOffset = if (content < container) {
         val bottom = header + content
         val delta = container - bottom
         delta.toFloat().coerceAtMost(0f)
      } else {
         -header.toFloat()
      }
      offset.coerceIn(_minOffset, _maxOffset)
   }

   private fun scrollToHide(value: Float): Boolean {
      if (!isReady) return false
      if (value < 0 && offset > _minOffset) {
         val newOffset = offset + value
         offset = newOffset.coerceAtLeast(_minOffset)
         return true
      }
      return false
   }

   private fun scrollToShow(value: Float): Boolean {
      if (!isReady) return false
      if (value > 0 && offset < _maxOffset) {
         val newOffset = offset + value
         offset = newOffset.coerceAtMost(_maxOffset)
         return true
      }
      return false
   }

   internal suspend fun dispatchFling(velocity: Float) {
      if (!isReady) return
      if (velocity.absoluteValue < 300) return

      val uuid = if (debug) UUID.randomUUID().toString() else ""
      logMsg { "fling start velocity:${velocity} $uuid" }

      _flingJob = currentCoroutineContext()[Job]

      val available = Velocity(0f, velocity)
      val preConsumed = headerNestedScrollDispatcher.dispatchPreFling(available).consumedCoerceIn(available)

      val left = available - preConsumed
      logMsg { "fling preConsumed:${preConsumed.y} left:${left.y} $uuid" }

      try {
         if (left != Velocity.Zero) {
            _anim.updateBounds(lowerBound = _minOffset, upperBound = _maxOffset)
            _anim.snapTo(offset)

            var lastValue = _anim.value
            _anim.animateDecay(
               initialVelocity = left.y,
               animationSpec = splineBasedDecay(density),
            ) {
               val delta = value - lastValue
               lastValue = value
               dispatchNestedScroll(
                  available = delta,
                  source = NestedScrollSource.SideEffect,
               )
            }
         }
      } finally {
         val postConsumed = headerNestedScrollDispatcher.dispatchPostFling(left, Velocity.Zero)
         logMsg { "fling end postConsumed:${postConsumed.y} $uuid" }
      }
   }

   internal fun dispatchNestedScroll(
      available: Float,
      source: NestedScrollSource,
   ) {
      headerNestedScrollDispatcher.dispatchScroll(
         available = Offset(0f, available),
         source = source,
      ) { left ->
         val leftValue = left.y
         when {
            leftValue < 0 -> scrollToHide(leftValue)
            leftValue > 0 -> scrollToShow(leftValue)
            else -> false
         }
      }
   }

   internal fun cancelFling(): Boolean {
      val job = _flingJob ?: return false
      _flingJob = null
      return job.isActive.also { isActive ->
         if (isActive) {
            logMsg { "fling cancel" }
            job.cancel()
         }
      }
   }

   internal fun cancelContentFling(): Boolean {
      val job = _contentFlingJob ?: return false
      _contentFlingJob = null
      return job.isActive.also { isActive ->
         if (isActive) {
            logMsg { "content fling cancel" }
            job.cancel()
         }
      }
   }

   companion object {
      internal val Saver = listSaver(
         save = { listOf(it.offset) },
         restore = { NestedHeaderState(initialOffset = it[0]) }
      )
   }
}

private inline fun NestedScrollDispatcher.dispatchScroll(
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
   val consumedX = x.consumedCoerceIn(available.x)
   val consumedY = y.consumedCoerceIn(available.y)
   return if (x == consumedX && y == consumedY) {
      this
   } else {
      this.copy(x = consumedX, y = consumedY)
   }
}

private fun Velocity.consumedCoerceIn(available: Velocity): Velocity {
   val consumedX = x.consumedCoerceIn(available.x)
   val consumedY = y.consumedCoerceIn(available.y)
   return if (x == consumedX && y == consumedY) {
      this
   } else {
      this.copy(x = consumedX, y = consumedY)
   }
}

private fun Float.consumedCoerceIn(available: Float): Float {
   return when {
      available > 0f -> coerceIn(0f, available)
      available < 0f -> coerceIn(available, 0f)
      else -> 0f
   }
}

private class NestedScrollConnectionY(
   val onPreScroll: (Float, NestedScrollSource) -> Boolean,
   val onPostScroll: (Float, NestedScrollSource) -> Boolean,
   val onPreFling: suspend (Float) -> Boolean,
) : NestedScrollConnection {
   override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      return if (onPreScroll(available.y, source)) {
         available.copy(x = 0f)
      } else {
         super.onPreScroll(available, source)
      }
   }

   override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      return if (onPostScroll(available.y, source)) {
         available.copy(x = 0f)
      } else {
         super.onPostScroll(consumed, available, source)
      }
   }

   override suspend fun onPreFling(available: Velocity): Velocity {
      return if (onPreFling(available.y)) {
         available.copy(x = 0f)
      } else {
         super.onPreFling(available)
      }
   }
}

internal inline fun NestedHeaderState.logMsg(block: () -> String) {
   if (debug) {
      Log.d("NestedHeader", block())
   }
}