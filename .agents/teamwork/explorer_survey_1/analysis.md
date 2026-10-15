# Analisi Approfondita del Layer Dati, Persistenza e Integrazione Gemini Flash

**Sintesi Esecutiva**:
Il layer dati di *Calediario Turni* si fonda su un'architettura reattiva in-memory (`ShiftRepository`) gestita tramite primitive Jetpack Compose (`mutableStateListOf`, `mutableStateMapOf`), serializzata in JSON tramite `GoogleDriveBackupHelper` senza dipendenze da Room o DataStore. L'integrazione di Google Gemini Flash Vision si inserisce in modo naturale riutilizzando il modello strutturato di estrazione (`NgeniaParseResult`), estendendo la persistenza delle preferenze con un `SecurePreferencesManager` per l'API Key e il nominativo utente, e invocando direttamente i metodi collaudati di sincronizzazione orari e turni già presenti nel repository.

---

## 1. Modellazione dei Turni e Dati di Dominio

Tutti i modelli fondamentali risiedono nel file:
`android-app/app/src/main/java/com/turni/calendario/model/ShiftModels.kt`

### 1.1 `ShiftDefinition` (Definizione Modello Turno)
Definisce l'anagrafica di ciascun turno configurabile.

```kotlin
data class ShiftDefinition(
    val id: String,
    val name: String,
    val shortCode: String,
    val startTime: String = "06:00",
    val endTime: String = "13:36",
    val isAllDay: Boolean = false,
    val breakMinutes: Int = 0,
    val breakStartTime: String? = null,
    val colorHex: Long = 0xFF0EA5E9,
    val iconKey: String = "sunny",
    val category: ShiftCategory = ShiftCategory.LAVORO,
    val workProfileId: String = "principale",
    val hourlyRate: Double = 0.0,
    val allowance: Double = 0.0
)
```

**Proprietà Calcolate Esistenti**:
- `color: Color`: Istanzia un `Color(colorHex)` di Compose.
- `durationMinutes: Long`: Calcola i minuti netti tenendo conto del passaggio della mezzanotte (`end.isBefore(start)`) e sottraendo `breakMinutes`. Fallback a 456L (7h 36m, standard ferroviario CCNL Mobilità).
- `durationHoursFloat: Float`: `durationMinutes / 60f`.
- `durationFormatted: String`: es. `"7h 36m"` oppure `"Tutto il giorno"` se `isAllDay = true`.
- `timeRangeFormatted: String`: es. `"06:00 - 13:36"`.

**Categorie Turno (`ShiftCategory`)**:
- `LAVORO`: Turni con orario effettivo e conteggio ore (Mattina, Pomeriggio, Notte, Mat Dom, Giornaliero, Uffici).
- `RIPOSO`: Riposi settimanali e smonti notte (`riposo`, `smonto_notte`).
- `ASSENZA`: Ferie, malattie, donazioni sangue, permessi (`ferie`, `malattia`, `donazione`, `permesso`).
- `ALTRO`: Formazione, corsi, straordinari singoli.

**Catalogo Turni Predefiniti (Inizializzati in `ShiftRepository.kt:229-357`)**:

