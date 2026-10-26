package com.turni.calendario.sync

import android.accounts.Account
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Tasks
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.LocalDatabaseStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

data class BackupScheduleStatus(
    val enabled: Boolean = false,
    val intervalHours: Int = 24,
    val accountEmail: String? = null,
    val lastSuccessAt: Long = 0,
    val lastError: String? = null,
    val needsConsent: Boolean = false,
    val running: Boolean = false
)

object DriveBackupPolicy {
    val intervalsHours = listOf(6, 12, 24, 168)
    fun interval(hours: Int) = hours.takeIf { it in intervalsHours } ?: 24
    fun sameAccount(expected: String?, actual: String?) =
        !expected.isNullOrBlank() && expected.equals(actual, true)
    fun retryable(code: Int) = code == 0 || code == 429 || code in 500..599
    fun retry(attempts: Int, allowed: Boolean) = allowed && attempts in 1..3
    fun canRecordSuccess(uploadAccount: String?, verified: Boolean, signedInAccount: String?,
                         automaticEnabled: Boolean, scheduledAccount: String?): Boolean =
        verified && sameAccount(uploadAccount, signedInAccount) &&
            (!automaticEnabled || sameAccount(uploadAccount, scheduledAccount))
    fun retryableFailure(error: Throwable): Boolean = when (error) {
        is GoogleApiException -> retryable(error.code)
        is java.io.IOException, is java.util.concurrent.TimeoutException -> true
        else -> false
    }
}

/** Native persisted jobs run with a network connection. Android chooses the exact execution time. */
object DriveAutomaticBackup {
    private const val PREFS = "turnisync_drive_automatic_v1"
    private const val PERIODIC = 784101
    private const val IMMEDIATE = 784102
    val intervalsHours = DriveBackupPolicy.intervalsHours
    private val mutableStatus = MutableStateFlow(BackupScheduleStatus())
    val changes: StateFlow<BackupScheduleStatus> = mutableStatus
    val statusFlow: StateFlow<BackupScheduleStatus> = changes
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)

    fun status(context: Context): BackupScheduleStatus {
        val p = prefs(context)
        return BackupScheduleStatus(p.getBoolean("enabled", false), DriveBackupPolicy.interval(p.getInt("interval", 24)),
            p.getString("account", null), p.getLong("last_success", 0), p.getString("error", null),
            p.getBoolean("consent", false), mutableStatus.value.running)
    }
    fun refresh(context: Context) { mutableStatus.value = status(context) }

    fun configure(context: Context, enabled: Boolean, intervalHours: Int = 24, accountEmail: String? = null) {
        require(!enabled || !accountEmail.isNullOrBlank()) { "Collega prima il tuo account Google." }
        val previous = status(context)
        scheduler(context).cancel(PERIODIC); scheduler(context).cancel(IMMEDIATE)
        DriveBackupJobService.cancelActive()
        val edit = prefs(context).edit().putBoolean("enabled", enabled)
            .putInt("interval", DriveBackupPolicy.interval(intervalHours))
        if (enabled) {
            edit.putString("account", accountEmail)
            if (!DriveBackupPolicy.sameAccount(accountEmail, previous.accountEmail))
                edit.remove("last_success").remove("error").remove("consent").remove("failures")
        }
        edit.commit()
        if (enabled) schedule(context)
        refresh(context)
    }
    fun disable(context: Context) {
        scheduler(context).cancel(PERIODIC); scheduler(context).cancel(IMMEDIATE)
        DriveBackupJobService.cancelActive()
        prefs(context).edit().clear().commit()
        mutableStatus.value = BackupScheduleStatus()
    }
    fun ensureScheduled(context: Context) {
        if (status(context).enabled && scheduler(context).getPendingJob(PERIODIC) == null) schedule(context)
        refresh(context)
    }
    fun runNow(context: Context): Boolean {
        val current = status(context)
        if (!allowed(context, current.accountEmail)) return false
        val success = scheduler(context).schedule(builder(context, IMMEDIATE).setMinimumLatency(0).build()) == JobScheduler.RESULT_SUCCESS
        if (!success) failure(context, current.accountEmail, "Android non ha potuto programmare il backup. Riprova.", false)
        return success
    }
    private fun builder(context: Context, id: Int) = JobInfo.Builder(id, ComponentName(context, DriveBackupJobService::class.java))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true)
        .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobInfo.BACKOFF_POLICY_LINEAR)
    private fun schedule(context: Context) {
        val s = status(context)
        val flex = if (s.intervalHours >= 24) 6L else 1L
        val job = builder(context, PERIODIC).setPersisted(true)
            .setPeriodic(TimeUnit.HOURS.toMillis(s.intervalHours.toLong()), TimeUnit.HOURS.toMillis(flex)).build()
        if (scheduler(context).schedule(job) != JobScheduler.RESULT_SUCCESS)
            failure(context, s.accountEmail, "Android non ha potuto programmare il backup automatico.", false)
    }
    internal fun allowed(context: Context, email: String?): Boolean {
        val s = status(context)
        val identity = context.getSharedPreferences("turnisync_google_identity_v1", Context.MODE_PRIVATE)
        return s.enabled && DriveBackupPolicy.sameAccount(email, s.accountEmail) &&
            identity.getBoolean("verified", false) && DriveBackupPolicy.sameAccount(email, identity.getString("email", null))
    }
    internal fun started(context: Context) { mutableStatus.value = status(context).copy(running = true) }
    internal fun stopped(context: Context) { mutableStatus.value = status(context).copy(running = false) }
    fun recordManualSuccess(context: Context, accountEmail: String, modifiedTime: String = "") {
        val state = status(context)
        val identity = context.getSharedPreferences("turnisync_google_identity_v1", Context.MODE_PRIVATE)
        if (!DriveBackupPolicy.canRecordSuccess(accountEmail, identity.getBoolean("verified", false),
                identity.getString("email", null), state.enabled, state.accountEmail)) return
        // A manual backup records its account/history without opting the user into periodic uploads.
        prefs(context).edit().putString("account", accountEmail)
            .putLong("last_success", System.currentTimeMillis()).putString("remote_modified", modifiedTime)
            .remove("error").putBoolean("consent", false).putInt("failures", 0).apply()
        mutableStatus.value = status(context).copy(running = false)
    }
    internal fun failure(context: Context, email: String?, message: String, consent: Boolean): Int {
        if (!DriveBackupPolicy.sameAccount(email, status(context).accountEmail)) return 4
        val failures = prefs(context).getInt("failures", 0) + 1
        prefs(context).edit().putString("error", message).putBoolean("consent", consent).putInt("failures", failures).apply()
        mutableStatus.value = status(context).copy(running = false)
        return failures
    }
}

class DriveBackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    companion object {
        private var active: Job? = null
        fun cancelActive() { active?.cancel(); active = null }
    }
    override fun onStartJob(parameters: JobParameters): Boolean {
        if (active?.isActive == true) return false
        val email = DriveAutomaticBackup.status(this).accountEmail
        if (email == null || !DriveAutomaticBackup.allowed(this, email)) return false
        active = scope.launch {
            var retry = false
            try {
                check(LocalDatabaseStore.loadError == null) { "Dati locali non leggibili: backup sospeso." }
                // Application loaded storage before this service; capture Compose state on main only.
                val snapshot = DatabaseSnapshot.capture()
                DriveAutomaticBackup.started(this@DriveBackupJobService)
                val remote = withContext(Dispatchers.IO) {
                    val scopes = listOf("openid", "https://www.googleapis.com/auth/userinfo.email",
                        "https://www.googleapis.com/auth/userinfo.profile", "https://www.googleapis.com/auth/drive.appdata")
                    val request = AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
                        .setRequestedScopes(scopes.map { Scope(it) }).build()
                    val result = Tasks.await(Identity.getAuthorizationClient(this@DriveBackupJobService).authorize(request), 25, TimeUnit.SECONDS)
                    val token = result.accessToken
                    if (result.hasResolution() || token.isNullOrBlank() || !result.grantedScopes.containsAll(scopes)) throw ConsentRequired()
                    ensureActive()
                    if (!DriveAutomaticBackup.allowed(this@DriveBackupJobService, email)) throw CancellationException()
                    val json = GoogleDriveBackupHelper.exportDatabaseToJson(snapshot)
                    DatabaseRestore.parse(json, snapshot)
                    require(json.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024) { "Backup troppo grande." }
                    GoogleDriveRemote.upload(token, json)
                }
                if (DriveAutomaticBackup.allowed(this@DriveBackupJobService, email))
                    DriveAutomaticBackup.recordManualSuccess(this@DriveBackupJobService, email, remote.modifiedTime)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (consent: ConsentRequired) {
                DriveAutomaticBackup.failure(this@DriveBackupJobService, email, "Apri Backup e autorizza Google Drive per il salvataggio automatico.", true)
            } catch (error: Exception) {
                val cause = (error as? java.util.concurrent.ExecutionException)?.cause ?: error
                val api = cause as? GoogleApiException
                val auth = cause as? ApiException
                val consent = api?.code == 401 || api?.code == 403 || auth?.statusCode in listOf(4, 6)
                val message = if (consent) "Autorizza di nuovo Google Drive dalla schermata Backup."
                    else if (auth?.statusCode == 10) "Configurazione Google non valida per questo APK. Verifica il client Android."
                    else api?.message ?: "Backup non riuscito. Verifica rete, account Google e dati locali."
                val failures = DriveAutomaticBackup.failure(this@DriveBackupJobService, email, message, consent)
                retry = DriveBackupPolicy.retry(failures, DriveBackupPolicy.retryableFailure(cause)) && !consent
            } finally {
                DriveAutomaticBackup.stopped(this@DriveBackupJobService)
                if (isActive) jobFinished(parameters, retry)
            }
        }
        return true
    }
    override fun onStopJob(parameters: JobParameters): Boolean {
        cancelActive(); DriveAutomaticBackup.stopped(this)
        return DriveAutomaticBackup.status(this).enabled
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private class ConsentRequired : Exception()
}
