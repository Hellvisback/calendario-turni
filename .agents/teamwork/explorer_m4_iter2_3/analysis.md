# Report di Analisi e Piano d'Azione Olistico — Milestone 4 Iteration 2
**Autore**: Explorer 3 (Milestone 4 Iteration 2: Holistic Synthesis & Action Plan)  
**Data**: 2026-10-15  
**Destinatari**: Orchestrator, Worker M4 Iteration 2, Reviewer 1 & 2, Challenger 1 & 2, Auditor  

---

## 1. Executive Summary

L'analisi integrata dei report dei Reviewer 1 e 2 (`reviewer_m4_1/handoff.md`, `reviewer_m4_2/handoff.md`), dei requisiti primari (`ORIGINAL_REQUEST.md`), del documento architetturale (`PROJECT.md`) e dello stato corrente del codice sorgente evidenzia che:
1. **Compilazione & Stabilità Gradle**: Il fallimento della compilazione Kotlin (`OutOfMemoryError: Java heap space` su `:app:compileDebugKotlin`) riscontrato dai reviewer è dovuto all'insufficiente allocazione di memoria per il demone del compilatore Kotlin (`kotlin.daemon.jvmargs`) e a demoni orfani/lock residui. L'incremento dell'heap a 4096MB con Garbage Collector parallelo (`-XX:+UseParallelGC`) e una pulizia preventiva dei demoni (`.\gradlew.bat --stop`) stabilizzano definitivamente il processo di compilazione.
2. **Conformità ai Requisiti Funzionali e UX (R4 & Acceptance Criteria)**:
   - È necessario formalizzare e rifinire l'`AlertDialog` di uscita e conferma alla pressione di "Modifica" con il testo esatto prescritto dai requisiti.
   - È necessario inserire la **nota visiva esplicativa** richiesta espressamente da `ORIGINAL_REQUEST.md § R4` ("i turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione dell'app").
   - È necessario allineare la nomenclatura dei pulsanti primario e secondario a `"Salva nel catalogo"` e `"Modifica"`.
   - È necessario proteggere l'azione di salvataggio da tocchi multipli o doppi click accidentali tramite stato `isSaving` (debounce).
   - È necessario verificare la corretta persistenza e il mascheramento dell'API Key tramite `PasswordVisualTransformation` e toggle di visibilità.
3. **Preservazione dei Vincoli Non Negoziabili**:
   - Palette e stile **Obsidian Liquid Glass** (nessuna alterazione cromatica o deviazione dallo stile).
   - **Riccardo Breccia UX**: singolo pulsante indietro 40dp in alto a sinistra con `BackHandler`, nessun tasto indietro duplicato in basso, spacer 100dp per Thumb Zone, `ElasticStarButton` con animazione elastica a molla e toggle a stato neutro (`defaultRotationId` azzerato su secondo click).
   - **Zero Layout Shift**: notifiche gestite ESCLUSIVAMENTE via `AnimatedStatusPopup` ancorato a `BottomCenter`.

---

## 2. Matrice di Sintesi dei Riscontri dei Reviewer

| Area di Verifica | Riscontro Reviewer 1 | Riscontro Reviewer 2 | Stato Corrente | Azione Richiesta Worker M4 Iter. 2 |
|---|---|---|---|---|
| **Build & Heap Space** | OOM su `compileDebugKotlin` con `-Xmx2048m` | `Not enough memory to run compilation` | Modificato parzialmente a `-Xmx4096m` | Aggiungere `-XX:+UseParallelGC` a Gradle e Kotlin daemon; eseguire `.\gradlew.bat --stop` |
| **Dialog Modale su "Modifica"** | Assente nella prima revisione | Assente nella prima revisione | Presente stato `showExitToEditDialog` parziale | Rifinire testo verbatim, pulsanti "Conferma" / "Annulla" e styling Obsidian |
| **Nota Esplicativa R4** | Non menzionata specificamente | Mancanza segnalata in § B.2 | Assente nella UI | Aggiungere card Obsidian con testo informativo prima dei pulsanti di chiusura |
| **Nomenclatura Pulsanti Finali** | Pulsante denominato "Modifica e Rifinisci..." | Pulsante secondario diretto | Discrepanza con Acceptance Criteria | Denominare pulsante primario "Salva nel catalogo" e secondario "Modifica" |
| **Debounce Salvataggio** | Non rilevato | Rilevata vulnerabilità doppio tap in § C.1 | Assente (`isSaving` mancante) | Introdurre `var isSaving by remember { mutableStateOf(false) }` e disabilitare pulsante |
| **Mascheramento API Key** | Rilevata assenza di PasswordVisualTransformation | Rilevata esposizione in chiaro durante editing | Presente toggle `isApiKeyVisible` | Convalidare reset stato e corretta interazione |
| **Ergonomia Breccia & UI** | 100% CONFORME (back 40dp, spacer 100dp, popup, stella) | 100% CONFORME | Conforme | Preservare rigorosamente senza regressioni |
| **Suite Test Unitari JVM** | Non eseguita per blocco build | 223/223 test verdi (100% pass) | Test passati con successo | Mantenere 100% green su `.\gradlew.bat test` |

