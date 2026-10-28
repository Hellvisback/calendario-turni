package com.turni.calendario

import com.turni.calendario.sync.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import org.json.JSONObject

class BackupSafetyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun reinstallDoesNotInheritPermissionToUploadFromRestoredPreferences() {
        val first = temporary.newFolder("first-no-backup")
        val second = temporary.newFolder("reinstalled-no-backup")
        assertFalse(BackupInstallationApproval.contains(first, "a@example.org"))
        BackupInstallationApproval.approve(first, "A@example.org")
        assertTrue(BackupInstallationApproval.contains(first, "a@example.org"))
        assertFalse(BackupInstallationApproval.contains(second, "a@example.org"))
        assertFalse(BackupInstallationApproval.contains(first, "b@example.org"))
        assertFalse(BackupInstallationApproval.contains(first, null))
        BackupInstallationApproval.approve(first, "a@example.org")
        assertEquals(1,first.listFiles()!!.size)
    }

    @Test fun corruptApprovalCannotAuthorizeAutomaticUploads() {
        val root=temporary.newFolder()
        BackupInstallationApproval.approve(root,"a@example.org")
        root.listFiles()!!.single().writeText("broken")
        assertFalse(BackupInstallationApproval.contains(root,"a@example.org"))
    }

    @Test fun localBackupPreservesLegacyAndCompleteCopiesEvenAfterAnEmptySave() {
        val folder=temporary.newFolder()
        val latest=File(folder,"turni_cloud_backup_latest.json")
        val legacy="{\"calendarShifts\":[\"old\"]}"
        val complete="{\"calendarShifts\":[\"old\",\"new\"]}"
        val empty="{\"calendarShifts\":[]}"
        latest.writeText(legacy)
        LocalBackupArchive.save(folder,complete)
        LocalBackupArchive.save(folder,empty)
        assertEquals(empty,latest.readText())
        assertEquals(setOf(legacy,complete,empty),LocalBackupArchive.copies(folder).map{it.readText()}.toSet())
        LocalBackupArchive.save(folder,empty)
        assertEquals(3,LocalBackupArchive.copies(folder).size)
    }

    @Test fun aNewEmptyRemoteSnapshotNeverUpdatesOrDeletesTheExistingCopy() {
        val remote=linkedMapOf("old-copy" to "original user data")
        val methods=mutableListOf<String>()
        fun send(url:String,method:String,body:ByteArray,type:String):String {
            methods+=method
            assertTrue(url.startsWith("https://www.googleapis.com/upload/drive/v3/files?"))
            assertTrue(type.startsWith("multipart/related"))
            val id="copy-${remote.size}"
            remote[id]=body.toString(Charsets.UTF_8)
            return JSONObject().put("id",id).put("modifiedTime","2026-10-27T12:00:00Z").toString()
        }
        GoogleDriveRemote.createSnapshot("{\"calendarShifts\":[1]}",::send)
        GoogleDriveRemote.createSnapshot("{\"calendarShifts\":[]}",::send)
        assertEquals(listOf("POST","POST"),methods)
        assertEquals("original user data",remote["old-copy"])
        assertEquals(3,remote.size)
        assertThrows(java.io.IOException::class.java){
            GoogleDriveRemote.createSnapshot("{\"calendarShifts\":[]}"){_,_,_,_->throw java.io.IOException("offline")}
        }
        assertEquals("original user data",remote["old-copy"])
        assertEquals(3,remote.size)
    }

    @Test fun legacyLocalCopyIsRestorableBeforeFirstVersionedSave() {
        val folder=temporary.newFolder()
        val latest=File(folder,"turni_cloud_backup_latest.json").apply{writeText("legacy")}
        assertEquals(listOf(latest),LocalBackupArchive.copies(folder))
    }

    @Test fun cooldownLastsThreeMinutesAndCompletionRestartsTheFullPropagationWindow() {
        val start=10_000L
        val savedDeadline=CalendarCooldownPolicy.deadline(start)
        assertEquals(180_000L,CalendarCooldownPolicy.remaining(savedDeadline,start))
        assertEquals(1L,CalendarCooldownPolicy.remaining(savedDeadline,start+179_999))
        assertEquals(0L,CalendarCooldownPolicy.remaining(savedDeadline,start+180_000))
        assertEquals(0L,CalendarCooldownPolicy.remaining(savedDeadline,start+180_001))
        val finished=CalendarCooldownPolicy.deadline(start+30_000)
        assertEquals(180_000L,CalendarCooldownPolicy.remaining(finished,start+30_000))
        assertEquals("03:00",CalendarCooldownPolicy.label(180_000))
        assertEquals("00:01",CalendarCooldownPolicy.label(1))
        assertEquals("00:00",CalendarCooldownPolicy.label(0))
    }
}
