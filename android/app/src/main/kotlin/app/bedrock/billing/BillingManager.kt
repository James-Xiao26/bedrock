package app.bedrock.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * The $1 emergency bypass (consumable). Ordering rule: GRANT the bypass
 * before consuming the purchase - a crash between purchase and consume must
 * never strand a paid user, so [reconcile] re-grants any unconsumed bypass
 * on engine start and the consume is retried there too.
 */
class BillingManager(
    context: Context,
    private val onBypassPurchased: () -> Unit,
) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** Connects (if needed) and grants any purchased-but-unconsumed bypass. */
    fun reconcile() = whenConnected {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
            }
        }
    }

    fun launchBypassPurchase(activity: Activity, onError: (String) -> Unit) = whenConnected(
        onUnavailable = { onError("Google Play billing is unavailable on this device.") },
    ) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_BYPASS)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            // PBL 8+: callback returns QueryProductDetailsResult, not List<ProductDetails>.
            val product = details.productDetailsList.firstOrNull()
            if (result.responseCode != BillingClient.BillingResponseCode.OK || product == null) {
                onError("Bypass purchase is not available right now.")
                return@queryProductDetailsAsync
            }
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(product)
                                .build(),
                        ),
                    )
                    .build(),
            )
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach(::handlePurchase)
        } else {
            Log.i(TAG, "purchase flow ended: ${result.responseCode}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (PRODUCT_BYPASS !in purchase.products) return
        Log.i(TAG, "bypass purchased (order ${purchase.orderId}); granting before consume")
        onBypassPurchased()
        client.consumeAsync(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { result, _ ->
            Log.i(TAG, "consume result: ${result.responseCode}")
        }
    }

    private fun whenConnected(onUnavailable: () -> Unit = {}, action: () -> Unit) {
        if (client.isReady) {
            action()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    action()
                } else {
                    Log.i(TAG, "billing unavailable: ${result.responseCode}")
                    onUnavailable()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_BYPASS = "emergency_bypass_1"
    }
}