---

## 3. Specifiche Tecniche Dettagliate delle Modifiche

### 3.1. `android-app/gradle.properties`

Il file deve contenere esattamente la seguente configurazione per eliminare alla radice qualunque rischio di OutOfMemoryError durante l'abbassamento IR del compilatore Jetpack Compose:

```properties
org.gradle.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC
```

**Motivazione tecnica**:
- `-Xmx4096m`: Garantisce 4 GB dedicati sia al demone Gradle orchestratore sia al demone worker del compilatore Kotlin.
- `-XX:+UseParallelGC`: Il Parallel Garbage Collector massimizza il throughput durante compilazioni massive CLI ed evita frammentazioni della memoria che si verificano tipicamente con G1GC su heap medi.
- `-Dfile.encoding=UTF-8`: Previene problemi di encoding su ambienti Windows PowerShell.

---

### 3.2. `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`

#### A. Dichiarazione dello stato `isSaving`
Inserire lo stato di protezione concorrenza insieme agli altri stati della schermata (attorno alla riga 105):
```kotlin
// Stato di salvataggio per debounce e protezione contro doppi tap
var isSaving by remember { mutableStateOf(false) }
```

#### B. Nota Visiva Esplicativa (Requisito R4)
Inserire immediatamente prima dei pulsanti di chiusura (dopo la card dei turni/squadre estratte) il seguente componente Obsidian Liquid Glass:
```kotlin
// =========================================================================
// Nota visiva esplicativa catalogo (ORIGINAL_REQUEST.md § R4)
// =========================================================================
Spacer(modifier = Modifier.height(18.dp))

Surface(
    shape = RoundedCornerShape(12.dp),
    color = ObsidianSurfaceContainer.copy(alpha = 0.55f),
    border = androidx.compose.foundation.BorderStroke(0.6.dp, ObsidianBorderSubtle),
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = ObsidianPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "I turni e le rotazioni importati potranno essere rifiniti e personalizzati in qualsiasi momento nell'apposita sezione del catalogo dell'app.",
            fontSize = 11.5.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )
    }
}

Spacer(modifier = Modifier.height(12.dp))
```

#### C. Pulsante Primario "Salva nel catalogo" con Debounce
Riformulare il pulsante primario per allinearlo agli Acceptance Criteria ("I pulsanti finali sono 'Salva nel catalogo' e 'Modifica'") e integrarlo con `isSaving`:
```kotlin
// Bottone primario: Salva nel catalogo
Button(
    onClick = {
        if (isSaving || isAnalyzing) return@Button
        isSaving = true

        val setupResult = GeminiShiftMapper.registerExtractedData(
            repository = ShiftRepository,
            data = currentData,
            targetUserName = userName
        )

        popupType = PopupType.SUCCESS
        feedbackMessage = "Turni registrati nel catalogo! ${setupResult.message}"

        coroutineScope.launch {
            delay(1500)
            isSaving = false
            onDismiss()
        }
    },
    enabled = !isSaving && !isAnalyzing,
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = ObsidianPrimaryContainer,
        disabledContainerColor = ObsidianPrimaryContainer.copy(alpha = 0.5f)
    ),
    modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
) {
    if (isSaving) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Salvataggio in corso...",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    } else {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Salva nel catalogo",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
```

#### D. Pulsante Secondario "Modifica" e `AlertDialog` Modale di Sicurezza
Allineare il pulsante secondario a "Modifica" e strutturare l'`AlertDialog` secondo i requisiti testuali di `ORIGINAL_REQUEST.md § R4`:
```kotlin
// Bottone secondario: Modifica (con dialog modale di avviso di uscita)
if (onNavigateToTurni != null) {
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            if (!isSaving && !isAnalyzing) {
                showExitToEditDialog = true
            }
        },
        enabled = !isSaving && !isAnalyzing,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Icon(
            imageVector = Icons.Default.EditCalendar,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Modifica",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
    }

    if (showExitToEditDialog) {
        AlertDialog(
            onDismissRequest = { showExitToEditDialog = false },
            title = {
                Text(
                    text = "Attenzione",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitToEditDialog = false
                        // Salva nel catalogo prima del passaggio
                        GeminiShiftMapper.registerExtractedData(
                            repository = ShiftRepository,
                            data = currentData,
                            targetUserName = userName
                        )
                        val targetRot = ShiftRepository.rotations.find {
                            it.name.equals(selectedSquadName, ignoreCase = true)
                        } ?: ShiftRepository.activeRotation
                        onNavigateToTurni(targetRot)
                    }
                ) {
                    Text(
                        text = "Conferma",
                        color = ObsidianPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitToEditDialog = false }
                ) {
                    Text(
                        text = "Annulla",
                        color = TextMuted
                    )
                }
            },
            containerColor = ObsidianSurfaceContainer,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        )
    }
}
```

