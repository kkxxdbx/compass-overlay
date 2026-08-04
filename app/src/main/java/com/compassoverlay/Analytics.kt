package com.compassoverlay

import android.content.Context
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure

object Analytics {

    private val ready: Boolean
        get() = !BuildConfig.UMENG_APPKEY.startsWith("YOUR_")

    fun init(context: Context) {
        if (!ready) return
        UMConfigure.init(
            context,
            BuildConfig.UMENG_APPKEY,
            BuildConfig.UMENG_CHANNEL,
            UMConfigure.DEVICE_TYPE_PHONE,
            null
        )
    }

    fun track(context: Context, event: String) {
        if (!ready) return
        MobclickAgent.onEvent(context, event)
    }
}
