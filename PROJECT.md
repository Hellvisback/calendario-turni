# Project: Calediario Turni — Sistema Intelligente Importazione Turni con Google Gemini Flash Vision

## Architecture
Il sistema aggiunge un flusso completo di acquisizione, estrazione multimodale e integrazione a catalogo dei turni a partire da screenshot, fotografie o documenti PDF:
1. **Network & Security Layer**:
   - `android.permission.INTERNET` in `AndroidManifest.xml`.
   - `SecurePreferencesManager`: salvataggio crittografato/privato in `Context.MODE_PRIVATE` di Google Gemini API Key e nome turnista predefinito ("Marco Graziani"), con in-memory fallback per JVM test suite.
   - `GeminiApiClient`: client REST leggero su `java.net.HttpsURLConnection` e `org.json` verso `v1beta/models/gemini-1.5-flash:generateContent`, con structured output JSON schema (`response_mime_type: "application/json"`), timeout e gestione errori HTTP (400, 403, 429, assenza rete).
2. **Document & Vision Pre-Processing Layer**:
   - `DocumentProcessor`: gestione di immagini (JPEG, PNG, WEBP) e file PDF.
   - Per i PDF: rendering ad alta risoluzione (scala 2.0x, canvas bianco) della prima pagina tramite `android.graphics.pdf.PdfRenderer` con gestione di URI SAF tramite file temporaneo in cache.
   - Ridimensionamento proporzionale delle immagini (max 2048px) e compressione JPEG quality 85.
   - Codifica Base64 con `java.util.Base64` compatibile Android SDK 26+ e JVM test suite.
3. **Domain & Data Mapping Layer**:
   - `GeminiShiftData` / `GeminiShiftParser`: parsing del JSON strutturato restituito da Gemini Flash.
   - Mappatura su `ShiftDefinition` (shortCode, orari inizio/fine, colore, categoria, reperibilità) e inserimento nel catalogo `ShiftRepository.shiftDefinitions`.
   - Mappatura squadre e rotazioni cicliche in `ShiftRotation`.
   - Riconoscimento automatico dell'utente tramite corrispondenza nome turnista -> pre-selezione della squadra predefinita con toggle a 2 stati (`defaultRotationId`).
   - Applicazione diretta al calendario tramite `ShiftRepository.applyEmployeeShiftsToCalendar`.
4. **Presentation Layer (Obsidian Liquid Glass & Riccardo Breccia UX)**:
   - Nuova schermata unificata `GeminiShiftImportScreen.kt` accessibile da `SettingsScreen.kt` tramite rotta `SettingsSubScreen.IMPORT_SHIFTS`.
   - File picker (immagini e PDF) con miniatura anteprima nativa via `Bitmap.asImageBitmap()`.
   - Inserimento/modifica mascherata API Key con stato di salvataggio.
   - Campo nome turnista per pre-selezione.
   - Indicatore di caricamento elastico con micro-interazioni.
   - Card riassuntiva dei turni, orari e squadre estratte con pulsante stella `ElasticStarButton`.
   - Singolo pulsante indietro in alto a sinistra (44dp) e gestione tasto back hardware (`BackHandler`).
   - Notifiche ed errori gestiti esclusivamente tramite `AnimatedStatusPopup` ancorato a `BottomCenter` (zero layout shift).

## Code Layout
- `android-app/app/src/main/AndroidManifest.xml`: Permesso INTERNET
- `android-app/app/build.gradle.kts`: Dipendenza `testImplementation("org.json:json:20231013")` per i test unitari JVM
- `android-app/app/src/main/java/com/turni/calendario/data/`:
  - `SecurePreferencesManager.kt`: Gestione sicura API Key e nome utente
  - `GeminiApiClient.kt`: Client REST Gemini Flash Vision con JSON Schema
  - `GeminiShiftModels.kt`: Modelli dati per la risposta strutturata Gemini
  - `DocumentProcessor.kt`: Rendering PDF ad alta risoluzione e compressione/Base64 immagini
- `android-app/app/src/main/java/com/turni/calendario/ui/screens/`:
  - `GeminiShiftImportScreen.kt`: Interfaccia utente Obsidian Liquid Glass
  - `SettingsScreen.kt`: Entry point aggiornato
