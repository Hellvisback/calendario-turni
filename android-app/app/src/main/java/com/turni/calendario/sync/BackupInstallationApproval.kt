package com.turni.calendario.sync

import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Pass noBackupFilesDir: a restored Android preference must never authorize a fresh installation. */
object BackupInstallationApproval {
    private fun marker(root: File, email: String): File {
        require(email.isNotBlank()) { "Account Google non disponibile." }
        val key = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase(Locale.ROOT).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, "drive-reviewed-$key")
    }
    @Synchronized fun contains(root: File, email: String?): Boolean = !email.isNullOrBlank() &&
        runCatching { marker(root, email).readText() == "reviewed-v1" }.getOrDefault(false)

    @Synchronized fun approve(root: File, email: String) {
        if (contains(root, email)) return
        check(root.exists() || root.mkdirs()) { "Impossibile salvare la scelta del backup." }
        val target = marker(root, email)
        val temp = File(root, "${target.name}.tmp")
        temp.outputStream().use { it.write("reviewed-v1".toByteArray()); it.fd.sync() }
        check(temp.renameTo(target)) { "Impossibile confermare la scelta del backup." }
    }
}
