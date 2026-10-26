package com.turni.calendario.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ShiftAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        handleNativeAlarm(context, intent)
    }

    private fun handleNativeAlarm(context: Context, intent: Intent) {
        if (intent.action == ACTION_TRIGGER_SHIFT_ALARM) {
            // Application.onCreate restores persisted rules before a cold-start receiver.
            try {
                androidx.core.content.ContextCompat.startForegroundService(context,
                    Intent(context, ShiftAlarmService::class.java).apply {
                        action = ShiftAlarmService.ACTION_RING
                        putExtras(intent)
                    })
            } catch (error: RuntimeException) {
                Log.e(TAG, "Alarm playback service could not start", error)
                ShiftAlarmService.recordDelivery(context, "error", error.javaClass.simpleName)
            }
        }
        val rescheduleActions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", ACTION_TRIGGER_SHIFT_ALARM)
        if (intent.action in rescheduleActions) {
            val pending = goAsync()
            executor.execute {
                try { ShiftAlarmScheduler.scheduleNextAlarm(context.applicationContext) }
                catch (error: Exception) { Log.e(TAG, "Alarm reschedule failed", error) }
                finally { pending.finish() }
            }
        }
    }

    companion object {
        const val TAG = "ShiftAlarmReceiver"
        const val ACTION_TRIGGER_SHIFT_ALARM = "com.turni.calendario.alarm.TRIGGER_SHIFT_ALARM"
        const val CHANNEL_ID = "turnisync_alarm_playback_v2"
        const val NOTIFICATION_ID = 5050
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
