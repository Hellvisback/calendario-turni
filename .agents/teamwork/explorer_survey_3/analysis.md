# Report di Analisi Tecnica: Client Gemini Flash Vision, Rendering PDF/Bitmap e Test Suite

**Data**: 2026-10-14  
**Autore**: Explorer Survey 3 (Codebase Explorer - Gemini Flash Vision Client, PDF/Bitmap Rendering & Testing)  
**Ambiente**: Android Jetpack Compose (`android-app/`), Kotlin 1.7.0, AGP 7.2.2, minSdk 26, targetSdk 32, compileSdk 32  
**Target Device**: Realme 8 Pro (`cb08bc57`)  

---

## 1. Indagine `build.gradle.kts` (Project & App)

### 1.1 Inventario delle Dipendenze Esistenti
Dall'ispezione diretta di `android-app/build.gradle.kts` e `android-app/app/build.gradle.kts`, emerge il seguente quadro:
- **Build System**: Android Gradle Plugin `7.2.2`, Gradle Wrapper 7.x, JVM 11 (`sourceCompatibility = JavaVersion.VERSION_11`, `targetCompatibility = JavaVersion.VERSION_11`, `jvmTarget = "11"`).
- **Kotlin**: Versione `1.7.0` con plugin `kotlin-android`.
- **Jetpack Compose**:
  - `kotlinCompilerExtensionVersion = "1.2.0"`
  - `androidx.compose.ui:ui:1.2.0`
  - `androidx.compose.ui:ui-tooling-preview:1.2.0`
  - `androidx.compose.material3:material3:1.0.0-alpha14`
  - `androidx.compose.material:material-icons-extended:1.2.0`
  - `androidx.navigation:navigation-compose:2.5.1`
  - `androidx.activity:activity-compose:1.5.1`
- **Coroutines**: Presenti per via transitiva (`kotlinx-coroutines-core` e `kotlinx-coroutines-android`) tramite `lifecycle-runtime-ktx:2.5.1` e Compose runtime.
- **Networking**: **NESSUNA** libreria HTTP di terze parti è attualmente configurata (né OkHttp, né Retrofit, né Ktor, né Volley).
- **JSON Serialization**: **NESSUNA** libreria JSON dichiarata in `dependencies` (né `kotlinx.serialization`, né `Gson`, né `Moshi`). Tuttavia, `org.json` (`JSONObject`, `JSONArray`) è già utilizzato nel codebase nativo in `GoogleDriveBackupHelper.kt` poiché fornito nativamente dalla runtime Android (`android.jar`).
- **Testing**: Attualmente presente solo `testImplementation("junit:junit:4.13.2")`.

### 1.2 Valutazione Architetturale: Networking
Per effettuare chiamate REST POST verso l'endpoint di Google Gemini (`generateContent` con payload JSON/Base64):

| Opzione | Vantaggi | Svantaggi | Raccomandazione |
| :--- | :--- | :--- | :--- |
| **A. `java.net.HttpURLConnection` / `HttpsURLConnection`** | Zero nuove dipendenze; zero rischio di conflitti AGP/Kotlin; standard nativo Java/Android su minSdk 26+; supporta timeout, body POST, lettura stream di errore (`errorStream`) | Richiede qualche riga in più di boilerplate (lettura/scrittura stream) | **Consigliata**: massimizza la stabilità del progetto evitando dipendenze esterne. |
| **B. `com.squareup.okhttp3:okhttp:4.10.0`** | API fluida (`Request.Builder`, `RequestBody`, `OkHttpClient`); gestione automatica dei connection pool | Introduce dipendenza esterna (~4MB) che richiede risoluzione Gradle | **Valida alternativa**: se si desidera un codice client più conciso. |

### 1.3 Valutazione Architetturale: JSON Parsing e JVM Unit Test Trap
- In runtime Android (`app/src/main`), `org.json.JSONObject` e `org.json.JSONArray` sono nativi e pronti all'uso.
- **CRITICITÀ PER I TEST UNITARI JVM (`app/src/test`)**: Nel test runner JUnit locale su JVM, le classi contenute in `android.jar` (come `org.json.JSONObject`) sono **stubbate** e lanciano `java.lang.RuntimeException: Method ... not mocked` se invocate durante `.\gradlew.bat test`.
- **Risoluzione per i test**: Aggiungere in `app/build.gradle.kts`:
  ```kotlin
  testImplementation("org.json:json:20231013")
  ```
  Questo sostituisce lo stub di Android con l'implementazione reale Java durante l'esecuzione dei test JUnit su JVM, permettendo di testare al 100% la decodifica del JSON strutturato di Gemini.

