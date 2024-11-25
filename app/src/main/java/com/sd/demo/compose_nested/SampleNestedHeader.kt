package com.sd.demo.compose_nested

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.sd.demo.compose_nested.ui.theme.AppTheme
import com.sd.lib.compose.nested.FNestedHeader

class SampleNestedHeader : ComponentActivity() {
   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      setContent {
         AppTheme {
            Content()
         }
      }
   }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Content(
   modifier: Modifier = Modifier,
) {
   val pagerState = rememberPagerState { 10 }

   HorizontalPager(
      state = pagerState
   ) { index ->
      if (index == 0) {
         FNestedHeader(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            debug = true,
            header = {
               HeaderView()
            }
         ) {
            VerticalListView(count = 50)
         }
      } else {
         Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
         ) {
            Text(text = index.toString())
         }
      }
   }
}

@Composable
private fun HeaderView(
   modifier: Modifier = Modifier,
) {
   Column(
      modifier = modifier.fillMaxWidth()
   ) {
      Box(
         modifier = Modifier
             .fillMaxWidth()
             .height(300.dp)
             .background(Color.Red)
             .clickable {
                 logMsg { "click Red" }
             }
      )

      HorizontalListView()

      Box(
         modifier = Modifier
             .fillMaxWidth()
             .height(500.dp)
             .background(Color.Green)
      )

      Box(
         modifier = Modifier
             .fillMaxWidth()
             .height(500.dp)
             .background(Color.Blue)
             .clickable {
                 logMsg { "click Blue" }
             }
      )
   }
}