package com.smokelaboratory.easyiap

import com.android.billingclient.api.Purchase

/**
 * Establishes communication bridge between caller and [EasyIapConnector].
 * [onConsumablePurchaseRetrieved] and [onNonConsumablePurchaseRetrieved] are used to get list of purchases.
 * [onSkuDetailsRetrieved] is to listen data about product IDs retrieved from Play console.
 * [onError] is used to notify caller about possible errors.
 */
interface InAppEventsListener {
    fun onSkuDetailsRetrieved(
        skuType: String,
        skuDetailsList: List<DataWrappers.SkuInfo>
    )

    fun onConsumablePurchaseRetrieved(consumableProduct: DataWrappers.PurchaseInfo)
    fun onNonConsumablePurchaseRetrieved(nonConsumableProduct: DataWrappers.PurchaseInfo)
    fun onError(inAppConnector: EasyIapConnector, message: String)
}