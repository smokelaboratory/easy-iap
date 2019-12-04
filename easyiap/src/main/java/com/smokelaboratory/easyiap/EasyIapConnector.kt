package com.smokelaboratory.easyiap

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode.*
import com.android.billingclient.api.BillingClient.FeatureType.SUBSCRIPTIONS
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import com.android.billingclient.api.BillingClient.SkuType.SUBS
import com.android.billingclient.api.Purchase.PurchaseState.PENDING
import com.android.billingclient.api.Purchase.PurchaseState.PURCHASED

/**
 * Wrapper class for Google In-App purchases.
 * Handles vital processes while dealing with IAP.
 * Works around a listener [InAppEventsListener] for delivering events back to the caller.
 */
class EasyIapConnector(
    private val activity: AppCompatActivity,
    private val base64Key: String
) {
    private var fetchedSkuDetailsList = mutableListOf<SkuDetails>()
    private val tag = "InAppLog"
    private var inAppEventsListener: InAppEventsListener? = null

    private var inAppIds: List<String>? = null
    private var subIds: List<String>? = null
    private var consumableIds: List<String> = listOf()

    private lateinit var iapClient: BillingClient

    init {
        init(activity)
    }

    /**
     * To set INAPP product IDs.
     */
    fun setInAppProductIds(inAppIds: List<String>): EasyIapConnector {
        this.inAppIds = inAppIds
        return this
    }

    /**
     * To set consumable product IDs.
     * Rest of the IDs will be considered non-consumable.
     */
    fun setConsumableProductIds(consumableIds: List<String>): EasyIapConnector {
        this.consumableIds = consumableIds
        return this
    }

    /**
     * To set SUBS product IDs.
     */
    fun setSubscriptionIds(subIds: List<String>): EasyIapConnector {
        this.subIds = subIds
        return this
    }

    /**
     * Called to purchase an item.
     * Its result is received in [PurchasesUpdatedListener] which further is handled
     * by [handleConsumableProducts] / [handleNonConsumableProducts].
     */
    fun makePurchase(skuDetails: DataWrappers.SkuInfo) {
        if (fetchedSkuDetailsList == null)
            inAppEventsListener?.onError(this, "Products not fetched")
        else
            iapClient.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder().setSkuDetails(fetchedSkuDetailsList?.find { it.sku == skuDetails.sku }!!).build()
            )
    }

    /**
     * To attach an event listener to establish a bridge with the caller.
     */
    fun setOnInAppEventsListener(inAppEventsListener: InAppEventsListener) {
        this.inAppEventsListener = inAppEventsListener
    }

    /**
     * To initialise EasyIapConnector.
     */
    private fun init(context: Context) {
        iapClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                /**
                 * Only recent purchases are received here
                 */

                when (billingResult.responseCode) {
                    OK -> purchases?.let { processPurchases(purchases.toHashSet()) }
                    ITEM_ALREADY_OWNED -> getAllPurchases()
                    SERVICE_DISCONNECTED -> connect()
                    else -> Log.i(tag, "Purchase update : ${billingResult.debugMessage}")
                }
            }.build()
    }

    /**
     * Connects billing client with Play console to start working with IAP.
     */
    fun connect(): EasyIapConnector {
        Log.d(tag, "Billing service : Connecting...")
        if (!iapClient.isReady) {
            iapClient.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    Log.d(tag, "Billing service : Disconnected")
                    connect()
                }

                override fun onBillingSetupFinished(billingResult: BillingResult?) {
                    when (billingResult?.responseCode) {
                        OK -> {
                            Log.d(tag, "Billing service : Connected")
                            inAppIds?.let {
                                querySku(INAPP, it)
                            }
                            subIds?.let {
                                querySku(SUBS, it)
                            }
                            getAllPurchases()
                        }
                        BILLING_UNAVAILABLE -> Log.d(tag, "Billing service : Unavailable")
                        else -> Log.d(tag, "Billing service : Setup error")
                    }
                }
            })
        }
        return this
    }

    /**
     * Fires a query in Play console to get [SkuDetails] for provided type and IDs.
     */
    private fun querySku(skuType: String, ids: List<String>) {
        iapClient.querySkuDetailsAsync(
            SkuDetailsParams.newBuilder()
                .setSkusList(ids).setType(skuType).build()
        ) { billingResult, skuDetailsList ->
            when (billingResult.responseCode) {
                OK -> {
                    if (skuDetailsList.isEmpty()) {
                        Log.d(tag, "Query SKU : Data not found (List empty)")
                        inAppEventsListener?.onError(this, billingResult.debugMessage)
                    } else {
                        Log.d(tag, "Query SKU : Data found")

                        fetchedSkuDetailsList.addAll(skuDetailsList)

                        inAppEventsListener?.onSkuDetailsRetrieved(
                            skuType,
                            skuDetailsList.map {
                                DataWrappers.SkuInfo(
                                    it.sku,
                                    it.description,
                                    it.freeTrialPeriod,
                                    it.iconUrl,
                                    it.introductoryPrice,
                                    it.introductoryPriceAmountMicros,
                                    it.introductoryPriceCycles,
                                    it.introductoryPricePeriod,
                                    it.isRewarded,
                                    it.originalJson,
                                    it.originalPrice,
                                    it.originalPriceAmountMicros,
                                    it.price,
                                    it.priceAmountMicros,
                                    it.priceCurrencyCode,
                                    it.subscriptionPeriod,
                                    it.title,
                                    it.type
                                )
                            })
                    }
                }
                else -> {
                    Log.d(tag, "Query SKU : Failed")
                    inAppEventsListener?.onError(this, billingResult.debugMessage)
                }
            }
        }
    }

    /**
     * Returns all the **non-consumable** purchases of the user.
     */
    fun getAllPurchases() {
        val allPurchases = HashSet<Purchase>()
        allPurchases.addAll(iapClient.queryPurchases(INAPP).purchasesList)
        if (isSubSupportedOnDevice())
            allPurchases.addAll(iapClient.queryPurchases(SUBS).purchasesList)
        processPurchases(allPurchases)
    }

    /**
     * Checks purchase signature for more security.
     */
    private fun processPurchases(allPurchases: HashSet<Purchase>) {
        val validPurchases = HashSet<Purchase>(allPurchases.size)
        allPurchases.forEach {
            when (it.purchaseState) {
                PURCHASED -> {
                    Log.d(tag, "Purchase validity : Purchased -> $it")
                    if (isPurchaseSignatureValid(it))
                        validPurchases.add(it)
                    else
                        Log.d(tag, "Purchase validity : Signature didn't match")
                }
                PENDING -> Log.d(tag, "Purchase validity : Purchase pending -> $it")
            }
        }

        val (consumables, nonConsumables) = validPurchases.partition {
            consumableIds.contains(it.sku)
        }
        handleConsumableProducts(consumables)
        handleNonConsumableProducts(nonConsumables)
    }

    /**
     * Consumable products might be brought/consumed by users multiple times (for eg. diamonds, coins).
     * Hence, it is necessary to notify Play console about such products.
     * Subscriptions are always non-consumable.
     */
    private fun handleConsumableProducts(consumables: List<Purchase>) {
        consumables.forEach {
            iapClient.consumeAsync(
                ConsumeParams.newBuilder()
                    .setPurchaseToken(it.purchaseToken).build()
            ) { billingResult, purchaseToken ->
                when (billingResult.responseCode) {
                    OK -> inAppEventsListener?.onConsumablePurchaseRetrieved(
                        DataWrappers.PurchaseInfo(
                            it.purchaseState,
                            it.developerPayload,
                            it.isAcknowledged,
                            it.isAutoRenewing,
                            it.orderId,
                            it.originalJson,
                            it.packageName,
                            it.purchaseTime,
                            it.purchaseToken,
                            it.signature,
                            it.sku
                        )
                    )
                    else -> {
                        Log.d(tag, "Handling consumables : Error -> ${billingResult.debugMessage}")
                        inAppEventsListener?.onError(this, billingResult.debugMessage)
                    }
                }
            }
        }


    }

    /**
     * Purchase of non-consumable products must be acknowledged to Play console.
     * This will avoid refunding for these products to users by Google.
     */
    private fun handleNonConsumableProducts(nonConsumables: List<Purchase>) {
        nonConsumables.forEach {
            iapClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(
                    it.purchaseToken
                ).build()
            ) { billingResult ->

                when (billingResult.responseCode) {
                    OK -> inAppEventsListener?.onNonConsumablePurchaseRetrieved(
                        DataWrappers.PurchaseInfo(
                            it.purchaseState,
                            it.developerPayload,
                            it.isAcknowledged,
                            it.isAutoRenewing,
                            it.orderId,
                            it.originalJson,
                            it.packageName,
                            it.purchaseTime,
                            it.purchaseToken,
                            it.signature,
                            it.sku
                        )
                    )
                    else -> {
                        Log.d(tag, "Handling non consumables : Error -> ${billingResult.debugMessage}")
                        inAppEventsListener?.onError(this, billingResult.debugMessage)
                    }
                }
            }
        }

    }

    /**
     * Before using subscriptions, device-support must be checked.
     */
    private fun isSubSupportedOnDevice(): Boolean {
        var isSupported = false
        when (iapClient.isFeatureSupported(SUBSCRIPTIONS).responseCode) {
            OK -> {
                isSupported = true
                Log.d(tag, "Subs support check : Success")
            }
            SERVICE_DISCONNECTED -> connect()
            else -> Log.d(tag, "Subs support check : Error")
        }
        return isSupported
    }

    /**
     * Checks purchase signature validity
     */
    private fun isPurchaseSignatureValid(purchase: Purchase): Boolean {
        return Security.verifyPurchase(
            base64Key, purchase.originalJson, purchase.signature
        )
    }
}