---

## 2. Indagine `AndroidManifest.xml` e Gestione Permessi

### 2.1 Permessi Mancanti
L'ispezione di `android-app/app/src/main/AndroidManifest.xml` evidenzia che:
- `android.permission.INTERNET` **NON È PRESENTE**.  
  È **mandatorio** aggiungere:
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  ```
- È consigliabile aggiungere anche il controllo di stato della connessione:
  ```xml
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  ```

### 2.2 Gestione Storage e Media Picker
- L'applicazione ha `minSdk = 26` e `targetSdk = 32`.
- I permessi `READ_EXTERNAL_STORAGE` o `READ_MEDIA_IMAGES` (API 33) **NON sono necessari** se il caricamento di file, PDF o immagini avviene tramite il contratti standard del Storage Access Framework (SAF):
  - `ActivityResultContracts.GetContent()` oppure
  - `ActivityResultContracts.OpenDocument()` con `arrayOf("image/*", "application/pdf")`.
- Il sistema Android concede automaticamente il permesso temporaneo di lettura (`FLAG_GRANT_READ_URI_PERMISSION`) per lo specifico URI selezionato dall'utente, garantendo massima privacy e conformità con il Google Play Store senza richiedere permessi invasivi a runtime.

---

## 3. Rendering PDF nativo tramite `android.graphics.pdf.PdfRenderer`

### 3.1 Apertura dell'URI e File Descriptor
`PdfRenderer` richiede obbligatoriamente un `ParcelFileDescriptor` con accesso seekable:
```kotlin
val pfd = context.contentResolver.openFileDescriptor(uri, "r")
```
Tuttavia, alcuni ContentProvider cloud (es. Google Drive o provider terzi) possono restituire un descrittore non seekable (pipe). La best-practice robusta consiste nel copiare lo stream in un file temporaneo di cache:
```kotlin
fun uriToSeekablePfd(context: Context, uri: Uri): Pair<ParcelFileDescriptor, File?> {
    return try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) Pair(pfd, null)
        else throw IllegalStateException("Impossibile aprire il file descriptor per l'URI")
    } catch (e: Exception) {
        val tempFile = File.createTempFile("gemini_pdf_temp_", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        Pair(ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY), tempFile)
    }
}
```

### 3.2 Scala di Risoluzione e DPI per OCR Ottimale
- Per default, le pagine PDF hanno coordinate espresse in punti PostScript (72 DPI). Ad esempio, una pagina A4 corrisponde a 595 x 842 pixel.
- A 72 DPI, il testo tabellare di un prospetto RFI (orari tipo `06:00-13:36`, matricole tipo `8024724`, sigle turno tipo `M, P, N, RC, MD`) risulta sfocato e difficilmente interpretabile dalla visione artificiale.
- Per garantire una leggibilità OCR ottimale senza appesantire la memoria, occorre calcolare una scala adattiva mirata a una dimensione massima di **1600 - 2048 pixel sul lato lungo**:
  ```kotlin
  val maxDimension = 2048f
  val currentMax = maxOf(page.width, page.height).toFloat()
  val scale = if (currentMax > 0f) (maxDimension / currentMax).coerceIn(1.5f, 3.0f) else 2.0f
  val targetWidth = (page.width * scale).toInt()
  val targetHeight = (page.height * scale).toInt()
  ```

### 3.3 Gestione Trasparenza e Ciclo di Vita delle Risorse
- Le pagine PDF possono avere sfondo trasparente: prima del rendering su `Bitmap`, è indispensabile disegnare un background bianco (`Canvas(bitmap).drawColor(android.graphics.Color.WHITE)`).
- `PdfRenderer` **non è thread-safe** e consente una sola pagina aperta alla volta per istanza.
- È obbligatorio chiudere le risorse nell'ordine corretto (`page.close()` -> `renderer.close()` -> `pfd.close()` -> eliminazione `tempFile`) in un blocco `try-finally` o con helper Kotlin `.use`.

---

## 4. Flusso di Compressione Immagini e Codifica Base64

### 4.1 Ridimensionamento Adattivo
Prima dell'upload verso l'API Gemini, le immagini provenienti da galleria/screenshot o da `PdfRenderer` devono essere scalate proporzionalmente se superano i 2048px di lato:
```kotlin
fun scaleBitmapToMaxDimension(bitmap: Bitmap, maxDim: Int = 2048): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    if (maxSide <= maxDim) return bitmap
    val ratio = maxDim.toFloat() / maxSide
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).toInt(),
        (bitmap.height * ratio).toInt(),
        true
    )
}
```

### 4.2 Compressione JPEG
- Formato: `Bitmap.CompressFormat.JPEG`
- Qualità: `85`
- Risultato dimensionale: Una bitmap da 2048x1450 si comprime a circa **300 - 600 KB**, preservando caratteri e linee tabellari nitidissimi.

### 4.3 Codifica Base64
- Si raccomanda `java.util.Base64.getEncoder().encodeToString(jpegBytes)` invece di `android.util.Base64`.
- **Motivazione**: Disponibile a partire da Java 8 (pienamente compatibile con `minSdk = 26`). Funziona identicamente su dispositivo Android e durante i test unitari JUnit su JVM, evitando eccezioni `Method not mocked`.

---

## 5. Specifica REST Google Gemini Flash (`generateContent`)

### 5.1 Endpoint e Parametri
- Modelli supportati: `gemini-1.5-flash` (stabile) e `gemini-2.0-flash` (latest)
- URL:
  `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key={API_KEY}`
- Metodo HTTP: `POST`
- Header: `Content-Type: application/json`

### 5.2 Formato Payload Multimodale
Il payload inviato integra l'immagine Base64 (parte `inlineData`) e il prompt con lo schema JSON strutturato:
```json
{
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "text": "Sei un analizzatore esperto di prospetti turni ferroviari e industriali (es. MOD. P.R. RFI o prospetti sanitari). Analizza l'immagine allegata ed estrai in formato JSON strutturato: 1. Date di validità inizio/fine (validityStart, validityEnd in formato YYYY-MM-DD); 2. Tabella oraria con codici turno e orari di inizio/fine (shiftHours con code, name, startTime, endTime); 3. Squadre e rotazioni cicliche (teams con teamName, cycleDays, rotationSequence); 4. Elenco turnisti (employees con name, teamName, role, employeeId, isOnCall, dailyShifts); 5. Note su reperibilità o particolarità (notes)."
        },
        {
          "inlineData": {
            "mimeType": "image/jpeg",
            "data": "<BASE64_JPEG_DATA>"
          }
        }
      ]
    }
  ],
  "generationConfig": {
    "temperature": 0.1,
    "responseMimeType": "application/json",
    "responseSchema": {
      "type": "OBJECT",
      "properties": {
        "validityStart": { "type": "STRING", "description": "Data inizio validità YYYY-MM-DD" },
        "validityEnd": { "type": "STRING", "description": "Data fine validità YYYY-MM-DD" },
        "shiftHours": {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "code": { "type": "STRING" },
              "name": { "type": "STRING" },
              "startTime": { "type": "STRING" },
              "endTime": { "type": "STRING" }
            },
            "required": ["code", "startTime", "endTime"]
          }
        },
        "teams": {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "teamName": { "type": "STRING" },
              "cycleDays": { "type": "INTEGER" },
              "rotationSequence": {
                "type": "ARRAY",
                "items": { "type": "STRING" }
              }
            },
            "required": ["teamName", "rotationSequence"]
          }
        },
        "employees": {
          "type": "ARRAY",
          "items": {
            "type": "OBJECT",
            "properties": {
              "name": { "type": "STRING" },
              "teamName": { "type": "STRING" },
              "role": { "type": "STRING" },
              "employeeId": { "type": "STRING" },
              "isOnCall": { "type": "BOOLEAN" },
              "dailyShifts": {
                "type": "ARRAY",
                "items": {
                  "type": "OBJECT",
                  "properties": {
                    "date": { "type": "STRING" },
                    "code": { "type": "STRING" }
                  },
                  "required": ["date", "code"]
                }
              }
            },
            "required": ["name", "teamName"]
          }
        },
        "notes": { "type": "STRING" }
      },
      "required": ["teams", "employees"]
    }
  }
}
```

### 5.3 Mappatura Codici di Errore HTTP e Messaggi Utente
Il client gestisce in modo trasparente e protetto gli errori di rete e di stato HTTP:
1. **HTTP 400 Bad Request / API_KEY_INVALID**: API Key non valida, sintassi JSON o immagine non conforme.  
   *Messaggio*: `"API Key non valida o documento non supportato. Verifica la chiave inserita."*
