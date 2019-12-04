package com.smokelaboratory.easyiap

import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails

class DataWrappers {

    data class SkuInfo(
        val sku: String,
        val description: String,
        val freeTrailPeriod: String,
        val iconUrl: String,
        val introductoryPrice: String,
        val introductoryPriceAmountMicros: Long,
        val introductoryPriceCycles: String,
        val introductoryPricePeriod: String,
        val isRewarded: Boolean,
        val originalJson: String,
        val originalPrice: String,
        val originalPriceAmountMicros: Long,
        val price: String,
        val priceAmountMicros: Long,
        val priceCurrencyCode: String,
        val subscriptionPeriod: String,
        val title: String,
        val type: String
    )

    data class PurchaseInfo(
        val purchaseState: Int,
        val developerPayload: String?,
        val isAcknowledged: Boolean,
        val isAutoRenewing: Boolean,
        val orderId: String,
        val originalJson: String,
        val packageName: String,
        val purchaseTime: Long,
        val purchaseToken: String,
        val signature: String,
        val sku: String
    )
}