| ID Turno | Nome | Sigla | Orario | Colore Hex | Categoria | AllDay |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `pomeriggio` | Pomeriggio | `POM` | 14:00 - 21:36 | `0xFFEF4444` (Rosso corallo) | `LAVORO` | false |
| `mattina` | Mattina | `MAT` | 06:00 - 13:36 | `0xFF0284C7` (Azzurro avio) | `LAVORO` | false |
| `notte` | Notte | `NOT` | 22:00 - 05:36 | `0xFF22C55E` (Verde chiaro) | `LAVORO` | false |
| `mat_dom` | Mat Dom | `DOM` | 08:36 - 16:12 | `0xFFF59E0B` (Giallo ambra) | `LAVORO` | false |
| `smonto_notte`| Smonto Notte | `SMO` | 05:36 - 05:36 | `0xFF06B6D4` (Ciano) | `RIPOSO` | true |
| `riposo` | Riposo | `RIP` | 00:00 - 23:59 | `0xFFEC4899` (Fucsia) | `RIPOSO` | true |
| `ferie` | Ferie | `FER` | 00:00 - 23:59 | `0xFF2563EB` (Blu cobalto) | `ASSENZA` | true |
| `giornaliero` | Giornaliero | `GIO` | 08:30 - 17:06 | `0xFF10B981` (Verde smeraldo) | `LAVORO` | false (pausa 60m) |
| `uffici` | Uffici | `UFF` | 07:30 - 15:36 | `0xFF64748B` (Grigio ardesia) | `LAVORO` | false |
| `donazione` | Donazione | `DON` | 00:00 - 23:59 | `0xFFEAB308` (Giallo oro) | `ASSENZA` | true |
| `malattia` | Malattia | `MAL` | 00:00 - 23:59 | `0xFF84CC16` (Lime) | `ASSENZA` | true |

### 1.2 `AssignedShift` & `DayExtraInfo` (Assegnazioni sul Calendario)
```kotlin
data class AssignedShift(
    val id: String,
    val date: LocalDate,
    val shiftDefId: String,
    val originalShiftDefId: String? = null,
    val isModifiedFromRotation: Boolean = false,
    val isFromRotation: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val isOvertime: Boolean = false,
    val overtimeHours: Float = 0f,
    val isTrasferta: Boolean = false,
    val isOnCallManual: Boolean? = null,
    val hideExShift: Boolean = false
)
```
- Supporta turni multipli per singola data (`calendarShifts[date]: MutableList<AssignedShift>`).
- Gestisce la regola "Ex-Turno": se un turno proviene da una rotazione ciclica (`isFromRotation = true`) ed è sostituito, mantiene `originalShiftDefId` e imposta `isModifiedFromRotation = true`. Il badge "ex TURNO" compare solo se `isModifiedFromRotation == true` e `hideExShift == false`.

### 1.3 Reperibilità Software e Ferroviaria
La reperibilità è gestita attraverso `ReperibilitaConfig`:
- **Modalità `MANUALE`**: Reperibilità solo sulle date presenti in `manualDates: Set<LocalDate>`.
- **Modalità `TURNO_SPECIFICO`**: Reperibilità attiva nei giorni in cui il lavoratore è assegnato a turni presenti in `shiftSpecificIds` (default: `["pomeriggio"]`).
- **Modalità `ALTERNATA_STAGIONALE`**:
  - Inverno (1 Ottobre - 31 Maggio): reperibilità attiva su turno (default `pomeriggio`).
  - Estate (1 Giugno - 30 Settembre): ciclo a rotazione settimanale calcolato tramite formula modulare a partire da `summerCycleReferenceDate` (es. 1 settimana reperibile ogni 3 di lavoro).
- **Precedenza assoluta dell'override giornaliero**: `dayExtraInfo[date]?.isOnCallManual` ha priorità massima su qualsiasi modalità globale (`true` forza la reperibilità, `false` la disattiva).

---

## 2. Esame di `ShiftRepository` e Persistenza dello Stato

File: `android-app/app/src/main/java/com/turni/calendario/repository/ShiftRepository.kt`

### 2.1 Architettura dello Stato In-Memory
`ShiftRepository` è un `object` singleton di Kotlin.
Non utilizza Room né Jetpack DataStore né librerie ORM esterne.
Lo stato reattivo primario è gestito interamente tramite primitive Compose:
```kotlin
val shiftDefinitions = mutableStateListOf<ShiftDefinition>()
val calendarShifts = mutableStateMapOf<LocalDate, MutableList<AssignedShift>>()
val dayExtraInfo = mutableStateMapOf<LocalDate, DayExtraInfo>()
val workProfiles = mutableStateListOf<WorkProfile>(...)
var activeWorkProfileId by mutableStateOf("principale")
val rotations = mutableStateListOf<ShiftRotation>()
var activeRotationId: String = "turno_a"
var defaultRotationId by mutableStateOf("turno_a")
var reperibilitaConfig by mutableStateOf(ReperibilitaConfig())
var smartAlarmsConfig by mutableStateOf(SmartAlarmsConfig())
var appPreferences by mutableStateOf(AppPreferences())
var enabledHolidayIds by mutableStateOf(...)
var googleAccountProfile by mutableStateOf<GoogleAccountProfile?>(...)
```

