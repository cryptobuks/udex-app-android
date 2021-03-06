package com.fridaytech.dex.presentation.convert

import androidx.lifecycle.MutableLiveData
import com.fridaytech.dex.App
import com.fridaytech.dex.R
import com.fridaytech.dex.core.model.Coin
import com.fridaytech.dex.core.model.EConvertType.UNWRAP
import com.fridaytech.dex.core.model.EConvertType.WRAP
import com.fridaytech.dex.core.ui.CoreViewModel
import com.fridaytech.dex.core.ui.SingleLiveEvent
import com.fridaytech.dex.data.adapter.FeeRatePriority
import com.fridaytech.dex.data.adapter.IAdapter
import com.fridaytech.dex.data.adapter.SendStateError
import com.fridaytech.dex.data.manager.ICoinManager
import com.fridaytech.dex.data.manager.duration.ETransactionType
import com.fridaytech.dex.data.manager.duration.IProcessingDurationProvider
import com.fridaytech.dex.data.manager.rates.RatesConverter
import com.fridaytech.dex.presentation.convert.confirm.ConvertConfirmInfo
import com.fridaytech.dex.presentation.convert.model.ConvertConfig
import com.fridaytech.dex.presentation.convert.model.ConvertState
import com.fridaytech.dex.presentation.model.AmountInfo
import com.fridaytech.dex.presentation.model.FeeInfo
import com.fridaytech.dex.presentation.widgets.balance.TotalBalanceInfo
import com.fridaytech.dex.utils.Logger
import com.fridaytech.dex.utils.rx.uiSubscribe
import com.fridaytech.zrxkit.contracts.IWethWrapper
import java.math.BigDecimal
import java.net.SocketTimeoutException
import kotlin.math.absoluteValue

