package com.google.android.material.bottomsheet

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting


class BottomSheetBehaviorFix<V : View> : BottomSheetBehavior<V> {
    constructor() : super()
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @VisibleForTesting
    override fun findScrollingChild(view: View): View? {
        // FIXME: 先暂时返回 NULL，不让 BottomSheetLayout 对触摸事件进行拦截。
        return null
    }
}