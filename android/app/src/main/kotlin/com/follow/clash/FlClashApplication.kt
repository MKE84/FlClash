package com.follow.clash

import android.app.Application
import android.content.Context
import com.follow.clash.common.GlobalState
import com.follow.clash.service.transparent.TransparentProxyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlClashApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        GlobalState.init(this)
        GlobalState.launch {
            withContext(Dispatchers.IO) {
                TransparentProxyController.clearStale()
            }
        }
    }
}
