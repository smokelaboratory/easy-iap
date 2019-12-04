package com.smokelaboratory.easyiapexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.smokelaboratory.easyiap.DataWrappers
import com.smokelaboratory.easyiap.EasyIapConnector
import com.smokelaboratory.easyiap.InAppEventsListener
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val tag: String = "EasyIAP"
    private var fetchedSkuDetailsList = mutableListOf<DataWrappers.SkuInfo>()
    private lateinit var easyIapConnector: EasyIapConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectEasyIap()
        listeners()
    }

    private fun connectEasyIap() {
        easyIapConnector = EasyIapConnector(
            this, "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArEaiCq7os9cmF" +
                    "+i564+pIOiSOVZa/LRzu0K79Dg6wKWjnJ1PkHAa4ZOJ81KrxyFk3q3UiJ3lNsTCdW216+KKdKp+YCOFLs" +
                    "sN+4FKjFBqY9lJbm6uuxZ9cPugMOTVFrVlmreYyhIY4jysfo4+LeyEmB7D20X7M+7diCRBEIsOY9lA2ne" +
                    "OtD6j0BR4rhLGR3xjN0LGrhCCdbw42+eIkc/awbY7FypLMJjbAmEnNBe1tlOxxX6ZgspwAlY8XjnX832l" +
                    "xxHdnuJKSPGtYCQLSt/LYc/go90/kc/U+oPtQy/KgCiQEcKeIL1a6AB294JDogkHuqRIeXIu1n4sAfzG" +
                    "cshrJQIDAQAB"
        )
            .setInAppProductIds(listOf("gas", "premium_car"))
            .setSubscriptionIds(listOf("gold_monthly", "gold_yearly"))
            .setConsumableProductIds(listOf("gas"))
            .connect()

        easyIapConnector.setOnInAppEventsListener(object : InAppEventsListener {
            override fun onSkuDetailsRetrieved(skuType: String, skuDetailsList: List<DataWrappers.SkuInfo>) {
                Log.d(tag, "Retrieved SKU details list : $skuDetailsList")
                fetchedSkuDetailsList.addAll(skuDetailsList)
            }

            override fun onConsumablePurchaseRetrieved(consumableProduct: DataWrappers.PurchaseInfo) {
                Log.d(tag, "Retrieved consumable product : $consumableProduct")
            }

            override fun onNonConsumablePurchaseRetrieved(nonConsumableProduct: DataWrappers.PurchaseInfo) {
                Log.d(tag, "Retrieved non consumable product : $nonConsumableProduct")
            }

            override fun onError(inAppConnector: EasyIapConnector, message: String) {
                Log.e(tag, message)
            }
        })
    }

    private fun listeners() {
        bt_purchase_cons.setOnClickListener {
            easyIapConnector.makePurchase(fetchedSkuDetailsList.find { it.sku == "gas" }!!)
        }
        bt_purchase_subs.setOnClickListener {
            easyIapConnector.makePurchase(fetchedSkuDetailsList.find { it.sku == "gold_monthly" }!!)
        }
        bt_purchase_iap.setOnClickListener {
            easyIapConnector.makePurchase(fetchedSkuDetailsList.find { it.sku == "premium_car" }!!)
        }
    }
}