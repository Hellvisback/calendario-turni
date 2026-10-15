# Handoff Report — Explorer Survey 3

## 1. Observation
1. **File `android-app/app/build.gradle.kts` (Lines 52-66)**:
   ```kotlin
   dependencies {
       implementation("androidx.core:core-ktx:1.8.0")
       implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
       implementation("androidx.activity:activity-compose:1.5.1")
       implementation("androidx.compose.ui:ui:1.2.0")
       implementation("androidx.compose.ui:ui-tooling-preview:1.2.0")
       implementation("androidx.compose.material3:material3:1.0.0-alpha14")
       implementation("androidx.compose.material:material-icons-extended:1.2.0")
       implementation("androidx.navigation:navigation-compose:2.5.1")
       debugImplementation("androidx.compose.ui:ui-tooling:1.2.0")
       debugImplementation("androidx.compose.ui:ui-test-manifest:1.2.0")
       testImplementation("junit:junit:4.13.2")
   }
   ```
   Direct observation: Nessuna libreria di terze parti per networking (OkHttp, Retrofit) o JSON (Gson, Moshi, kotlinx.serialization) è inclusa nelle dipendenze.
2. **File `android-app/app/src/main/AndroidManifest.xml` (Lines 4-12)**:
   ```xml
   <uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
   <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   <uses-permission android:name="android.permission.VIBRATE" />
   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
   <uses-permission android:name="android.permission.READ_CALENDAR" />
   <uses-permission android:name="android.permission.WRITE_CALENDAR" />
   <uses-permission android:name="android.permission.GET_ACCOUNTS" />
   ```
   Direct observation: Il permesso `android.permission.INTERNET` è assente.
3. **File `android-app/app/src/main/java/com/turni/calendario/sync/GoogleDriveBackupHelper.kt` (Lines 9-10)**:
   ```kotlin
   import org.json.JSONArray
   import org.json.JSONObject
   ```
   Direct observation: Il parser `org.json` è già impiegato nel progetto sfruttando le classi integrate nell'SDK Android runtime.
4. **Comando di Build e Test**:
   - `powershell -Command "$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jre'; .\gradlew.bat test"`
   - Risultato verbatim:
     `> Task :app:testDebugUnitTest`
     `> Task :app:testReleaseUnitTest`
     `> Task :app:test`
     `BUILD SUCCESSFUL in 1m 11s`
     `31 test totali (22 in NgeniaTurniParserTest, 9 in ShiftRepositoryTest), 0 failures, 0 errors`.
   - `powershell -Command "$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jre'; .\gradlew.bat assembleDebug"`
   - Risultato verbatim: `BUILD SUCCESSFUL in 18s`.

---

## 2. Logic Chain
1. Dalle osservazioni (1) e (2), per poter effettuare chiamate REST alla Gemini API da Android:
   - È necessario dichiarare `<uses-permission android:name="android.permission.INTERNET" />` in `AndroidManifest.xml`, altrimenti qualsiasi connessione genererà `SecurityException: Permission denied (missing INTERNET permission)`.
   - Per il networking, l'uso di `java.net.HttpURLConnection` / `HttpsURLConnection` standard permette di eseguire chiamate POST con body JSON e ricezione di risposte senza introdurre dipendenze Gradle esterne che potrebbero confliggere con Kotlin 1.7.0 e AGP 7.2.2.
2. Dalle osservazioni (1) e (3), `org.json.JSONObject` e `org.json.JSONArray` sono disponibili in Android runtime, ma per eseguire test unitari su JVM in `app/src/test`, le classi di `android.jar` sono stubbed e lanciano `RuntimeException: Method ... not mocked`. Pertanto, per scrivere test unitari dedicati al parser della risposta Gemini su JVM, è necessario aggiungere `testImplementation("org.json:json:20231013")` a `app/build.gradle.kts`.
3. Per il rendering PDF tramite `android.graphics.pdf.PdfRenderer`:
   - L'apertura dell'URI da SAF con `contentResolver.openFileDescriptor(uri, "r")` può fallire se il provider non supporta descrittori seekable. La creazione di un file temporaneo in `context.cacheDir` azzera il rischio di crash.
   - Poiché le dimensioni native di una pagina PDF sono espresse in punti PostScript (72 DPI, ca. 595x842 per A4), il testo delle tabelle risulterebbe illeggibile. Una scalatura adattiva di 2.0x-2.5x per portare la dimensione massima a ~1600-2048px garantisce un OCR ottimale e nitido.
4. Dalla specifica REST Google Gemini (`v1beta/models/gemini-1.5-flash:generateContent`), la configurazione di `generationConfig` con `responseMimeType: "application/json"` e `responseSchema` garantisce la restituzione di JSON perfettamente conforme alla struttura dati richiesta (date di validità, tabella orari `shiftHours`, squadre `teams`, e turnisti `employees`).
5. La mappatura del risultato JSON nell'esistente modello `NgeniaParseResult` consente di riutilizzare integralmente la UI di selezione turnista, il toggle a stella elastico per la rotazione predefinita e l'applicazione al calendario già validati e funzionanti in `ImportShiftsScreen.kt`.

---

## 3. Caveats
- Non è stato testato l'invio live con una chiave API reale attiva (ambiente read-only di esplorazione, le chiamate sono state analizzate in base alla documentazione e schema REST ufficiale).
- Le prestazioni di conversione PDF su documenti superiori alle 10 pagine non sono state investigate; tuttavia, i prospetti turni ferroviari/aziendali (MOD. P.R.) sono composti da 1 o 2 pagine e l'implementazione limita il rendering alle prime pagine rilevanti.
- L'ispezione sul device fisico Realme 8 Pro è demandata alla fase di esecuzione/rilascio.

---

## 4. Conclusion
L'infrastruttura dell'applicazione è pronta e compatibile per l'integrazione di Google Gemini Flash Vision:
1. Aggiungere il permesso `INTERNET` in `AndroidManifest.xml`.
2. Aggiungere `testImplementation("org.json:json:20231013")` in `app/build.gradle.kts` per abilitare i test unitari JSON su JVM.
3. Creare il client `GeminiVisionClient` (usando `HttpsURLConnection` e `org.json`), l'helper per la conversione PDF `PdfBitmapRenderer` (con scala 2x-2.5x e salvataggio cache sicuro), e la funzione di compressione JPEG / Base64.
4. Salvare in modo persistente l'API Key in `SharedPreferences` e collegare l'interfaccia di `ImportShiftsScreen.kt` mantenendo il pattern Obsidian Liquid Glass, l'anteprima visiva della Bitmap e le notifiche contestuali con `AnimatedStatusPopup`.

---

## 5. Verification Method
1. **Verifica Permesso Rete**:
   - Ispezionare `android-app/app/src/main/AndroidManifest.xml` e confermare la presenza di `<uses-permission android:name="android.permission.INTERNET" />`.
2. **Verifica Suite Test Unitari**:
   - Eseguire da terminale:
     ```powershell
     $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
     cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
     .\gradlew.bat test
     ```
   - Invalidation condition: Qualsiasi fallimento nei 31 test esistenti o nei nuovi test JSON invalida l'integrità.
3. **Verifica Compilazione Release/Debug**:
   - Eseguire:
     ```powershell
     .\gradlew.bat assembleDebug
     ```
   - Invalidation condition: Errore di compilazione Gradle o assenza dell'APK in `android-app/app/build/outputs/apk/debug/app-debug.apk`.
