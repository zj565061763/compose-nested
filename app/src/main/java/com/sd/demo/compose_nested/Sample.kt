package com.sd.demo.compose_nested

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sd.demo.compose_nested.ui.theme.AppTheme
import com.sd.lib.compose.nested.NestedHeader
import com.sd.lib.compose.nested.rememberNestedHeaderState

class Sample : ComponentActivity() {
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
      header = { TestHeaderView() }
   ) {
      VerticalListView(count = 50)
   }
}