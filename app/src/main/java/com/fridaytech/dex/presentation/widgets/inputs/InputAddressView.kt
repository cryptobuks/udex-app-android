package com.fridaytech.dex.presentation.widgets.inputs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.fridaytech.dex.R
import kotlinx.android.synthetic.main.view_address_input.view.*

class InputAddressView : ConstraintLayout {

    init {
        inflate(context, R.layout.view_address_input, this)
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun bindAddressInputInitial(
        onBarcodeClick: (() -> Unit)? = null,
        onPasteClick: (() -> Unit)? = null,
        onDeleteClick: (() -> Unit)? = null
    ) {
        address_barcode_scan?.visibility = View.VISIBLE
        address_paste?.visibility = View.VISIBLE
        address_delete?.visibility = View.GONE

        address_barcode_scan?.setOnClickListener { onBarcodeClick?.invoke() }
        address_paste?.setOnClickListener { onPasteClick?.invoke() }
        address_delete?.setOnClickListener { onDeleteClick?.invoke() }

        invalidate()
    }

    private fun updateAddress(address: String) {
        val empty = address.isEmpty()
        address_barcode_scan.visibility = if (empty) View.VISIBLE else View.GONE
        address_paste.visibility = if (empty) View.VISIBLE else View.GONE
        address_delete.visibility = if (!empty) View.VISIBLE else View.GONE

        address_input.text = address
    }

    fun updateInput(address: String = "", errorText: String? = null) {
        updateAddress(address)

        errorText?.let {
            address_input_error.visibility = View.VISIBLE
            address_input_error.text = it
        } ?: run {
            address_input_error.visibility = View.GONE
        }
    }

    fun updateInput(address: String = "", errorCode: Int? = null) {
        updateAddress(address)

        if (errorCode == null || errorCode == 0) {
            address_input_error.visibility = View.GONE
        } else {
            address_input_error.visibility = View.VISIBLE
            address_input_error.setText(errorCode)
        }
    }
}
