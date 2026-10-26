package com.turni.calendario.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Bounded, stoppable playback. Does not change global volume, ringer or DND. */
class ShiftAlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != ACTION_RING) { stopSelf(); return START_NOT_STICKY }
        ensureChannel(this)
        val name = intent.getStringExtra("shift_name") ?: "Turno"
        val message = intent.getStringExtra("message") ?: "È ora di prepararti"
        val test = intent.getBooleanExtra("is_test", false)
        val open = PendingIntent.getActivity(this, 5050, Intent(this, ShiftAlarmActivity::class.java).apply {
            putExtra("shift_name", name); putExtra("message", message)
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 5051, Intent(this, ShiftAlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, ShiftAlarmReceiver.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (test) "Sveglia di prova" else "Sveglia · $name")
            .setContentText(message).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
            .setContentIntent(open).setFullScreenIntent(open, true)
            .addAction(0, "Ferma", stop).build()
        // Register foreground before audio work and the five-second deadline.
        startForeground(ShiftAlarmReceiver.NOTIFICATION_ID, notification)
        if (!ShiftAlarmScheduler.hasNotificationPermission(this)) {
            recordDelivery(this, "blocked", "Notifications disabled")
            stopSelf(); return START_NOT_STICKY
        }
        releasePlayback()
        ringing.value = true
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TurniSync:ShiftAlarm").apply { acquire(130_000L) }
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener { /* Dismissal or bounded timeout ends playback. */ }.build()
        audio.requestAudioFocus(focusRequest!!)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(this@ShiftAlarmService, sound)
                isLooping = true
                setWakeMode(this@ShiftAlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                setOnPreparedListener {
                    if (player === it) {
                        try {
                            it.start()
                            recordDelivery(this@ShiftAlarmService, "ringing", if (test) "test" else name)
                            Log.i(TAG, "Alarm playback started; test=$test; alarmVolume=${audio.getStreamVolume(AudioManager.STREAM_ALARM)}")
                        } catch (error: RuntimeException) {
                            Log.e(TAG, "Prepared alarm could not start", error)
                            recordDelivery(this@ShiftAlarmService, "audio_error", error.javaClass.simpleName)
                        }
                    }
                }
                setOnErrorListener { _, what, extra ->
                    recordDelivery(this@ShiftAlarmService, "audio_error", "$what/$extra")
                    true
                }
                prepareAsync()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Alarm sound unavailable; notification and vibration remain active", error)
            recordDelivery(this, "audio_error", error.javaClass.simpleName)
        }
        vibrator = (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.also {
            if (it.hasVibrator()) it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 250, 450, 1000), 0), attributes)
        }
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, if (test) 20_000L else 120_000L)
        return START_NOT_STICKY
    }

    private fun releasePlayback() {
        player?.let { runCatching { it.stop() }; it.release() }; player = null
        vibrator?.cancel(); vibrator = null
        focusRequest?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }; focusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        releasePlayback()
        ringing.value = false
        recordDelivery(this, "stopped", "")
        stopForeground(true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.turni.calendario.alarm.RING"
        const val ACTION_STOP = "com.turni.calendario.alarm.STOP"
        private const val TAG = "TurniSyncAlarm"
        val ringing = MutableStateFlow(false)

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(ShiftAlarmReceiver.CHANNEL_ID) == null) {
                manager.createNotificationChannel(NotificationChannel(ShiftAlarmReceiver.CHANNEL_ID, "Sveglie TurniSync", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Sveglie sonore per i turni, con azione Ferma"
                    // The service owns sound, so the channel must not add a second tone.
                    setSound(null, null); enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                })
            }
        }

        fun recordDelivery(context: Context, state: String, detail: String) {
            val now = System.currentTimeMillis()
            val editor = context.getSharedPreferences("turnisync_alarm_delivery", Context.MODE_PRIVATE).edit()
                .putString("state", state).putString("detail", detail).putLong("timestamp", now)
            if (state == "ringing") editor.putLong("last_started_at", now).putString("last_started_kind", detail)
            if (state == "audio_error" || state == "error" || state == "blocked") editor.putLong("last_error_at", now).putString("last_error", "$state: $detail")
            editor.apply()
        }
    }
}
