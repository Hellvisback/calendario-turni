package com.turni.calendario
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.sync.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class ThemeBackupTest {
 @Test fun chosenThemeSurvivesBackupAndRestore() {
  val snapshot=DatabaseSnapshot.capture().copy(shifts=emptyMap(),extras=emptyMap())
  for(mode in listOf("AUTOMATIC","LIGHT","DARK")) {
   val expected=snapshot.copy(preferences=snapshot.preferences.copy(themeMode=mode))
   assertEquals(expected,DatabaseRestore.parse(GoogleDriveBackupHelper.exportDatabaseToJson(expected)))
  }
 }
 @Test fun legacyBackupDoesNotInventAnExplicitTheme() {
  val json=JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson())
  json.getJSONObject("appPreferences").remove("themeMode")
  assertNull(DatabaseRestore.parse(json.toString()).preferences.themeMode)
 }
 @Test fun unknownBackupThemeIsRejectedBeforeMutation() {
  val before=DatabaseSnapshot.capture()
  val json=JSONObject(GoogleDriveBackupHelper.exportDatabaseToJson(before))
  json.getJSONObject("appPreferences").put("themeMode","invalid")
  assertThrows(IllegalArgumentException::class.java){DatabaseRestore.parse(json.toString())}
  assertEquals(before,DatabaseSnapshot.capture())
 }
}
