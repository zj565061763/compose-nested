package com.sd.demo.compose_nested

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

@Composable
fun VerticalListView(
    modifier: Modifier = Modifier,
    count: Int,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Gray),
    ) {
        items(count) { index ->
            Button(
                onClick = {
                    Toast.makeText(context, index.toString(), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = index.toString())
            }
        }

        item(
            key = "HorizontalListView",
            contentType = "HorizontalListView",
        ) {
            HorizontalListView(height = 500.dp)
        }
    }
}

@Composable
fun HorizontalListView(
    modifier: Modifier = Modifier,
    count: Int = 50,
    height: Dp = 200.dp,
) {
    val context = LocalContext.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Gray),
    ) {
        items(count) { index ->
            Button(
                onClick = {
                    Toast.makeText(context, index.toString(), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.height(height)
            ) {
                Text(text = index.toString())
            }
        }
    }
}

val nestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        logMsg { "1 (${available.y}) $source" }
        return super.onPreScroll(available, source)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        logMsg { "2 (${available.y}) (${consumed.y}) $source" }
        return super.onPostScroll(consumed, available, source)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        logMsg { "--1 (${available.y})" }
        return super.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        logMsg { "--2 (${available.y}) (${consumed.y})" }
        return super.onPostFling(consumed, available)
    }
}