package com.smokelaboratory.easyiap

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode.OK
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import com.android.billingclient.api.BillingClient.SkuType.SUBS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class EasyIapConnector(context: Context) {

    private var isIapConnected: Boolean = false
    private val easyIapErrorCode = 23
    private var base64Key: String? = null
    private val iapConnectionMutex = Mutex()

    private var subscriptionSkuList = listOf<String>()
    private var consumableSkuList = listOf<String>()

    private val iapPurchaseResultFlow =
        MutableSharedFlow<Pair<BillingResult, Purchase>>(extraBufferCapacity = 1)
    private val iapErrorFlow = MutableSharedFlow<BillingResult>(extraBufferCapacity = 1)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                OK ->
                    if (!purchases.isNullOrEmpty())
                        CoroutineScope(Dispatchers.IO).launch {
                            purchases.forEach {
                                //todo : check isAcknowledged for consumables
                                if (it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged)
                                    acknowledgePurchase(
                                        it, when {
                                            subscriptionSkuList.contains(it.skus.firstOrNull()) -> SUBS
                                            consumableSkuList.contains(it.skus.firstOrNull()) -> INAPP
                                            else -> ""
                                        }
                                    )
                            }
                        }
                else -> iapErrorFlow.tryEmit(billingResult)
            }
        }
        .enablePendingPurchases()
        .build()

    //region setup

    fun connect(): EasyIapConnector {
        CoroutineScope(Dispatchers.IO).launch {
            connectIap()
        }
        return this
    }

    fun setEncodedBase64Key(base64Key: String): EasyIapConnector {
        this.base64Key = base64Key
        return this
    }

    fun getIapListener(iapListener: (iapErrorFlow: MutableSharedFlow<BillingResult>, iapPurchaseResultFlow: MutableSharedFlow<Pair<BillingResult, Purchase>>) -> Unit) {
        iapListener.invoke(iapErrorFlow, iapPurchaseResultFlow)
    }

    fun enableAutoAcknowledge(
        subscriptionSkuList: List<String>? = null,
        consumableSkuList: List<String>? = null
    ): EasyIapConnector {
        subscriptionSkuList?.let {
            this.subscriptionSkuList = it
        }
        consumableSkuList?.let {
            this.consumableSkuList = it
        }

        return this
    }

    //endregion

    //region connection

    private suspend fun connectIap() {
        iapConnectionMutex.withLock {
            if (!isIapConnected)
                suspendCancellableCoroutine<Unit> {
                    billingClient.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            when (billingResult.responseCode) {
                                OK -> isIapConnected = true
                                else -> iapErrorFlow.tryEmit(billingResult)
                            }
                            it.resume(Unit)
                        }

                        override fun onBillingServiceDisconnected() {
                            iapErrorFlow.tryEmit(
                                prepareBillingResponse(
                                    easyIapErrorCode,
                                    "IAP disconnected"
                                )
                            )

                            isIapConnected = false
                            try {
                                if (it.isActive)
                                    it.resume(Unit)
                            } catch (e: Exception) {

                            }
                        }
                    })
                }
        }
    }

    fun disconnectIap() {
        billingClient.endConnection()
        isIapConnected = false
    }

    private suspend fun checkIAPConnection() {
        if (!isIapConnected)
            connectIap()
    }

    //endregion

    //region fetch products

    suspend fun getInAppProducts(skuList: List<String>): List<SkuDetails> =
        getProducts(INAPP, skuList)

    suspend fun getSubscriptionProducts(skuList: List<String>): List<SkuDetails> =
        getProducts(SUBS, skuList)

    private suspend fun getProducts(productType: String, skuList: List<String>): List<SkuDetails> {
        checkIAPConnection()

        if (isIapConnected) {
            val result = billingClient.querySkuDetails(
                SkuDetailsParams.newBuilder().setSkusList(skuList).setType(productType)
                    .build()
            )

            when (result.billingResult.responseCode) {
                OK -> return result.skuDetailsList.orEmpty()
                else ->
                    iapErrorFlow.tryEmit(result.billingResult)
            }
        }

        return listOf()
    }

    /**
     * returns active subscription & one-time purchases
     */
    suspend fun getPurchaseHistory(skuType: String) =
        suspendCoroutine<List<Purchase>> {
            billingClient.queryPurchasesAsync(
                skuType
            ) { billingResult, products ->
                if (billingResult.responseCode != OK)
                    iapErrorFlow.tryEmit(billingResult)
                it.resume(products)
            }
        }

    //endregion

    //region purchase product

    suspend fun purchaseProduct(activity: AppCompatActivity, skuDetails: SkuDetails) {
        connectIap()

        if (isIapConnected) {
            val resultCode = billingClient.launchBillingFlow(
                activity, BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetails)
                    .build()
            ).responseCode

            if (resultCode != OK)
                iapErrorFlow.tryEmit(
                    prepareBillingResponse(
                        easyIapErrorCode,
                        "Cannot launch billing flow"
                    )
                )
        }
    }

    /**
     * acknowledge a purchase to give entitlement to user
     */
    suspend fun acknowledgePurchase(purchase: Purchase, skuType: String) {
        if (isPurchaseSignatureValid(purchase)) {
            connectIap()

            if (isIapConnected) {
                val acknowledgementResult = when (skuType) {
                    INAPP -> billingClient.consumePurchase(
                        ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    ).billingResult
                    SUBS -> billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    )
                    else -> prepareBillingResponse(OK, "Invalid SKU for acknowledgement")
                }

                iapPurchaseResultFlow.emit(Pair(acknowledgementResult, purchase))
            }
        } else
            iapErrorFlow.emit(
                prepareBillingResponse(
                    easyIapErrorCode,
                    "Purchase cannot be verified"
                )
            )
    }

    private fun isPurchaseSignatureValid(purchase: Purchase): Boolean {
        base64Key?.let {
            return Security.verifyPurchase(
                it, purchase.originalJson, purchase.signature
            )
        }
        return true
    }

    //endregion

    private fun prepareBillingResponse(responseCode: Int, message: String) =
        BillingResult.newBuilder()
            .setDebugMessage(message).setResponseCode(responseCode).build()
}