2. **HTTP 403 Forbidden**: Chiave non autorizzata per l'API Generative Language.  
   *Messaggio*: `"Accesso negato: assicurati che l'API Key abbia i permessi per Gemini API."*
3. **HTTP 429 Too Many Requests / RESOURCE_EXHAUSTED**: Limite di frequenza o quota esaurita (es. free tier 15 RPM).  
   *Messaggio*: `"Limite di richieste superato (Quota esaurita). Attendi qualche istante prima di riprovare."*
4. **HTTP 500 / 503 Internal Server Error**: Servizio temporaneamente sovraccarico.  
   *Messaggio*: `"I server di Google Gemini sono temporaneamente occupati. Riprova tra poco."*
5. **`SocketTimeoutException`**: Timeout di connessione.  
   *Messaggio*: `"Timeout di connessione: il server ha impiegato troppo tempo a rispondere. Riprova."*
6. **`UnknownHostException`**: Assenza di connessione internet.  
   *Messaggio*: `"Nessuna connessione a internet. Verifica che il dispositivo sia connesso."*

### 5.4 Mappatura Verso il Modello Interno `NgeniaParseResult`
Il risultato JSON decodificato da Gemini Vision viene convertito direttamente nell'esistente `NgeniaParseResult`:
- `detectedHours`: mappa `code -> Pair(startTime, endTime)` (es. `"M" -> Pair("06:00", "13:36")`).
- `teams`: lista di `ParsedTeamRotation` con `dayCodes` e `dayShiftDefIds` generati tramite `NgeniaTurniParser.mapCodeToShiftDefId(code)`.
- `employees`: lista di `ParsedEmployee` con mappa `dayShifts` (`LocalDate -> code`).
- **Beneficio**: L'intera UI esistente di `ImportShiftsScreen` (selezione del lavoratore, filtro nome a monte, stella elastica del predefinito, anteprima turni, applicazione al calendario e salvataggio nel catalogo) continua a funzionare perfettamente senza duplicazione logica.

---

## 6. Ambiente di Test e Validazione

### 6.1 Stato Attuale dei Test
- Suite esistente:
  - `com.turni.calendario.NgeniaTurniParserTest`: 22 test
  - `com.turni.calendario.ShiftRepositoryTest`: 9 test
- Esecuzione verificata:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
  .\gradlew.bat test
  ```
  **Esito**: `BUILD SUCCESSFUL` (31 test eseguiti, 0 fallimenti, 0 errori, 100% verde).

