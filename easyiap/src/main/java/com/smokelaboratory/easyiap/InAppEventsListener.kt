package com.smokelaboratory.easyiap

/**
 * Establishes communication bridge between caller and [EasyIapConnector].
 * [onProductsPurchased] provides recent purchases
 * [onPurchaseAcknowledged] provides a callback after a purchase is acknowledged
 * [onSubscriptionsFetched] and [onInAppProductsFetched] is to listen data about product IDs retrieved from Play console.
 * [onError] is used to notify caller about possible errors.
 */
interface InAppEventsListener {
    fun onSubscriptionsFetched(skuDetailsList: List<DataWrappers.SkuInfo>)
    fun onInAppProductsFetched(skuDetailsList: List<DataWrappers.SkuInfo>)
    fun onPurchaseAcknowledged(purchase: DataWrappers.PurchaseInfo)
    fun onProductsPurchased(purchases: List<DataWrappers.PurchaseInfo>)
    fun onError(inAppConnector: EasyIapConnector, result: DataWrappers.BillingResponse? = null)
}