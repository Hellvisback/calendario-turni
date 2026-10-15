# Handoff Report — Explorer Survey 1 (Data Models, Shift Repositories & State)

## 1. Observation
1. **Modelli di Turno e Assegnazione**:
   - File `android-app/app/src/main/java/com/turni/calendario/model/ShiftModels.kt`:
     - Linee 8-13: `enum class ShiftCategory { LAVORO, RIPOSO, ASSENZA, ALTRO }`.
     - Linee 19-70: `data class ShiftDefinition(val id: String, val name: String, val shortCode: String, val startTime: String = "06:00", val endTime: String = "13:36", val isAllDay: Boolean = false, val breakMinutes: Int = 0, val breakStartTime: String? = null, val colorHex: Long = 0xFF0EA5E9, val iconKey: String = "sunny", val category: ShiftCategory = ShiftCategory.LAVORO, val workProfileId: String = "principale", val hourlyRate: Double = 0.0, val allowance: Double = 0.0)`.
     - Linee 76-92: `data class AssignedShift(val id: String, val date: LocalDate, val shiftDefId: String, val originalShiftDefId: String? = null, val isModifiedFromRotation: Boolean = false, val isFromRotation: Boolean = false, ... val isOnCallManual: Boolean? = null, val hideExShift: Boolean = false)`.
     - Linee 108-135: `ReperibilitaMode` (MANUALE, TURNO_SPECIFICO, ALTERNATA_STAGIONALE) e `ReperibilitaConfig`.
     - Linee 160-166: `data class AppPreferences(val showExShifts: Boolean = true, val weekStartMonday: Boolean = true, val timeFormat24h: Boolean = true, val showHolidays: Boolean = true, val autoBackupDaily: Boolean = true)`.
     - Linee 340-346: `data class ShiftRotation(val id: String, val name: String, val totalDays: Int, val daySequence: List<String>, val description: String = "")`.
2. **Architettura di Persistenza e State Repository**:
   - File `android-app/app/src/main/java/com/turni/calendario/repository/ShiftRepository.kt`:
     - Linee 100-117: Stato reattivo Compose: `val shiftDefinitions = mutableStateListOf<ShiftDefinition>()`, `val calendarShifts = mutableStateMapOf<LocalDate, MutableList<AssignedShift>>()`, `val dayExtraInfo = mutableStateMapOf<LocalDate, DayExtraInfo>()`, `val rotations = mutableStateListOf<ShiftRotation>()`, `var activeRotationId: String = "turno_a"`, `var defaultRotationId by mutableStateOf("turno_a")`.
     - Linee 130-151: Metodi di gestione turno predefinito: `setDefaultRotation(rotationId: String)`, `toggleDefaultRotation(rotationId: String): Boolean`, `getRotationsSortedByDefault(): List<ShiftRotation>`.
     - Linee 385-393: `getPlannedRotationShift(date: LocalDate, rotationId: String): ShiftDefinition?` basato su `cycleReferenceDate = LocalDate.of(2026, 9, 28)`.
     - Linee 975-1076: `ensureShiftDefinitionsFromParsedHours(hours: Map<String, Pair<String, String>>)` aggiorna orari M, P, N, MD senza duplicati.
     - Linee 1081-1120: `importNgeniaTurniResult(result: NgeniaParseResult, selectedTeamName: String?, makeDefault: Boolean, applyToCalendar: Boolean): String`.
     - Linee 1129-1163: `applyEmployeeShiftsToCalendar(employee: ParsedEmployee, overwrite: Boolean, alsoSetOnCall: Boolean): Int`.
   - File `android-app/app/src/main/java/com/turni/calendario/sync/GoogleDriveBackupHelper.kt`:
     - Linee 30-143: `exportDatabaseToJson(): String` serializza l'intero database in JSON con `org.json.JSONObject`.
     - Linee 168-180: `saveCloudBackupDirect(context: Context): Boolean` scrive in `context.filesDir/cloud_backups/turni_cloud_backup_latest.json`.
     - Linee 202-214: `restoreCloudBackupDirect(context: Context): RestoreResult` ripristina da `turni_cloud_backup_latest.json`.
   - File `android-app/app/src/main/java/com/turni/calendario/MainActivity.kt`:
     - Linee 12-14: `if (ShiftRepository.appPreferences.autoBackupDaily) GoogleDriveBackupHelper.saveCloudBackupDirect(applicationContext)`.
   - Ricerca di Room, DataStore o SharedPreferences:
     - `grep_search` per `SharedPreferences` in `android-app/app/src/main/java`: 0 riscontri.
     - `grep_search` per `DataStore` e `Room`: 0 riscontri.