### 6.2 Nuovi Test Unitari da Introdurre per Gemini Flash
Per completare i requisiti di accettazione del milestone:
1. **`GeminiVisionResponseParserTest`**:
   - Test decodifica JSON valido restituito da Gemini con squadre (Squadra A, B, C, D, B 1), orari (M, P, N, MD) e turnisti.
   - Test tolleranza a campi opzionali mancanti (`notes`, `employeeId`, `validityEnd`).
   - Test mappatura codici turno verso gli ID interni del catalogo (`ShiftRepository.shiftDefinitions`).
   - Test simulazione codici errore HTTP (429, 403, 400).
2. **`GeminiImageUtilsTest`**:
   - Test ridimensionamento proporzionale delle bitmap (lato lungo max 2048px).
   - Test codifica Base64 con `java.util.Base64`.
3. **Dipendenza di Test**:
   - Aggiungere `testImplementation("org.json:json:20231013")` a `app/build.gradle.kts` per garantire che i test di parsing JSON su JVM passino senza eccezioni di mock.

---

## 7. Raccomandazioni per l'Implementazione

1. **Gestione Persistente dell'API Key**:
   - Salvare la chiave Gemini in `SharedPreferences` (es. nome file `"gemini_settings"`, chiave `"api_key"`) con accesso privato e caricarla all'avvio in `ShiftRepository.geminiApiKey`.
   - Nella schermata `ImportShiftsScreen`, presentare un campo dedicato in stile Obsidian Glass con pulsante per mostrare/nascondere la chiave (`PasswordVisualTransformation`) e salvare rapidamente.
2. **UI di Anteprima Immagine/PDF**:
   - Quando l'utente seleziona un file PDF o immagine, generare immediatamente la `Bitmap` (o miniatura) e visualizzarla in una card di anteprima compatta con badge del formato ("PDF 1 Pagina" o "Immagine PNG/JPEG").
   - Pulsante primario dinamico: "Analizza con Gemini Flash Vision" con animazione di caricamento e messaggio di stato contestuale ("Conversione pagina...", "Interrogazione Gemini Flash...", "Elaborazione turni...").
3. **Nessun Banner Statico**:
   - Ogni notifica di esito (successo, quota esaurita, errore chiave, timeout) deve utilizzare rigorosamente `AnimatedStatusPopup` con overlay dal basso, in piena conformità con le regole di design del progetto.
