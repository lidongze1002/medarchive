package com.mvp.medarchive.ui.health

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.ui.theme.MedArchiveTheme

/**
 * Health Connect 权限说明页。系统在弹出健康数据授权前会先展示这个页面，
 * 没有它 Health Connect 会直接拒绝授权。
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedArchiveTheme {
                RationaleScreen(
                    onAgree = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun RationaleScreen(onAgree: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "读取手环 / 体脂秤数据",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "授权后会从「健康连接」读取你已经在小米运动、华为健康等 App 里同步的：",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(10.dp))
            listOf("体重（体脂秤）", "心率（手环/手表）", "血压", "血糖").forEach { item ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("• ", style = MaterialTheme.typography.bodyLarge)
                    Text(item, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "这些数据只用于在本机生成趋势图，不会上传到任何服务器；你随时可以在系统「健康连接」里撤销授权。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth()
        ) { Text("同意并继续") }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) { Text("暂不授权", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
