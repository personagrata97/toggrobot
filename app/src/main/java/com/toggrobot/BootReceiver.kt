package com.toggrobot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Telefon yeniden baslarsa (veya uygulama guncellenirse) servisi geri ac. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Config(context).running) {
            runCatching { CheckerService.start(context) }
        }
    }
}
