package com.fridaytech.dex.presentation.pin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.fridaytech.dex.R
import com.fridaytech.dex.core.ui.CoreActivity
import com.fridaytech.dex.data.security.biometric.BiometricManager
import com.fridaytech.dex.presentation.main.MainActivity
import com.fridaytech.dex.presentation.widgets.MainToolbar
import com.fridaytech.dex.presentation.widgets.NumPadItem
import com.fridaytech.dex.presentation.widgets.NumPadItemType.*
import com.fridaytech.dex.presentation.widgets.NumPadItemsAdapter
import com.fridaytech.dex.utils.ui.ToastHelper
import com.fridaytech.dex.utils.visible
import kotlinx.android.synthetic.main.activity_pin.*

class PinActivity : CoreActivity(), NumPadItemsAdapter.Listener, BiometricManager.IBiometricListener {

    private lateinit var viewModel: PinViewModel
    private lateinit var pagesAdapter: PinPagesAdapter
    private lateinit var biometricManager: BiometricManager

    private val visiblePage: Int?
        get() = (pin_pages_recycler?.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()

    //region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        initViews()

        initViewModel()

        biometricManager = BiometricManager(this, this)
    }

    private fun initViews() {
        pagesAdapter = PinPagesAdapter()
        pin_pages_recycler.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(pin_pages_recycler)

        pin_pages_recycler.adapter = pagesAdapter
        pin_pages_recycler.setOnTouchListener { _, _ -> true }

        pin_numpad?.bind(this, FINGER, false)
    }

    private fun initViewModel() {
        val interactionType = intent.getSerializableExtra(EXTRA_INTERACTION_TYPE) as PinInteractionType
        val showCancelButton = intent.getBooleanExtra(EXTRA_SHOW_CANCEL, false)

        viewModel = ViewModelProviders.of(this).get(PinViewModel::class.java)
        viewModel.init(interactionType, showCancelButton)

        viewModel.hideToolbar.observe(this, Observer {
            toolbar?.visible = false
        })

        viewModel.showBackButton.observe(this, Observer {
            if (it) {
                toolbar?.bind(MainToolbar.getBackAction { viewModel.onBackPressed() })
            }
        })

        viewModel.titleLiveData.observe(this, Observer { title ->
            title?.let {
                toolbar.setTitle(it)
            }
        })

        viewModel.pagesLiveData.observe(this, Observer { pinPages ->
            pinPages?.let {
                pagesAdapter.setPages(pinPages)
            }
        })

        viewModel.showPageAtIndex.observe(this, Observer { index ->
            index?.let {
                Handler().postDelayed({
                    visiblePage?.let {
                        pagesAdapter.setEnteredPinLength(it, 0)
                        pin_pages_recycler?.smoothScrollToPosition(index)
                    }
                }, 300)
            }
        })

        viewModel.showErrorForPage.observe(this, Observer { errorForPage ->
            errorForPage?.let { (error, pageIndex) ->
                pagesAdapter.setErrorForPage(pageIndex, error.let { getString(error) } ?: null)
            }
        })

        viewModel.showError.observe(this, Observer { error ->
            error?.let { ToastHelper.showErrorMessage(it) }
        })

        viewModel.showSuccess.observe(this, Observer { success ->
            success?.let { ToastHelper.showSuccessMessage(it, 1000) }
        })

        viewModel.navigateToMain.observe(this, Observer {
            MainActivity.start(this)
            finish()
        })

        viewModel.fillPinCircles.observe(this, Observer { pair ->
            pair?.let { (length, pageIndex) ->
                pagesAdapter.setEnteredPinLength(pageIndex, length)
            }
        })

        viewModel.dismissWithCancelEvent.observe(this, Observer {
            setResult(RESULT_CANCELLED)
            finish()
        })

        viewModel.dismissWithSuccessEvent.observe(this, Observer {
            setResult(RESULT_OK)
            finish()
        })

        viewModel.showFingerprintInputEvent.observe(this, Observer { cryptoObject ->
            cryptoObject?.let {
                showBiometricDialog(it)
                pin_numpad.showFingerPrintButton = true
            }
        })

        viewModel.resetCirclesWithShakeAndDelayForPageEvent.observe(this, Observer { pageIndex ->
            pageIndex?.let {
                pagesAdapter.shakePageIndex = it
                pagesAdapter.notifyDataSetChanged()
                Handler().postDelayed({
                    pagesAdapter.shakePageIndex = null
                    pagesAdapter.setEnteredPinLength(pageIndex, 0)
                    viewModel.resetPin()
                }, 300)
            }
        })

        viewModel.closeApplicationEvent.observe(this, Observer {
            finishAffinity()
        })
    }

    override fun addTestLabel() = Unit

    //endregion

    override fun onBackPressed() {
        viewModel.onBackPressed()
    }

    override fun onItemClick(item: NumPadItem) {
        visiblePage?.let { page ->
            when (item.type) {
                NUMBER -> viewModel.onNumberEnter(page, item.number.toString())
                DELETE -> viewModel.onDeleteClick(page)
                FINGER -> viewModel.onBiometricUnlockClick()
                else -> {}
            }
        }
    }

    override fun onItemLongClick(item: NumPadItem) {
    }

    //region Biometric

    override fun onAuthSuccess() {
        viewModel.onBiometricUnlock()
    }

    override fun onAuthFail(type: BiometricManager.ErrorType) {
        runOnUiThread {
            ToastHelper.showErrorMessage(R.string.error_enter_your_pin)
        }
    }

    private fun showBiometricDialog(cryptoObject: BiometricPrompt.CryptoObject) {
        biometricManager.request(cryptoObject = cryptoObject)
    }

    //endregion

    companion object {
        private const val EXTRA_INTERACTION_TYPE = "interaction_type"
        private const val EXTRA_SHOW_CANCEL = "show_cancel"

        const val RESULT_OK = 1
        const val RESULT_CANCELLED = 2

        fun startForSetPin(context: AppCompatActivity, requestCode: Int) {
            startForResult(
                context,
                requestCode,
                PinInteractionType.SET_PIN
            )
        }

        fun startForEditPin(context: Context) {
            start(
                context,
                PinInteractionType.EDIT_PIN
            )
        }

        fun startForUnlock(context: AppCompatActivity, requestCode: Int, showCancel: Boolean = false) {
            startForResult(
                context,
                requestCode,
                PinInteractionType.UNLOCK,
                showCancel
            )
        }

        private fun start(context: Context, interactionType: PinInteractionType) {
            val intent = Intent(context, PinActivity::class.java)
            intent.putExtra(EXTRA_INTERACTION_TYPE, interactionType)
            context.startActivity(intent)
        }

        private fun startForResult(context: AppCompatActivity, requestCode: Int, interactionType: PinInteractionType, showCancel: Boolean = true) {
            val intent = Intent(context, PinActivity::class.java)
            intent.putExtra(EXTRA_INTERACTION_TYPE, interactionType)
            intent.putExtra(EXTRA_SHOW_CANCEL, showCancel)
            context.startActivityForResult(intent, requestCode)
//            context.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}
