package com.turni.calendario.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.turni.calendario.MainActivity
import com.turni.calendario.R

class ShiftAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Ricevuto broadcast con action: $action")

        if (action == ACTION_TRIGGER_SHIFT_ALARM) {
            val shiftName = intent.getStringExtra("shift_name") ?: "Turno di Lavoro"
            val message = intent.getStringExtra("message") ?: "È ora di prepararsi per il tuo turno!"
            val isReperibilita = intent.getBooleanExtra("is_reperibilita", false)

            showAlarmNotification(context, shiftName, message, isReperibilita)

            // Pianifica automaticamente la sveglia successiva!
            ShiftAlarmScheduler.scheduleNextAlarm(context)
        } else if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d(TAG, "Dispositivo riavviato o app aggiornata: ripristino sveglia attiva")
            ShiftAlarmScheduler.scheduleNextAlarm(context)
        }
    }

    private fun showAlarmNotification(
        context: Context,
        title: String,
        message: String,
        isReperibilita: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        createNotificationChannel(context, notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isReperibilita) "📞 $title" else "⏰ SVEGLIA: $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sveglie & Avvisi Turni",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifiche e sveglie ad alto volume per turni e reperibilità"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                    setSound(soundUri, audioAttributes)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        const val TAG = "ShiftAlarmReceiver"
        const val ACTION_TRIGGER_SHIFT_ALARM = "com.turni.calendario.alarm.TRIGGER_SHIFT_ALARM"
        const val CHANNEL_ID = "turni_smart_alarms_channel"
        const val NOTIFICATION_ID = 5050
    }
}