- `android-app/app/src/main/java/com/turni/calendario/MainActivity.kt`: Gestione navigazione e sub-screen
- `android-app/app/src/test/java/com/turni/calendario/`:
  - Unit test per parsing JSON, prompt schema, fallback, mapping e rotazioni

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | Gemini API Key Management | Inserimento, mascheramento, persistenza sicura e modifica dell'API Key | M1 | ORIGINAL_REQUEST § R1 |
| F2 | Multimodal REST Client | Client REST HttpsURLConnection verso Gemini Flash con structured JSON schema ed error handling | M1 | ORIGINAL_REQUEST § R1 |
| F3 | Image Document Processing | Selezione immagini (JPEG, PNG, WEBP), ridimensionamento e codifica Base64 | M2 | ORIGINAL_REQUEST § R2 |
| F4 | Native PDF Conversion | Conversione automatica PDF in Bitmap ad alta risoluzione tramite PdfRenderer nativo | M2 | ORIGINAL_REQUEST § R2 |
| F5 | Document Preview Thumbnail | Rendering miniatura grafica del documento o PDF elaborato | M2 | ORIGINAL_REQUEST § R2 |
| F6 | Structured Data Extraction | Prompt visivo specializzato per estrarre turni, orari, reperibilità, squadre e turnisti | M3 | ORIGINAL_REQUEST § R3 |
| F7 | Shift Catalog Registration | Registrazione/aggiornamento automatico in ShiftRepository.shiftDefinitions | M3 | ORIGINAL_REQUEST § R3, R4 |
| F8 | Auto User Squad Matching | Pre-selezione automatica squadra predefinita da matching nominativo utente | M4 | ORIGINAL_REQUEST § R4 |
| F9 | One-Click Calendar Setup | Pulsante di conferma rapida che applica la rotazione al calendario | M4 | ORIGINAL_REQUEST § R4 |
| F10 | Obsidian Liquid Glass Import UI | Schermata unificata con palette scura, riflessi prismatici e card traslucide | M4 | ORIGINAL_REQUEST § R5 |
| F11 | Riccardo Breccia Ergonomics | Thumb zone, micro-interazioni elastiche, star toggle, singolo pulsante back | M4 | ORIGINAL_REQUEST § R5 |
| F12 | Zero-Shift Animated Notifications | Notifiche di errore/successo esclusivamente con AnimatedStatusPopup dal basso | M4 | ORIGINAL_REQUEST § R5 |
| F13 | E2E & Adversarial Verification | Test suite completa su JVM (unit + edge case) e verifica integrità | M5 | ORIGINAL_REQUEST § R1-R5 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Core Network & Security | Permesso INTERNET, testImplementation org.json, SecurePreferencesManager, GeminiApiClient base | none | DONE |
| M2 | Document & Vision Processing | DocumentProcessor (PDF rendering via PdfRenderer nativo, compressione JPEG/PNG, Base64) e anteprima | M1 | DONE |
| M3 | Shift Extraction & Domain Mapping | Modelli GeminiShiftModels, parsing JSON strutturato, mapping su ShiftDefinition/ShiftRotation | M1, M2 | DONE |
| M4 | Liquid Glass UI & Setup Experience | GeminiShiftImportScreen, integrazione in SettingsScreen/MainScreen, Breccia UX, AnimatedStatusPopup | M1, M2, M3 | IN_PROGRESS |
| M5 | E2E Testing, Adversarial Hardening & Audit | 100% test verdi E2E e unitari, verifica build gradlew assembleDebug, Forensic Audit | M4 | PLANNED |

## Interface Contracts
### SecurePreferencesManager
```kotlin
interface SecurePreferencesManager {
    fun getApiKey(): String?
    fun saveApiKey(apiKey: String)
    fun getUserName(): String?
    fun saveUserName(name: String)
}
```

### GeminiApiClient
```kotlin
interface GeminiApiClient {
    suspend fun extractShiftData(
        apiKey: String,
        imageBase64: String,
        mimeType: String
    ): Result<GeminiShiftData>
}
```

### DocumentProcessor
```kotlin
interface DocumentProcessor {
    suspend fun processPdfToBitmap(context: Context, pdfUri: Uri): Bitmap
    fun processImageUriToBitmap(context: Context, imageUri: Uri): Bitmap
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): Pair<String, String> // (Base64, mimeType)
}
```

### GeminiShiftMapper
```kotlin
interface GeminiShiftMapper {
    fun mapToShiftDefinitions(data: GeminiShiftData): List<ShiftDefinition>
    fun mapToRotations(data: GeminiShiftData): List<ShiftRotation>
    fun matchUserSquad(data: GeminiShiftData, userName: String): String? // returns matching team ID
}
```
