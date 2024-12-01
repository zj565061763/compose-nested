package com.sd.demo.compose_nested

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sd.demo.compose_nested.ui.theme.AppTheme
import com.sd.lib.compose.nested.NestedHeader
import com.sd.lib.compose.nested.rememberNestedHeaderState

class SampleShortHeader : ComponentActivity() {
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
   val state = rememberNestedHeaderState(debug = true)
   NestedHeader(
      modifier = modifier.fillMaxSize(),
      state = state,
      header = {
         HeaderView(
            modifier = Modifier.clickable {
               state.hideHeader()
            }
         )
      }
   ) {
      VerticalListView(count = 50)
   }
}

@Composable
private fun HeaderView(
   modifier: Modifier = Modifier,
) {
   Column(modifier = modifier.fillMaxWidth()) {
      Box(
         modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.Red)
      )
   }
}