class ConvertViewModel(
    private val coinManager: ICoinManager = App.coinManager,
    private val wethWrapper: IWethWrapper = App.zrxKitManager.zrxKit().getWethWrapperInstance(),
    private val ratesConverter: RatesConverter = App.ratesConverter,
    private val processingDurationProvider: IProcessingDurationProvider = App.processingDurationProvider
) : CoreViewModel() {
    private var minRemainingAmount = 0.001.toBigDecimal()

    private lateinit var config: ConvertConfig
    private lateinit var fromCoin: Coin
    private lateinit var toCoin: Coin

    private var adapter: IAdapter? = null

    private var sendAmount = BigDecimal.ZERO

    var decimalSize: Int = 18

    val convertState = MutableLiveData<ConvertState>()
    val convertAmount = MutableLiveData<BigDecimal>()
    val receiveAmount = MutableLiveData<BigDecimal>()
    val convertEnabled = MutableLiveData<Boolean>()
    val info = MutableLiveData<AmountInfo>()
    val feeInfo = MutableLiveData<FeeInfo>()

    val dismissDialog = SingleLiveEvent<Unit>()
    val transactionSentEvent = SingleLiveEvent<String>()
    val showProcessingEvent = SingleLiveEvent<Unit>()
    val dismissProcessingEvent = SingleLiveEvent<Unit>()
    val showConfirmEvent =
        SingleLiveEvent<ConvertConfirmInfo>()

    private val maxAmount: BigDecimal
        get() = feeInfo.value?.amount?.let { fee ->
            adapter?.balance?.let {
                if (it >= fee) {
                    it - when (config.type) {
                        WRAP -> minRemainingAmount
                        UNWRAP -> BigDecimal.ZERO
                        else -> BigDecimal.ZERO
                    }
                } else BigDecimal.ZERO
            } ?: BigDecimal.ZERO
        } ?: BigDecimal.ZERO

    fun init(config: ConvertConfig) {
        this.config = config

        adapter = App.adapterManager.adapters
            .firstOrNull { it.coin.code == config.coinCode }

        if (adapter == null) {
            errorEvent.postValue(R.string.error_invalid_coin)
            dismissDialog.call()
            return
        }

        fromCoin = coinManager.getCoin(config.coinCode)
        toCoin = coinManager.getCoin(
            if (config.type == WRAP)
                "WETH"
            else
                "ETH"
        )

        val balanceInfo = TotalBalanceInfo(
            adapter!!.coin,
            adapter!!.balance,
            ratesConverter.getCoinsPrice(adapter!!.coin.code, adapter!!.balance)
        )

        convertState.value = ConvertState(
            fromCoin,
            toCoin,
            balanceInfo,
            config.type
        )

        convertEnabled.value = false
        onAmountChanged(BigDecimal.ZERO, true)
        transactionSentEvent.reset()
        dismissDialog.reset()
        decimalSize = adapter?.decimal ?: 18

        val transactionPrice = when (config.type) {
            WRAP -> wethWrapper.depositEstimatedPrice
            UNWRAP -> wethWrapper.withdrawEstimatedPrice
            else -> BigDecimal.ZERO
        }

        minRemainingAmount = transactionPrice.multiply(4.toBigDecimal())
        val transactionPriceFiat = ratesConverter.getCoinsPrice(adapter!!.coin.code, transactionPrice)

        feeInfo.value = FeeInfo(
            adapter?.feeCoinCode ?: "",
            transactionPrice,
            transactionPriceFiat,
            0
        )
    }

    private fun refreshInfo(sendAmount: BigDecimal) {
        adapter?.let { adapter ->
            val info = AmountInfo(
                ratesConverter.getCoinsPrice(adapter.coin.code, sendAmount),
                0
            )

            adapter.validate(sendAmount, null, FeeRatePriority.MEDIUM)
                .forEach {
                    when (it) {
                        is SendStateError.InsufficientAmount -> {
                            info.error = R.string.error_insufficient_balance
                        }
                        is SendStateError.InsufficientFeeBalance -> {
                            info.error = R.string.error_insufficient_fee_balance
                        }
                    }
                }

            this.info.value = info
        }
    }

    private fun convert() {
        if (sendAmount <= maxAmount) {
            showProcessingEvent.call()
            val sendRaw = sendAmount.movePointRight(18).stripTrailingZeros().toBigInteger()
            onAmountChanged(BigDecimal.ZERO, true)

            when (config.type) {
                WRAP -> wethWrapper.deposit(sendRaw)
                UNWRAP -> wethWrapper.withdraw(sendRaw)
                else -> null
            }?.uiSubscribe(disposables, {
                dismissProcessingEvent.call()
                transactionSentEvent.postValue(it.transactionHash)
                dismissDialog.call()
            }, {
                Logger.e(it)

                errorEvent.postValue(
                    when {
                        it is SocketTimeoutException -> R.string.error_timeout_error
                        config.type == UNWRAP -> R.string.error_unwrap_failed
                        else -> R.string.error_wrap_failed
                    }
                )

                dismissProcessingEvent.call()
            })
        } else {
            errorEvent.postValue(R.string.error_invalid_amount)
        }
    }

    fun onMaxClicked() {
        onAmountChanged(maxAmount, true)
    }

    fun onConvertClick() {
        if (sendAmount <= maxAmount) {
            showConfirmEvent.value =
                ConvertConfirmInfo(
                    config.type,
                    fromCoin,
                    sendAmount,
                    toCoin,
                    sendAmount,
                    feeInfo.value?.amount,
                    feeInfo.value?.coinCode,
                    processingDurationProvider.getEstimatedDuration(
                        fromCoin,
                        ETransactionType.CONVERT
                    )
                ) { convert() }
        } else {
            errorEvent.postValue(R.string.error_invalid_amount)
        }
    }

    fun onAmountChanged(amount: BigDecimal?, updateLiveData: Boolean = false) {
        if (sendAmount != amount) {
            sendAmount = amount ?: BigDecimal.ZERO

            amount?.let { refreshInfo(sendAmount) }

            val noErrors = (info.value?.error?.absoluteValue ?: 0) == 0
            val nonZeroAmount = sendAmount > BigDecimal.ZERO

            convertEnabled.value = noErrors && nonZeroAmount

            if (updateLiveData) {
                this.convertAmount.value = sendAmount
            }
        }
    }
}
