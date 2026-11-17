package com.mvp.medarchive.security

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

class LockManager(context: Context) {
    private val prefs = context.getSharedPreferences("medarchive_lock", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getString("pin_hash", null) != null

    fun verify(pin: String): Boolean = prefs.getString("pin_hash", null) == hash(pin)

    fun setPin(pin: String) {
        prefs.edit().putString("pin_hash", hash(pin)).apply()
    }

    fun disable() {
        prefs.edit().remove("pin_hash").apply()
    }

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val lock = remember { LockManager(ctx) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("家庭病历本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "输入密码解锁",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v ->
                if (v.length <= 6 && v.all { it.isDigit() }) {
                    pin = v
                    error = false
                }
            },
            label = { Text("密码") },
            singleLine = true,
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        if (error) {
            Text(
                "密码错误",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (lock.verify(pin)) onUnlock() else error = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("解锁") }
    }
}

@Composable
fun LockSettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val lock = remember { LockManager(ctx) }
    var enabled by remember { mutableStateOf(lock.isEnabled()) }
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("应用锁") },
        text = {
            Column {
                Text(
                    if (enabled) "已开启，打开 App 需输入密码。" else "开启后打开 App 需输入 4-6 位数字密码。",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!enabled) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) pin = v },
                        label = { Text("设置密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            }
        },
        confirmButton = {
            if (enabled) {
                TextButton(onClick = {
                    lock.disable()
                    onDismiss()
                }) { Text("关闭应用锁") }
            } else {
                TextButton(
                    onClick = {
                        lock.setPin(pin)
                        onDismiss()
                    },
                    enabled = pin.length >= 4
                ) { Text("开启") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
