package com.mvp.medarchive.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class ProManager(context: Context) {
    private val prefs = context.getSharedPreferences("medarchive_pro", Context.MODE_PRIVATE)

    fun isPro(): Boolean = prefs.getBoolean("is_pro", false)

    fun setPro() {
        prefs.edit().putBoolean("is_pro", true).apply()
    }

    fun clearPro() {
        prefs.edit().putBoolean("is_pro", false).apply()
    }

    companion object {
        const val FREE_MEMBER_LIMIT = 2
        const val FREE_RECORD_LIMIT_PER_MEMBER = 20
        const val PRODUCT_ID = "pro_unlock"
    }
}

/**
 * 真实 Google Play 内购（一次性买断）。
 * 上架前需要在 Play Console 创建「应用内商品」pro_unlock 并激活；
 * 未上架/未配置时连接会失败，UI 会显示为「暂不可购买」。
 */
class BillingManager(
    context: Context,
    private val onProUpdate: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val pro = ProManager(appContext)
    private var client: BillingClient? = null
    var productDetails: ProductDetails? = null
        private set

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    fun connect(onReady: (Boolean) -> Unit) {
        if (client?.isReady == true) {
            onReady(true)
            return
        }
        val c = BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdated)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                onReady(result.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                onReady(false)
            }
        })
    }

    fun loadProduct(onLoaded: (ProductDetails?) -> Unit) {
        val c = client ?: run { onLoaded(null); return }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProManager.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        c.queryProductDetailsAsync(params) { result, list ->
            val details = if (result.responseCode == BillingClient.BillingResponseCode.OK) list.firstOrNull() else null
            productDetails = details
            onLoaded(details)
        }
    }

    fun priceText(details: ProductDetails?): String? =
        details?.oneTimePurchaseOfferDetails?.formattedPrice

    fun launchPurchase(activity: Activity, onError: (String) -> Unit) {
        val c = client
        val details = productDetails
        if (c == null || !c.isReady || details == null) {
            onError("应用商店未就绪，请稍后再试")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = com.mvp.medarchive.security.ExternalUi.launch {
            c.launchBillingFlow(activity, params)
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onError("无法发起购买（${result.responseCode}）")
        }
    }

    /** 恢复购买：已购用户重装后取回权益。 */
    fun restore(onResult: (Boolean) -> Unit) {
        val c = client ?: run { onResult(false); return }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        c.queryPurchasesAsync(params) { _, purchases ->
            var found = false
            purchases.forEach {
                if (it.products.contains(ProManager.PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    found = true
                    handlePurchase(it)
                }
            }
            if (!found) onResult(false)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(ProManager.PRODUCT_ID)) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client?.acknowledgePurchase(params) { }
        }
        pro.setPro()
        onProUpdate(true)
    }

    fun end() {
        client?.endConnection()
        client = null
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun ProDialog(onDismiss: () -> Unit, onPurchased: () -> Unit = {}) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    var price by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var purchased by remember { mutableStateOf(false) }

    val billing = remember {
        BillingManager(ctx) { ok ->
            if (ok) purchased = true
        }
    }

    val debugBuild = remember(ctx) {
        (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    LaunchedEffect(Unit) {
        billing.connect { ready ->
            if (!ready) {
                loading = false
                message = "应用商店不可用：上架 Google Play 并配置商品后才能购买"
                return@connect
            }
            billing.loadProduct { details ->
                loading = false
                price = billing.priceText(details)
                if (details == null) message = "商品尚未在 Play Console 配置"
            }
        }
    }

    LaunchedEffect(purchased) {
        if (purchased) {
            onPurchased()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("升级 Pro", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "免费版最多 ${ProManager.FREE_MEMBER_LIMIT} 位成员、每位 ${ProManager.FREE_RECORD_LIMIT_PER_MEMBER} 条记录。升级 Pro 解锁：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text("• 无限成员与记录", style = MaterialTheme.typography.bodyMedium)
                Text("• 数据备份恢复（含照片）", style = MaterialTheme.typography.bodyMedium)
                Text("• OCR 检查单识别", style = MaterialTheme.typography.bodyMedium)
                Text("• PDF 导出无水印", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (debugBuild) {
                    Text(
                        "Debug 版：可直接解锁用于测试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = {
                        if (debugBuild && price == null) {
                            ProManager(ctx).setPro()
                            onPurchased()
                            onDismiss()
                        } else {
                            activity?.let {
                                billing.launchPurchase(it) { err -> message = err }
                            } ?: run { message = "无法获取当前页面" }
                        }
                    },
                    enabled = !loading
                ) {
                    Text(price?.let { "解锁 Pro $it" } ?: "解锁 Pro")
                }
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    billing.restore { ok -> if (!ok) message = "没有找到已购记录" }
                }) { Text("恢复购买") }
                TextButton(onClick = onDismiss) { Text("以后再说") }
            }
        }
    )
}