### 2.2 Persistenza su File JSON (`GoogleDriveBackupHelper.kt`)
La persistenza permanente dello stato avviene tramite serializzazione JSON con le librerie native `org.json.JSONObject` e `org.json.JSONArray` (senza dipendenze esterne come Gson o Moshi).

- **Backup locale cloud storage**:
  `GoogleDriveBackupHelper.saveCloudBackupDirect(context: Context)`
  Scrive il database formattato in:
  `context.filesDir/cloud_backups/turni_cloud_backup_latest.json`
- **Ripristino locale**:
  `GoogleDriveBackupHelper.restoreCloudBackupDirect(context: Context): RestoreResult`
  Legge il file e popola nuovamente le liste e mappe di `ShiftRepository`.
- **Condivisione & SAF**:
  `writeBackupToUri` e `restoreDatabaseFromUri` consentono all'utente di esportare/importare verso Google Drive o file system esterno tramite `FileProvider` e SAF (`Intent.ACTION_SEND` / `OpenDocument`).
- **Autosalvataggio giornaliero**:
  In `MainActivity.onCreate()` (righe 12-14), se `autoBackupDaily == true`, viene eseguito automaticamente `saveCloudBackupDirect(applicationContext)`.

### 2.3 Undo / Cronologia Modifiche
`ShiftRepository` dispone di uno stack `undoHistory` interno a 40 livelli (`mutableListOf<Map<LocalDate, List<AssignedShift>>>()`), gestito tramite `saveSnapshot()` e `undo()`.

---

## 3. Gestione dei Team e Squadre (`ShiftRotation`)

### 3.1 Struttura del Modello `ShiftRotation`
```kotlin
data class ShiftRotation(
    val id: String,
    val name: String,
    val totalDays: Int,
    val daySequence: List<String>, // Lista ordinata di shiftDefId
    val description: String = ""
)
```

### 3.2 Calcolo Matematico del Ciclo
Il calcolo del turno assegnato per qualsiasi data futura o passata avviene dinamicamente in `ShiftRepository.getPlannedRotationShift(date, rotationId)`:
- `cycleReferenceDate: LocalDate = LocalDate.of(2026, 9, 28)` (Lunedì inizio ciclo).
- Formula:
  $$\text{daysDiff} = \text{ChronoUnit.DAYS.between(cycleReferenceDate, date)}$$
  $$\text{cycleIndex} = \text{Math.floorMod}(\text{daysDiff}, \text{rot.daySequence.size})$$
  $$\text{defId} = \text{rot.daySequence}[\text{cycleIndex}]$$
- Questa formula garantisce il supporto nativo a rotazioni di qualsiasi lunghezza (7, 14, 21, 28, 35 o $N$ giorni).

### 3.3 Gestione Turno Predefinito (Toggle & Ordinamento)
- `defaultRotationId`: memorizza l'ID della rotazione predefinita dell'utente.
- `setDefaultRotation(rotationId: String)`: imposta la rotazione predefinita e la attiva contemporaneamente.
- `toggleDefaultRotation(rotationId: String): Boolean`: implementa il toggle a due stati. Se la rotazione cliccata è già predefinita, reimposta `defaultRotationId = ""` (stato neutro) e restituisce `false`; altrimenti la imposta e restituisce `true`.
- `getRotationsSortedByDefault(): List<ShiftRotation>`: ordina le rotazioni posizionando la rotazione con `id == defaultRotationId` al primo posto dell'elenco.

