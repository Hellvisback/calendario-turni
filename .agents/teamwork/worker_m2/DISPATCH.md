# DISPATCH — Worker Milestone 2: Document & Vision Processing

## Identity & Role
- Archetype: teamwork_preview_worker
- Role: Document & Vision Processing Developer
- Working Directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m2

## Objective
Implement Milestone 2 (Document & Vision Processing):
Create `android-app/app/src/main/java/com/turni/calendario/data/DocumentProcessor.kt`:
1. **PDF Rendering**:
   - Method `suspend fun processPdfToBitmap(context: Context, pdfUri: Uri): Bitmap`
   - Use Android native `android.graphics.pdf.PdfRenderer` (available from minSdk 26).
   - Handle SAF `content://` URIs safely: if `ParcelFileDescriptor` is not seekable, copy stream to a temporary cache file (`context.cacheDir/temp_pdf_render.pdf`), open it with `ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)`, render page 0 with `PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY`.
   - Scale appropriately (e.g. 2.0x-2.5x, max dimension 2048px) and pre-fill canvas with `Color.WHITE` to ensure tables and text have a solid white background (not black/transparent).
   - Ensure `page.close()`, `renderer.close()`, `pfd.close()`, and temporary file cleanup in `finally` blocks to prevent memory leaks.
2. **Image Uri to Bitmap**:
   - Method `fun processImageUriToBitmap(context: Context, imageUri: Uri): Bitmap`
   - Use `BitmapFactory.decodeStream` with `inJustDecodeBounds` to calculate optimal sample size (max 2048px on longest side), then decode the bitmap.
   - Fallback/in-memory helper for testing when Context is null or running on JVM.
3. **Bitmap to Base64 & Compression**:
   - Method `fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): Pair<String, String>` returning `(base64Data, mimeType)` (defaulting to `"image/jpeg"`).
   - Scale down if dimensions exceed 2048px while maintaining aspect ratio.
   - Compress to ByteArrayOutputStream using `Bitmap.CompressFormat.JPEG`.
   - Encode with `java.util.Base64.getEncoder().encodeToString(bytes)` (Java 8+, standard in Android SDK 26+ and JVM tests).
4. **Thumbnail Helper**:
   - Method `fun createThumbnail(bitmap: Bitmap, maxDim: Int = 300): Bitmap` to provide smooth, low-memory previews in the UI.

## Verification Required
Worker MUST verify:
- Create unit tests in `android-app/app/src/test/java/com/turni/calendario/DocumentProcessorTest.kt` verifying Base64 encoding, scaling calculations, JPEG quality, and error resilience.
- Run `.\gradlew.bat test` (with `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`).
- Run `.\gradlew.bat assembleDebug`.
Both must be 100% GREEN.

## Files Owned Exclusively
- `android-app/app/src/main/java/com/turni/calendario/data/DocumentProcessor.kt`
- `android-app/app/src/test/java/com/turni/calendario/DocumentProcessorTest.kt`


## 2026-10-14T17:36:32Z
Sei Worker M2 (Document & Vision Processing Developer).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m2

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche il piano globale e le analisi degli explorer:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m2\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_survey_3\analysis.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

File di cui hai la proprietà esclusiva di scrittura:
- android-app/app/src/main/java/com/turni/calendario/data/DocumentProcessor.kt
- android-app/app/src/test/java/com/turni/calendario/DocumentProcessorTest.kt

Implementa DocumentProcessor.kt:
1. processPdfToBitmap(context, pdfUri): rendering ad alta risoluzione tramite PdfRenderer nativo Android, fallback su file temporaneo cache se non seekable, canvas riempito di bianco per evitare trasparenze, pulizia risorse in finally.
2. processImageUriToBitmap(context, imageUri): decodifica con calcolo di inSampleSize per immagini di grandi dimensioni (max 2048px).
3. bitmapToBase64(bitmap, quality): ridimensionamento max 2048px mantenendo aspect ratio, compressione JPEG qualità 85, codifica Base64 con java.util.Base64.
4. createThumbnail(bitmap, maxDim = 300): creazione miniatura a bassa memoria per anteprima UI.

VERIFICA OBBLIGATORIA:
Scrivi DocumentProcessorTest.kt ed esegui:
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
powershell: $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
Entrambi devono terminare con BUILD SUCCESSFUL.

Scrivi handoff.md in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m2\handoff.md e invia un messaggio di completamento con send_message.