3. **Estrazione e Struttura Dati Tabellone**:
   - File `android-app/app/src/main/java/com/turni/calendario/importer/NgeniaTurniParser.kt`:
     - Linee 13-21: `data class ParsedEmployee(val name: String, val role: String = "", val phone: String = "", val teamName: String = "", val isOnCallGeneral: Boolean = false, val dayShifts: Map<LocalDate, String> = emptyMap(), val rawShiftSequence: List<String> = emptyList())`.
     - Linee 26-33: `data class ParsedTeamRotation(val teamName: String, val rotationId: String, val totalDays: Int, val dayCodes: List<String>, val dayShiftDefIds: List<String>, val members: List<ParsedEmployee> = emptyList())`.
     - Linee 38-46: `data class NgeniaParseResult(val validityStart: LocalDate? = null, val validityEnd: LocalDate? = null, val detectedHours: Map<String, Pair<String, String>> = emptyMap(), val teams: List<ParsedTeamRotation> = emptyList(), val employees: List<ParsedEmployee> = emptyList(), val specialShifts: List<ShiftDefinition> = emptyList(), val summaryText: String = "")`.
     - Linee 751-766: `mapCodeToShiftDefId(code: String): String`.
     - Linee 772-797: `matchesEmployeeQuery(employee: ParsedEmployee, query: String): Boolean`.
4. **Verifica Operativa Suite Test**:
   - Esecuzione comando: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test` nella cartella `android-app`.
   - Esito verbatim: `BUILD SUCCESSFUL in 19s`, 43 actionable tasks up-to-date, 0 fallimenti nei test esistenti (`ShiftRepositoryTest` e `NgeniaTurniParserTest`).

## 2. Logic Chain
1. Da **Observation 2**, `ShiftRepository` gestisce il runtime state in memoria e serializza via `GoogleDriveBackupHelper` in JSON, mentre non esiste ancora un gestore di `SharedPreferences` per l'archiviazione di parametri utente sensibili come l'API Key.
2. Da **Observation 1 & 2**, le preferenze sono modellate in `AppPreferences` ma non includono ancora i campi per l'API Key di Gemini né per il nome turnista predefinito.
3. Dalle specifiche di **R1 & R4** in `ORIGINAL_REQUEST.md`, l'utente deve poter inserire e memorizzare in modo sicuro l'API Key di Google Gemini e richiamare il proprio nome per la preselezione automatica.
4. Pertanto, l'introduzione di un gestore dedicato `SecurePreferencesManager` (con fallback trasparente in-memory quando `Context == null`) consentirà il salvataggio sicuro in sandbox Android `MODE_PRIVATE` senza intaccare né rompere l'esecuzione degli unit test JUnit 4 su JVM pura (che eseguono senza `android.content.Context`).
5. Da **Observation 3**, `NgeniaParseResult` già modella in modo esaustivo: periodo di validità, legenda orari (`detectedHours`), squadre cicliche (`teams`), e lista dei lavoratori con turni individuali e reperibilità (`employees`).
6. Da **Observation 2 (righe 975-1163)**, `ShiftRepository` dispone già di tutti i metodi necessari per inserire i dati estratti nel catalogo e nel calendario (`ensureShiftDefinitionsFromParsedHours`, `saveRotation`, `setDefaultRotation`, `applyEmployeeShiftsToCalendar`).
7. Pertanto, l'implementazione del client Gemini Flash Vision non deve inventare nuovi formati dati né alterare il core engine: è sufficiente deserializzare il JSON restituito dal modello direttamente in un'istanza di `NgeniaParseResult` (o estensione equivalente), garantendo compatibilità immediata al 100% con la UI e la logica di business già esistente.

## 3. Caveats
- Non è stato testato l'ambiente di runtime su dispositivo reale con una chiave reale attiva durante questa fase di sola esplorazione read-only, come prescritto dal ruolo di Explorer.
- L'eventuale aggiunta di `androidx.security:security-crypto` per `EncryptedSharedPreferences` deve essere valutata con attenzione: le `SharedPreferences` standard in `Context.MODE_PRIVATE` offrono isolamento a livello di sandbox Linux di Android senza rischiare crash su vecchie implementazioni Keystore o incompatibilità con test JUnit 4 su JVM locale.

## 4. Conclusion
Il layer dati e i repository dell'app sono perfettamente predisposti per accogliere l'integrazione di Gemini Flash Vision:
1. I modelli `ShiftDefinition`, `ShiftRotation`, `AssignedShift`, `ParsedTeamRotation`, `ParsedEmployee` e `NgeniaParseResult` coprono integralmente tutte le entità estratte dal modello multimodale.
2. La persistenza sicura dell'API Key e del nome lavoratore può essere implementata in modo chirurgico tramite `SecurePreferencesManager` e l'estensione di `AppPreferences`.
3. Il client Gemini Flash può essere implementato sfruttando `java.net.HttpURLConnection` e `org.json` già disponibili nell'Android SDK, senza pesanti dipendenze esterne.

## 5. Verification Method
1. **Verifica dei file e delle firme**:
   - Ispezionare `android-app/app/src/main/java/com/turni/calendario/model/ShiftModels.kt` e `ShiftRepository.kt`.
2. **Esecuzione dei test unitari**:
   - Eseguire da PowerShell:
     ```powershell
     cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
     .\gradlew.bat test
     ```
   - Condizione di invalidazione: se un qualsiasi test fallisce o l'aggiunta di nuove classi causa errori di compilazione in `ShiftRepositoryTest` o `NgeniaTurniParserTest`.
3. **Verifica del report di dettaglio**:
   - Consultare `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_1\analysis.md`.