### 3.4 Metodi di Inserimento Esistenti in `ShiftRepository`
1. `saveRotation(updated: ShiftRotation)`: registra o aggiorna una rotazione nel catalogo `rotations`.
2. `applyRotation(rotationId: String, startDate: LocalDate, daysCount: Int = 90)`: genera e scrive in `calendarShifts` le assegnazioni giorno per giorno.
3. `ensureShiftDefinitionsFromParsedHours(hours: Map<String, Pair<String, String>>)`:
   - Aggiorna gli orari dei turni esistenti (M, P, N, MD, TGF) **senza creare duplicati**.
   - Se un turno è inedito, ne crea la definizione con colori e categorie coerenti.
4. `applyEmployeeShiftsToCalendar(employee: ParsedEmployee, overwrite: Boolean, alsoSetOnCall: Boolean): Int`:
   - Applica direttamente i turni specifici estratti per il singolo turnista nel calendario.
   - Attiva opzionalmente la reperibilità giornaliera (`setDayOnCallManual(date, true)`).

---

## 4. Memorizzazione Sicura di Gemini API Key e Preferenze Utente

### 4.1 Requisiti di Sicurezza & Ergonomia (R1 & R4)
- L'utente deve poter inserire la propria API Key di Google Gemini al primo utilizzo della schermata di importazione.
- La chiave deve essere memorizzata in modo persistente e protetto su disco, mascherata visivamente (`••••••••••••`), con possibilità di modifica/cancellazione immediata.
- Il nome/cognome del lavoratore preferito (es. "Marco Graziani" o "Graziani") deve essere salvato nelle preferenze per consentire il pre-filtraggio immediato a monte alla prima apertura della schermata.

### 4.2 Analisi Architetturale delle Opzioni di Storage Android

| Meccanismo | Valutazione Tecnica | Vantaggi | Svantaggi / Rischi |
| :--- | :--- | :--- | :--- |
| **`EncryptedSharedPreferences`** (`androidx.security:security-crypto`) | Buona, ma richiede aggiunta di dipendenza Maven (`security-crypto:1.1.0-alpha06`) | Cifratura hardware KeyStore AES-256-GCM | Può fallire o lanciare eccezioni su ROM custom (MIUI/ColorOS vecchie) o in unit test JUnit 4 puri senza Robolectric |
| **`SharedPreferences` standard (`MODE_PRIVATE`)** | **Consigliata con offuscamento / protezione locale** | Nessuna dipendenza esterna, sandbox Linux di Android protegge il file da altre app non-root | In chiaro su dispositivi rooted se non offuscata |
| **`SecurePreferencesManager` ibrido** | **Architettura Ottimale Raccomandata** | Separazione netta: `SharedPreferences` in produzione Android, fallback in-memory per Unit Test | Zero impatto sui test esistenti, testabilità al 100% |

### 4.3 Proposta di Implementazione: `AppPreferences` & `SecurePreferencesManager`

1. **Estensione del modello dati `AppPreferences`** in `ShiftModels.kt`:
```kotlin
data class AppPreferences(
    val showExShifts: Boolean = true,
    val weekStartMonday: Boolean = true,
    val timeFormat24h: Boolean = true,
    val showHolidays: Boolean = true,
    val autoBackupDaily: Boolean = true,
    val geminiApiKey: String = "",
    val defaultWorkerName: String = "Graziani Marco"
)
```

