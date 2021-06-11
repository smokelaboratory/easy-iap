package com.smokelaboratory.easyiapexample

import android.icu.text.DateTimePatternGenerator.PatternInfo.OK
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.SkuDetails
import com.smokelaboratory.easyiap.EasyIapConnector
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val tag: String = "EasyIAP"
    private var fetchedSkuDetailsList = mutableListOf<SkuDetails>()
    private lateinit var easyIapConnector: EasyIapConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectEasyIap()
        listeners()
    }

    private fun connectEasyIap() {
        easyIapConnector = EasyIapConnector(this).enableAutoAcknowledge(
            consumableSkuList = listOf("gas"),
            subscriptionSkuList = listOf("infinite_gas_yearly", "infinite_gas_yearly")
        ).connect()

        easyIapConnector.getIapListener { iapErrorFlow, iapPurchaseResultFlow ->
            lifecycleScope.launch {
                iapErrorFlow.collect {
                    Log.d(tag, "Error => ${it.responseCode} : ${it.debugMessage}")
                }
            }

            lifecycleScope.launch {
                iapPurchaseResultFlow.collect {
                    if (it.first.responseCode == OK)
                        Log.d(tag, "Purchase => ${it.second}")
                    else
                        Log.d(
                            tag,
                            "Acknowledgement error => ${it.first.responseCode} : ${it.first.debugMessage}"
                        )
                }
            }
        }

        lifecycleScope.launch {
            fetchedSkuDetailsList.addAll(
                easyIapConnector.getInAppProducts(
                    listOf(
                        "gas",
                        "premium"
                    )
                )
            )
        }

        lifecycleScope.launch {
            fetchedSkuDetailsList.addAll(
                easyIapConnector.getSubscriptionProducts(
                    listOf(
                        "infinite_gas_monthly",
                        "infinite_gas_yearly"
                    )
                )
            )
        }
    }

    private fun listeners() {
        bt_purchase_cons.setOnClickListener {
            fetchedSkuDetailsList.find { it.sku == "gas" }?.let {
                lifecycleScope.launch {
                    easyIapConnector.purchaseProduct(this@MainActivity, it)
                }
            }
        }
        bt_purchase_subs.setOnClickListener {
            fetchedSkuDetailsList.find { it.sku == "infinite_gas_monthly" }?.let {
                lifecycleScope.launch {
                    easyIapConnector.purchaseProduct(this@MainActivity, it)
                }
            }
        }
        bt_purchase_iap.setOnClickListener {
            fetchedSkuDetailsList.find { it.sku == "premium" }?.let {
                lifecycleScope.launch {
                    easyIapConnector.purchaseProduct(this@MainActivity, it)
                }
            }
        }
    }
}