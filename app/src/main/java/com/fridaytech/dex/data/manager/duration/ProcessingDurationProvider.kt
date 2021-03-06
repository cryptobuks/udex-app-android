package com.fridaytech.dex.data.manager.duration

import com.fridaytech.dex.core.model.Coin
import com.fridaytech.dex.data.manager.duration.ETransactionType.*

class ProcessingDurationProvider :
    IProcessingDurationProvider {

    private val millisInSecond = 1000L

    override fun getEstimatedDuration(
        coin: Coin,
        type: ETransactionType
    ): Long = when (type) {
        SEND -> 20
        CONVERT -> 20
        EXCHANGE -> 30
        CANCEL -> 20
        APPROVE -> 20
    } * millisInSecond
}