2. **Nuovo modulo `SecurePreferencesManager`** in `android-app/app/src/main/java/com/turni/calendario/util/SecurePreferencesManager.kt` (o nel repository):
```kotlin
package com.turni.calendario.util

import android.content.Context
import android.content.SharedPreferences

object SecurePreferencesManager {
    private const val PREFS_NAME = "calediario_secure_settings"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_DEFAULT_WORKER_NAME = "default_worker_name"

    fun getGeminiApiKey(context: Context?): String {
        if (context == null) return ""
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun saveGeminiApiKey(context: Context?, apiKey: String) {
        if (context == null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getDefaultWorkerName(context: Context?): String {
        if (context == null) return "Marco Graziani"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_WORKER_NAME, "Marco Graziani") ?: "Marco Graziani"
    }

    fun saveDefaultWorkerName(context: Context?, name: String) {
        if (context == null) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DEFAULT_WORKER_NAME, name.trim()).apply()
    }
}
```

3. **Integrazione in `GoogleDriveBackupHelper.kt`**:
Aggiungere i campi nel salvataggio/ripristino JSON di `appPreferences` per sincronizzare la chiave e il nominativo anche nei backup personali.

---

## 5. File Esistenti, Firme dei Metodi e Piano di Integrazione per Gemini Flash

### 5.1 Tabella dei File e Firme Chiave

| File | Percorso | Componente / Firma Chiave | Ruolo Attuale |
| :--- | :--- | :--- | :--- |
| `ShiftModels.kt` | `android-app/app/src/main/java/com/turni/calendario/model/` | `data class ShiftDefinition(...)`<br>`data class ShiftRotation(...)`<br>`data class AssignedShift(...)` | Modelli di dominio |
| `ShiftRepository.kt` | `android-app/app/src/main/java/com/turni/calendario/repository/` | `ensureShiftDefinitionsFromParsedHours(hours: Map<String, Pair<String, String>>)`<br>`saveRotation(updated: ShiftRotation)`<br>`applyRotation(...)`<br>`applyEmployeeShiftsToCalendar(...)` | Single Source of Truth & Business Logic |
| `NgeniaTurniParser.kt` | `android-app/app/src/main/java/com/turni/calendario/importer/` | `data class NgeniaParseResult(...)`<br>`data class ParsedTeamRotation(...)`<br>`data class ParsedEmployee(...)`<br>`mapCodeToShiftDefId(code: String): String`<br>`matchesEmployeeQuery(...)` | Modelli e logica di estrazione tabelloni |
| `GoogleDriveBackupHelper.kt` | `android-app/app/src/main/java/com/turni/calendario/sync/` | `exportDatabaseToJson(): String`<br>`restoreFromJsonString(jsonStr: String): RestoreResult` | Serializzazione JSON e persistenza file |
| `ImportShiftsScreen.kt` | `android-app/app/src/main/java/com/turni/calendario/ui/screens/` | `fun ImportShiftsScreen(onDismiss, onNavigateToEdit)`<br>`ElasticStarButton(...)` | UI importazione, anteprima e salvataggio |

### 5.2 Specifiche Tecniche del Client Multimodale Gemini Flash

- **Modello Consigliato**: `gemini-1.5-flash` o `gemini-2.0-flash`.
- **Endpoint REST**:
  `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={API_KEY}`
- **Protocollo di rete**: Implementazione pura tramite `java.net.HttpURLConnection` nativa di Android (zero dipendenze aggiuntive da compilare, compatibile al 100% con `minSdk 26`).
- **Payload Request**:
```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "Analizza questo documento o prospetto turni tabellare (ferroviario, ospedaliero, industriale o generico). Estrai in formato JSON strutturato:\n1. Date di validità inizio e fine.\n2. Orari e legenda codici turno (es. M 06:00-13:36, P 14:00-21:36, N 22:00-05:36, MD 08:36-16:12).\n3. Tutte le squadre rilevate con la sequenza ciclica di codici turno.\n4. Elenco di tutti i lavoratori con nome, ruolo, telefono/matricola, squadra di appartenenza, flag reperibilità e mappa dei turni data per data.\n5. Eventuali regole o note di reperibilità."
        },
        {
          "inline_data": {
            "mime_type": "image/jpeg",
            "data": "<BASE64_IMAGE_STRING>"
          }
        }
      ]
    }
  ],
  "generationConfig": {
    "response_mime_type": "application/json",
    "temperature": 0.1
  }
}
```

