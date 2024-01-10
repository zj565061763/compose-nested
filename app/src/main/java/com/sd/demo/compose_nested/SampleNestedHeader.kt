package com.sd.demo.compose_nested

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
private fun Content(
    modifier: Modifier = Modifier,
) {
    FNestedHeader(
        modifier = modifier.fillMaxSize(),
        header = {
            HeaderView()
        }
    ) {
        ListView()
    }
}

@Composable
private fun HeaderView(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(Color.Red)
    )
}

@Composable
private fun ListView(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        items(100) { index ->
            Button(onClick = { }) {
                Text(text = index.toString())
            }
        }
    }
}