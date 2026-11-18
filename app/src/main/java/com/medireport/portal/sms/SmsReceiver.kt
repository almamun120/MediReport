package com.medireport.portal.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.medireport.portal.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Puts incoming SMS into the persistent queue and wakes the worker. Does nothing unless enabled. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // long texts arrive in several parts from the same sender: join them
        val byNumber = messages.groupBy { it.originatingAddress.orEmpty() }
            .mapValues { (_, parts) -> parts.joinToString("") { it.messageBody.orEmpty() } }
        val pending = goAsync()
        val app = context.applicationContext as App
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.repository.prefs.getSms().enabled) {
                    byNumber.forEach { (from, body) -> SmsProcessor.enqueue(app.repository, from, body) }
                    SmsProcessor.kick(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