---

## 4. Checklist Operativa per il Worker M4 Iteration 2

Il Worker M4 Iteration 2 deve seguire pedissequamente questa sequenza operativa in 6 fasi:

### [ ] Fase 1: Aggiornamento Configurazione Gradle (`gradle.properties`)
- [ ] Aprire `android-app/gradle.properties`.
- [ ] Verificare che le proprietà JVM corrispondano a:
  ```properties
  org.gradle.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8
  android.useAndroidX=true
  android.nonTransitiveRClass=true
  kotlin.code.style=official
  kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC
  ```

### [ ] Fase 2: Rifinitura `GeminiShiftImportScreen.kt`
- [ ] Verificare la presenza dello stato `var isSaving by remember { mutableStateOf(false) }`.
- [ ] Verificare che il campo API Key utilizzi `PasswordVisualTransformation` e toggle di visibilità temporanea.
- [ ] Inserire la **Nota Visiva Esplicativa** ("I turni e le rotazioni importati potranno essere rifiniti e personalizzati in qualsiasi momento nell'apposita sezione del catalogo dell'app.") posizionata sopra i tasti finali.
- [ ] Ridenominare il pulsante primario in `"Salva nel catalogo"` collegandolo a `isSaving` per disabilitazione al tocco.
- [ ] Ridenominare il pulsante secondario in `"Modifica"` collegandolo all'apertura del dialog modale `showExitToEditDialog = true`.
- [ ] Verificare che l'`AlertDialog` mostri il titolo `"Attenzione"`, il messaggio esatto di `R4` (*"Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni."*), e i bottoni `"Conferma"` e `"Annulla"`.
- [ ] Verificare l'integrità del singolo tasto indietro in alto a sinistra (40dp) e dello `Spacer(modifier = Modifier.height(100.dp))` in fondo per la Thumb Zone.

### [ ] Fase 3: Reset Demoni Gradle e Risoluzione Lock File Windows
Sui sistemi operativi Windows, terminazioni anomale della JVM o esecuzioni concorrenti mantengono file handle aperti su `app\build\test-results\testReleaseUnitTest\binary\output.bin` (`java.io.IOException: Unable to delete directory`) e generano warning di sessioni orfane (`Detected multiple Kotlin daemon sessions at build\kotlin\sessions`).
Eseguire la seguente sequenza di pulizia garantita da PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
cd "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
# 1. Arresto deterministico dei demoni Gradle e rilascio di tutti i file handle
.\gradlew.bat --stop

# 2. Eliminazione preventiva dei lock e delle sessioni orfane
Remove-Item -Path "app\build\test-results" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "app\build\kotlin\sessions" -Recurse -Force -ErrorAction SilentlyContinue
```

### [ ] Fase 4: Esecuzione Suite Test Unitari JVM
Eseguire:
```powershell
.\gradlew.bat test
```
*Criterio di successo*: `BUILD SUCCESSFUL`, 223+ test eseguiti con 0 fallimenti e 0 ignorati.

### [ ] Fase 5: Compilazione Completa APK di Debug
Eseguire:
```powershell
.\gradlew.bat assembleDebug
```
*Criterio di successo*: `BUILD SUCCESSFUL`, nessun errore di memoria heap, generazione di `android-app/app/build/outputs/apk/debug/app-debug.apk`.

### [ ] Fase 6: Redazione Handoff & Notifica Gate
- [ ] Verificare la dimensione e timestamp del file APK generato.
- [ ] Scrivere `handoff.md` nel folder di lavoro del Worker documentando le evidenze empiriche.
- [ ] Inviare messaggio di completamento all'Orchestrator.

---

## 5. Piano di Verifica Indipendente per il Gate M4

Tutti gli agenti di controllo (Reviewer 1, Reviewer 2, Challenger 1, Challenger 2, Auditor) dovranno poter verificare in autonomia i seguenti punti oggettivi:
1. **Configurazione Gradle**: `android-app/gradle.properties` contiene `-Xmx4096m` e `-XX:+UseParallelGC` sia in `org.gradle.jvmargs` che in `kotlin.daemon.jvmargs`.
2. **Ispezione Visiva e Codice Sorgente**:
   - `GeminiShiftImportScreen.kt` contiene la nota visiva esplicativa R4.
   - `GeminiShiftImportScreen.kt` espone i pulsanti `"Salva nel catalogo"` e `"Modifica"`.
   - Il click su `"Modifica"` apre l'`AlertDialog` con il testo prescritto.
   - Il click su `"Salva nel catalogo"` previene doppi invii mediante `isSaving`.
   - Nessun tasto back duplicato in basso; presenza dello spacer 100dp; notifiche via `AnimatedStatusPopup`.
3. **Esecuzione Build**:
   `.\gradlew.bat --stop` seguito da `.\gradlew.bat test` e `.\gradlew.bat assembleDebug` completano con codice di uscita 0 (`BUILD SUCCESSFUL`).
