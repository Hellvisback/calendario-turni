package com.turni.calendario.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Keep immutable local copies before updating the legacy latest-file pointer. */
object LocalBackupArchive {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Synchronized fun save(folder: File, json: String) {
        check(folder.exists() || folder.mkdirs()) { "Impossibile creare la cartella delle copie locali." }
        val latest = File(folder, "turni_cloud_backup_latest.json")
        val bytes = json.toByteArray(Charsets.UTF_8)
        val previous = latest.takeIf { it.isFile }?.readBytes()
        if (previous?.contentEquals(bytes) == true) return
        val versions = File(folder, "versions").apply { check(exists() || mkdirs()) }
        fun keep(data: ByteArray, time: Long) {
            val copy = File(versions, digest(data) + ".json")
            if (!copy.exists()) {
                val temp = File(versions, copy.name + ".tmp")
                temp.outputStream().use { it.write(data); it.fd.sync() }
                check(temp.renameTo(copy)) { "Impossibile conservare la copia precedente." }
                copy.setLastModified(time)
            }
        }
        if (previous != null) keep(previous, latest.lastModified())
        keep(bytes, System.currentTimeMillis())
        val temp = File(folder, "latest.tmp")
        temp.outputStream().use { it.write(bytes); it.fd.sync() }
        Files.move(temp.toPath(), latest.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized fun copies(folder: File): List<File> {
        val versions = File(folder, "versions").listFiles().orEmpty().filter { it.isFile && it.extension == "json" }
        return if (versions.isNotEmpty()) versions.sortedByDescending { it.lastModified() }
        else listOf(File(folder, "turni_cloud_backup_latest.json")).filter { it.isFile }
    }
}