### 5.3 Pipeline di Conversione PDF tramite `android.graphics.pdf.PdfRenderer`
Per gestire file PDF (come il MOD. P.R. ferroviario o prospetti ospedalieri):
1. Copiare il flusso URI in un file temporaneo nella cache dell'app (`context.cacheDir`).
2. Aprire `ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)`.
3. Inizializzare `PdfRenderer(fileDescriptor)`.
4. Renderizzare la pagina (o le pagine) su un `Bitmap` ad alta risoluzione (scala $2.0\times$ o $1920\times 1080$ per leggibilità ottimale dei testi piccoli).
5. Comprimere il `Bitmap` in JPEG a qualità 85% su `ByteArrayOutputStream`.
6. Codificare con `android.util.Base64.encodeToString(bytes, Base64.NO_WRAP)`.

### 5.4 Mapping Diretto tra Output JSON di Gemini e `NgeniaParseResult`
Il JSON restituito da Gemini Flash viene deserializzato con `org.json.JSONObject` e convertito direttamente nell'esistente `NgeniaParseResult`:
- `detectedHours`: `hours.forEach { map[code] = Pair(start, end) }`
- `teams`: per ogni squadra crea `ParsedTeamRotation(teamName, rotationId, totalDays, dayCodes, dayShiftDefIds)` mappando ogni codice tramite `NgeniaTurniParser.mapCodeToShiftDefId(code)`.
- `employees`: per ogni lavoratore crea `ParsedEmployee(name, role, phone, teamName, isOnCallGeneral, dayShifts, rawShiftSequence)`.

**Vantaggio Architetturale Chiave**:
Poiché `NgeniaParseResult` è già la struttura dati nativa utilizzata da `ImportShiftsScreen`, una volta popolata tramite Gemini Flash:
1. Le sezioni UI di anteprima turnista a monte, stellina elastica, salvataggio nel catalogo e modifica funzionano immediatamente senza riscrivere l'intera schermata.
2. I metodi collaudati `ShiftRepository.ensureShiftDefinitionsFromParsedHours` e `ShiftRepository.applyEmployeeShiftsToCalendar` possono essere chiamati direttamente.

### 5.5 Gestione Robusta degli Errori e Casi Limite
- **HTTP 400**: Richiesta malformata o API Key priva del formato corretto $\rightarrow$ Popup: *"Chiave API non valida. Verifica la stringa inserita."*
- **HTTP 403**: Permesso negato o Generative Language API disabilitata sul progetto Google Cloud $\rightarrow$ Popup: *"Accesso negato: assicurati che la tua API Key abbia accesso al modello Gemini Flash."*
- **HTTP 429**: Quota superata $\rightarrow$ Popup: *"Quota richieste Gemini superata. Riprova tra qualche istante."*
- **Timeout / Nessuna connessione**: `java.net.SocketTimeoutException` o `UnknownHostException` $\rightarrow$ Popup: *"Impossibile contattare i server Google Gemini. Controlla la connessione internet."*
- **JSON non strutturato**: Gestione fallback tramite `NgeniaTurniParser.parseDocumentText(rawText)` nel caso in cui il modello restituisca testo esplicativo insieme al JSON.

---

## 6. Conclusioni per la Fase di Implementazione
1. **Zero modifiche strutturali al motore di calcolo**: `ShiftRepository` dispone già di tutti i metodi atomici necessari (`ensureShiftDefinitionsFromParsedHours`, `saveRotation`, `setDefaultRotation`, `applyEmployeeShiftsToCalendar`).
2. **Nessuna dipendenza pesante aggiunta**: L'interazione con Gemini Flash può essere implementata in modo snello e robusto usando `HttpURLConnection` e `org.json`.
3. **Piena compatibilità con i test**: Tutte le estensioni mantengono intatta la suite dei 31 unit test esistenti in `app/src/test/`.
