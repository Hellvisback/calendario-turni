# DISPATCH — Explorer Survey 3

## Identity & Role
- Archetype: teamwork_preview_explorer
- Role: Codebase Explorer - Gemini Flash Vision Client, PDF/Bitmap Rendering & Testing
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3

## Objective
Investigate the technical integration requirements for Google Gemini Flash Vision API, PDF rendering via Android `PdfRenderer`, image handling, and test infrastructure.

## Scope & Files to Investigate
- Read `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
- Inspect `android-app/` looking for:
  - `build.gradle.kts` (app and root) to check existing HTTP clients (OkHttp, Ktor, Retrofit, or standard HttpURLConnection), coroutines, serialization (kotlinx.serialization or Gson or Moshi)
  - Android permissions in `AndroidManifest.xml` (e.g. INTERNET, READ_MEDIA_IMAGES)
  - Android `PdfRenderer` usage or implementation pattern to convert Uri/File of PDF into Bitmap
  - Image scaling, compression to JPEG/PNG, Base64 encoding for Gemini multimodale payload
  - Gemini Flash API REST endpoint format (`generateContent` with `gemini-1.5-flash` or `gemini-2.0-flash`), request schema (`response_mime_type: "application/json"` and `response_schema`)
  - Unit test setup in `app/src/test/` to see how tests are structured and run (`gradlew test`)

## Output
Write a structured report to `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\analysis.md` and complete handoff at `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\handoff.md`.
Report back via send_message when done.

## 2026-10-14T15:43:00Z
Sei Explorer 3 (Codebase Explorer - Gemini Flash Vision Client, PDF/Bitmap Rendering & Testing).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il tuo dispatch file:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\DISPATCH.md

Investiga approfonditamente i requisiti tecnici e i file di configurazione in android-app/:
1. Verifica build.gradle.kts (project e app): quali librerie di networking esistono (OkHttp, Retrofit, Ktor, o HttpURLConnection standard)? Quale JSON parser (kotlinx.serialization, Gson, Moshi, org.json)? Come è configurato Kotlin, Compose e Coroutines?
2. Verifica AndroidManifest.xml: permessi di rete (INTERNET), gestione storage/immagini/media picker.
3. Esamina l'implementazione o predisposizione per il rendering di PDF tramite android.graphics.pdf.PdfRenderer: come aprire un Uri di un PDF selezionato con l'Activity result contract (OpenDocument o GetContent), renderizzare la prima pagina (o pagine) su una Bitmap ad alta risoluzione (es. 300 DPI o scala adeguata), e convertirla in ByteArray/Base64.
4. Esamina il flusso di compressione delle immagini (JPEG/PNG) prima dell'upload per evitare payload eccessivi.
5. Esamina la specifica REST di Google Gemini generateContent: endpoint per gemini-1.5-flash / gemini-2.0-flash, formato payload multimodale (inlineData con mimeType e data Base64), structured output schema (response_mime_type: "application/json" e response_schema), gestione codici di stato (429 rate limit, 400 bad request, 403 invalid key, timeout).
6. Esamina l'ambiente di test: app/src/test/, librerie di test presenti (JUnit 4/5, MockK, Coroutines Test), come eseguire gradlew test e verificare che sia al 100% verde.

Scrivi il tuo report completo in:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\analysis.md
e il tuo handoff report in:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\handoff.md
Poi invia un messaggio di completamento a me (il Project Orchestrator) con send_message indicando i percorsi dei file e i risultati chiave.
