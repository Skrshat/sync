package com.offlinesync.utils

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

data class SimCardInfo(
    val subId: Int,
    val displayName: String,
    val carrierName: String,
    val slotIndex: Int,
    val iccId: String
)

object SimCardManager {

    fun getSimCards(context: Context): List<SimCardInfo> {
        val simCards = mutableListOf<SimCardInfo>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList

            if (activeSubscriptions != null) {
                for (subscription in activeSubscriptions) {
                    simCards.add(
                        SimCardInfo(
                            subId = subscription.subscriptionId,
                            displayName = subscription.displayName?.toString() ?: "SIM ${subscription.simSlotIndex + 1}",
                            carrierName = subscription.carrierName?.toString() ?: "Unknown",
                            slotIndex = subscription.simSlotIndex,
                            iccId = subscription.iccId ?: ""
                        )
                    )
                }
            }
        }

        if (simCards.isEmpty()) {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val simState = telephonyManager.simState
                if (simState != TelephonyManager.SIM_STATE_ABSENT && simState != TelephonyManager.SIM_STATE_UNKNOWN) {
                    simCards.add(
                        SimCardInfo(
                            subId = -1,
                            displayName = "Default SIM",
                            carrierName = telephonyManager.networkOperatorName ?: "Unknown",
                            slotIndex = 0,
                            iccId = ""
                        )
                    )
                }
            }
        }

        return simCards
    }

    fun getSimCardBySubId(context: Context, subId: Int): SimCardInfo? {
        return getSimCards(context).find { it.subId == subId }
    }

    fun getDefaultSimSubId(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
            if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return defaultSubId
            }
        }
        return -1
    }
}
