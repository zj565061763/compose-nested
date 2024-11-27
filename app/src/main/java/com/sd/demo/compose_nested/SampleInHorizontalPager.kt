package com.sd.demo.compose_nested

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.sd.demo.compose_nested.ui.theme.AppTheme
import com.sd.lib.compose.nested.NestedHeader
import com.sd.lib.compose.nested.rememberNestedHeaderState

class SampleInHorizontalPager : ComponentActivity() {
   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      setContent {
         AppTheme {
            Content()
         }
      }
   }
}

@Composable
private fun Content(
   modifier: Modifier = Modifier,
) {
   val pagerState = rememberPagerState { 2 }
   HorizontalPager(
      state = pagerState,
      beyondViewportPageCount = pagerState.pageCount,
      modifier = modifier.fillMaxSize(),
   ) { index ->
      if (index == 0) {
         FirstPageView(
            modifier = Modifier.nestedScroll(TestNestedScrollConnection)
         )
      } else {
         VerticalListView(
            count = 100,
            modifier = Modifier.nestedScroll(TestNestedScrollConnection)
         )
      }
   }
}

@Composable
private fun FirstPageView(
   modifier: Modifier = Modifier,
) {
   val state = rememberNestedHeaderState(debug = true)
   NestedHeader(
      modifier = modifier.fillMaxSize(),
      state = state,
      header = { TestHeaderView() }
   ) {
      VerticalListView(count = 50)
   }
}