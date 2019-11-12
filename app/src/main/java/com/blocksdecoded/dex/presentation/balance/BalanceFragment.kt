package com.blocksdecoded.dex.presentation.balance

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.blocksdecoded.dex.R
import com.blocksdecoded.dex.core.ui.CoreFragment
import com.blocksdecoded.dex.presentation.balance.recycler.BalanceAdapter
import com.blocksdecoded.dex.presentation.balance.recycler.BalanceViewHolder
import com.blocksdecoded.dex.presentation.coinmanager.CoinManagerActivity
import com.blocksdecoded.dex.presentation.common.ActionViewHolder
import com.blocksdecoded.dex.presentation.convert.ConvertDialog
import com.blocksdecoded.dex.presentation.dialogs.AlertDialogFragment
import com.blocksdecoded.dex.presentation.receive.ReceiveDialog
import com.blocksdecoded.dex.presentation.send.SendDialog
import com.blocksdecoded.dex.presentation.transactions.TransactionsActivity
import com.blocksdecoded.dex.presentation.widgets.MainToolbar
import com.blocksdecoded.dex.utils.openUrl
import com.blocksdecoded.dex.utils.ui.AnimationHelper
import com.blocksdecoded.dex.utils.visible
import kotlinx.android.synthetic.main.fragment_balance.*
import kotlinx.android.synthetic.main.view_top_up_account.*

class BalanceFragment : CoreFragment(R.layout.fragment_balance),
        BalanceViewHolder.Listener,
        ActionViewHolder.Listener {

    private lateinit var adapter: BalanceAdapter
    private lateinit var viewModel: BalanceViewModel

    private val buyCryptoToolbarAction = MainToolbar.ActionInfo(
        R.drawable.ic_credit_card,
        R.string.action_buy_crypto
    ) { viewModel.onBuyCryptoClick() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = BalanceAdapter(this, this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(BalanceViewModel::class.java)

        viewModel.balances.observe(this, Observer {
            adapter.setCoins(it)
        })

        viewModel.totalBalance.observe(this, Observer {
            balance_total?.update(
                it,
                isIconVisible = false,
                isFiatPrimary = true
            )
        })

        viewModel.openReceiveDialog.observe(this, Observer { coinCode ->
            activity?.let {
                ReceiveDialog.open(it.supportFragmentManager, coinCode)
            }
        })

        viewModel.openSendDialog.observe(this, Observer { coinCode ->
            activity?.let {
                SendDialog.open(it.supportFragmentManager, coinCode)
            }
        })

        viewModel.openConvertDialog.observe(this, Observer { config ->
            activity?.let {
                ConvertDialog.open(it.supportFragmentManager, config)
            }
        })

        viewModel.openTransactions.observe(this, Observer { coinCode ->
            activity?.let {
                TransactionsActivity.start(it, coinCode)
            }
        })

        viewModel.totalBalanceVisible.observe(this, Observer {
            balance_total?.visible = it
        })

        viewModel.topUpVisible.observe(this, Observer { topUpVisible ->
            if (topUpVisible) {
                if (!top_up_container.visible) {
                    toolbar.bind(rightActionButton = null)
                    AnimationHelper.expand(top_up_container, speed = 1.5f)
                }
            } else {
                toolbar.bind(rightActionButton = buyCryptoToolbarAction)
                top_up_container.visible = false
            }
        })

        viewModel.openCoinInfo.observe(this, Observer {
            CoinInfoDialog.show(childFragmentManager, it)
        })

        viewModel.openCoinManager.observe(this, Observer {
            activity?.let { CoinManagerActivity.start(it) }
        })

        viewModel.openUrlEvent.observe(this, Observer { url ->
            activity?.let { it.openUrl(url) }
        })

        viewModel.showTestModeDialog.observe(this, Observer {
            AlertDialogFragment.newInstance(
                R.string.test_network,
                R.string.test_network_buy_crypto_description,
                R.string.ok
            ).show(childFragmentManager, "test_mode_buy_crypto")
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        balance_recycler?.layoutManager = object : LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }

        balance_recycler?.adapter = adapter
        (balance_recycler?.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        swipe_refresh?.setOnRefreshListener {
            viewModel.refresh()
            swipe_refresh?.isRefreshing = false
        }

        top_up_add_coins?.setOnClickListener { viewModel.onAddCoinsClick() }

        top_up_buy_crypto?.setOnClickListener { viewModel.onBuyCryptoClick() }
    }

    //region ViewHolder

    override fun onClick() {
        viewModel.onManageCoinsClick()
    }

    override fun onClick(position: Int) = adapter.toggleViewHolder(position)

    override fun onSendClick(position: Int) = viewModel.onSendClick(position)
    override fun onReceiveClick(position: Int) = viewModel.onReceiveClick(position)
    override fun onTransactionsClick(position: Int) = viewModel.onTransactionsClick(position)
    override fun onConvertClick(position: Int) = viewModel.onConvertClick(position)
    override fun onInfoClick(position: Int) = viewModel.onInfoClick(position)
    //endregion

    companion object {
        fun newInstance() = BalanceFragment()
    }
}
