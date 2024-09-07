/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 André Sousa
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.andrefrsousa.superbottomsheet

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.annotation.IntRange
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehaviorFix
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class SuperBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var sheetTouchOutsideContainer: View
    private lateinit var sheetContainer: CornerRadiusFrameLayout
    private lateinit var behavior: BottomSheetBehaviorFix<*>
    private lateinit var callback: BottomSheetBehavior.BottomSheetCallback

    // Customizable properties
    private var propertyDim = 0f
    private var propertyCornerRadius = 0f
    private var propertyStatusBarColor = 0
    private var propertyIsAlwaysExpanded = false
    private var propertyIsSheetCancelableOnTouchOutside = true
    private var propertyIsSheetCancelable = true
    private var propertyAnimateCornerRadius = true

    // Bottom sheet properties
    private var canSetStatusBarColor = false

    /** Methods from [BottomSheetDialogFragment]  */

    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return SuperBottomSheetDialog(context, R.style.superBottomSheetDialog)
    }

    @CallSuper
    @SuppressLint("NewApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Change status bar on the condition: API >= 21
        val supportsStatusBarColor = hasMinimumSdk(Build.VERSION_CODES.LOLLIPOP)
        canSetStatusBarColor = !context.isTablet() && supportsStatusBarColor

        // Init properties
        propertyDim = getDim()
        propertyCornerRadius = getCornerRadius()
        propertyStatusBarColor = getStatusBarColor()
        propertyIsAlwaysExpanded = isSheetAlwaysExpanded()
        propertyIsSheetCancelable = isSheetCancelable()
        propertyIsSheetCancelableOnTouchOutside = isSheetCancelableOnTouchOutside()

        // Set dialog properties
        dialog.runIfNotNull {
            setCancelable(propertyIsSheetCancelable)

            val isCancelableOnTouchOutside =
                propertyIsSheetCancelable && propertyIsSheetCancelableOnTouchOutside
            setCanceledOnTouchOutside(isCancelableOnTouchOutside)

            window.runIfNotNull {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(propertyDim)

                if (supportsStatusBarColor) {
                    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    // 设置状态栏，并且全屏显示
                    addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    statusBarColor = propertyStatusBarColor
                }

                if (context.isTablet() && !context.isInPortrait()) {
                    setGravity(Gravity.CENTER_HORIZONTAL)
                    setLayout(
                        resources.getDimensionPixelSize(R.dimen.super_bottom_sheet_width),
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            }
        }

        return null
    }

    @CallSuper
    override fun onStart() {
        super.onStart()

        // Init UI components
        iniBottomSheetUiComponents()
    }

    override fun onResume() {
        behavior.addBottomSheetCallback(callback)
        super.onResume()
    }

    override fun onPause() {
        behavior.removeBottomSheetCallback(callback)
        super.onPause()
    }

    //region UI METHODS

    @UiThread
    private fun iniBottomSheetUiComponents() {
        // Store views references
        sheetContainer = dialog?.findViewById(R.id.super_bottom_sheet)!!
        sheetTouchOutsideContainer = dialog?.findViewById(R.id.touch_outside)!!

        // Set the bottom sheet radius
        sheetContainer.setBackgroundColor(getBackgroundColor())
        sheetContainer.setCornerRadius(propertyCornerRadius)

        // Load bottom sheet behaviour
        behavior = BottomSheetBehavior.from(sheetContainer) as BottomSheetBehaviorFix<*>

        // Set tablet sheet width when in landscape. This will avoid full bleed sheet
        if (context.isTablet() && !context.isInPortrait()) {
            sheetContainer.layoutParams = sheetContainer.layoutParams.apply {
                width = resources.getDimensionPixelSize(R.dimen.super_bottom_sheet_width)
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }
        // If is always expanded, there is no need to set the peek height
        if (propertyIsAlwaysExpanded) {
            sheetContainer.layoutParams = sheetContainer.layoutParams.apply {
                height = getExpandedHeight()
            }

        } else {
            behavior.peekHeight = getPeekHeight()
            sheetContainer.minimumHeight = behavior.peekHeight
        }

        // Only skip the collapse state when the device is in landscape or the sheet is always expanded
        val deviceInLandscape =
            (!context.isTablet() && !context.isInPortrait()) || propertyIsAlwaysExpanded
        behavior.skipCollapsed = deviceInLandscape

        if (deviceInLandscape) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED

            // Load content container height
            sheetContainer.viewTreeObserver.addOnPreDrawListener(object :
                ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (sheetContainer.height > 0) {
                        sheetContainer.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    return true
                }
            })
        }

        // Override sheet callback events
        callback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    dialog?.cancel()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        }
    }

    //region PUBLIC
    @Dimension
    open fun getPeekHeight(): Int = with(context!!.getAttrId(R.attr.superBottomSheet_peekHeight)) {
        val peekHeightMin = when (this) {
            INVALID_RESOURCE_ID -> resources.getDimensionPixelSize(R.dimen.super_bottom_sheet_peek_height)
            else -> resources.getDimensionPixelSize(this)
        }

        // 16:9 ratio
        return with(resources.displayMetrics) {
            Math.max(peekHeightMin, heightPixels - heightPixels * 9 / 16)
        }
    }

    @Dimension
    open fun getDim(): Float = with(context!!.getAttrId(R.attr.superBottomSheet_dim)) {
        return when (this) {
            INVALID_RESOURCE_ID -> TypedValue().run {
                resources.getValue(R.dimen.super_bottom_sheet_dim, this, true)
                float
            }

            else -> TypedValue().let {
                resources.getValue(this, it, true)
                it.float
            }
        }
    }

    @ColorInt
    open fun getBackgroundColor(): Int =
        with(context!!.getAttrId(R.attr.superBottomSheet_backgroundColor)) {
            return when (this) {
                INVALID_RESOURCE_ID -> Color.WHITE
                else -> ContextCompat.getColor(context!!, this)
            }
        }

    @ColorInt
    open fun getStatusBarColor(): Int =
        with(context!!.getAttrId(R.attr.superBottomSheet_statusBarColor)) {
            return when (this) {
                INVALID_RESOURCE_ID -> ContextCompat.getColor(
                    context!!,
                    context!!.getAttrId(R.attr.colorPrimaryDark)
                )

                else -> ContextCompat.getColor(context!!, this)
            }
        }

    @Dimension
    open fun getCornerRadius(): Float =
        with(context!!.getAttrId(R.attr.superBottomSheet_cornerRadius)) {
            return when (this) {
                INVALID_RESOURCE_ID -> context!!.resources.getDimension(R.dimen.super_bottom_sheet_radius)
                else -> resources.getDimension(this)
            }
        }

    open fun isSheetAlwaysExpanded(): Boolean =
        with(context!!.getAttrId(R.attr.superBottomSheet_alwaysExpanded)) {
            return when (this) {
                INVALID_RESOURCE_ID -> context!!.resources.getBoolean(R.bool.super_bottom_sheet_isAlwaysExpanded)
                else -> resources.getBoolean(this)
            }
        }

    open fun isSheetCancelableOnTouchOutside(): Boolean =
        with(context!!.getAttrId(R.attr.superBottomSheet_cancelableOnTouchOutside)) {
            return when (this) {
                INVALID_RESOURCE_ID -> context!!.resources.getBoolean(R.bool.super_bottom_sheet_cancelableOnTouchOutside)
                else -> resources.getBoolean(this)
            }
        }

    open fun isSheetCancelable(): Boolean =
        with(context!!.getAttrId(R.attr.superBottomSheet_cancelable)) {
            return when (this) {
                INVALID_RESOURCE_ID -> context!!.resources.getBoolean(R.bool.super_bottom_sheet_cancelable)
                else -> resources.getBoolean(this)
            }
        }

    @IntRange(
        from = ViewGroup.LayoutParams.WRAP_CONTENT.toLong(),
        to = ViewGroup.LayoutParams.MATCH_PARENT.toLong()
    )
    open fun getExpandedHeight() =
        with(context!!.getAttrId(R.attr.superBottomSheet_expandedHeight)) {
            when (this) {
                INVALID_RESOURCE_ID -> context!!.resources.getInteger(R.integer.super_bottom_expanded_behaviour)
                else -> resources.getInteger(this)
            }
        }
    //endregion
}