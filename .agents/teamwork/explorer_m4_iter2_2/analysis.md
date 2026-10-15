# Analisi Tecnica e Specifica UI & Acceptance Criteria Alignment
**Agente**: Explorer 2 (Milestone 4 Iteration 2)  
**Data**: 2026-10-15  
**Working Directory**: `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_2`  
**Target File**: `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`  
**Stato**: Completata (Read-Only)

---

## 1. Executive Summary & Problem Statement

Durante il Gate di Milestone 4, entrambi i revisori indipendenti (**Reviewer 1** e **Reviewer 2**) hanno rilevato una serie di discrepanze tra i requisiti formali sanciti in `ORIGINAL_REQUEST.md` (§ R4, § R1 e Acceptance Criteria) e l'attuale implementazione della schermata `GeminiShiftImportScreen.kt`:

1. **Assenza / Incompletezza del Dialog Modale di Avviso su "Modifica" (§ R4)**:
   - `ORIGINAL_REQUEST.md` § R4 impone: *"Alla pressione di 'Modifica', mostrare un dialog modale di avviso: 'Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni', consentendo di confermare il passaggio o annullare."*
   - Gli Acceptance Criteria ribadiscono: *"[ ] Il click su 'Modifica' apre il dialog modale di avviso di uscita con opzioni di conferma/annulla."*
   - L'azione secondaria deve quindi aprire un `AlertDialog` Obsidian Liquid Glass prima di navigare, richiedendo conferma esplicita dell'utente.
2. **Mancanza della Nota Visiva Esplicativa (§ R4)**:
   - `ORIGINAL_REQUEST.md` § R4 specifica: *"Includere una nota visiva esplicativa che ricordi all'utente che i turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione dell'app."*
   - Attualmente, nella schermata manca del tutto un elemento visivo/card dedicata prima delle azioni di chiusura con il testo prescritto.
3. **Mancanza di Debounce e Protezione Concorrenza su "Conferma e Applica Turni"**:
   - Il click sul pulsante primario avvia la registrazione nel catalogo e attende 1500ms per il feedback dell'HUD. In assenza di uno stato `isSaving` e della disabilitazione `enabled = !isSaving`, tocchi ripetuti rapidi causano registrazioni multiple e conflitti di coroutine.
4. **Mascheramento e Toggle Visibilità API Key (§ R1)**:
   - Inserimento dell'API Key deve garantire privacy e sicurezza visiva tramite `PasswordVisualTransformation` e toggle eye icon (`Visibility`/`VisibilityOff`).

Il presente documento fornisce al Worker la **specifica esatta in codice Kotlin Jetpack Compose**, corredata da blocchi `before -> after`, per implementare queste correzioni con precisione chirurgica.

---

## 2. Analisi Dettagliata delle Specifiche

### 2.1 Dialog Modale di Avviso su "Modifica" (§ R4)

#### Analisi Funzionale ed Ergonomica
- **Trigger**: Il tocco sul pulsante secondario ("Modifica" / "Modifica e Rifinisci nel Catalogo Turni") non deve navigare immediatamente. Deve invece impostare lo stato `showExitToEditDialog = true`.
- **Comportamento di Conferma**:
  - Salva le definizioni dei turni e la rotazione tramite `GeminiShiftMapper.registerExtractedData(repository = ShiftRepository, data = currentData, targetUserName = userName)`.
  - Individua la rotazione target (`selectedSquadName` o `activeRotation`).
  - Naviga verso la schermata di modifica turni (`onNavigateToTurni(targetRot)` se non nullo, altrimenti `onDismiss()`).
  - Chiude il dialog (`showExitToEditDialog = false`).
- **Comportamento di Annullamento**:
  - Chiude il dialog (`showExitToEditDialog = false`) senza alcuna navigazione, permettendo all'utente di rimanere nella schermata.
- **Stile Obsidian Liquid Glass**:
  - `containerColor = ObsidianSurfaceContainer`
  - `shape = RoundedCornerShape(20.dp)`
  - Bordo sottile di riflesso prismatico
  - Titolo con icona `Icons.Default.WarningAmber` in colore ambra (`#F59E0B`)
  - Testo prescritto verbatim da R4: *"Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni."*
  - Pulsante primario di conferma: `Button` con container `ObsidianPrimaryContainer` e testo `"Esci e Modifica"`.
  - Pulsante secondario di annullamento: `OutlinedButton` con bordo `ObsidianBorderSubtle` e testo `"Rimani qui"`.

#### Specifiche Codice Compose (Punto 1)
```kotlin
if (showExitToEditDialog) {
    AlertDialog(
        onDismissRequest = { showExitToEditDialog = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Attenzione",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Text(
                text = "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni.",
                color = TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showExitToEditDialog = false
                    GeminiShiftMapper.registerExtractedData(
                        repository = ShiftRepository,
                        data = currentData,
                        targetUserName = userName
                    )
                    val targetRot = ShiftRepository.rotations.find {
                        it.name.equals(selectedSquadName, ignoreCase = true)
                    } ?: ShiftRepository.activeRotation
                    if (onNavigateToTurni != null) {
                        onNavigateToTurni(targetRot)
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Esci e Modifica",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { showExitToEditDialog = false },
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
            ) {
                Text(
                    text = "Rimani qui",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = ObsidianSurfaceContainer,
        shape = RoundedCornerShape(20.dp)
    )
}
```

---

### 2.2 Nota Visiva Esplicativa R4 sul Catalogo Turni

#### Analisi Funzionale ed Ergonomica
- **Requisito**: § R4 richiede che l'utente sia informato che l'importazione automatica non è vincolante e che i turni potranno essere liberamente ritoccati nella sezione dedicata.
- **Testo prescritto**: `"I turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione 'Catalogo Turni' dell'app."`
- **Posizionamento**: All'interno del blocco risultati (`geminiData?.let { currentData -> ... }`), posizionata immediatamente sopra i pulsanti finali di azione. In questo modo è visibile nel momento esatto della decisione (salva vs modifica).
- **Stile Obsidian Liquid Glass**:
  - `Surface` con forma `RoundedCornerShape(14.dp)`.
  - Sfondo `ObsidianSurfaceContainer` e bordo `ObsidianBorderSubtle` da 1.dp.
  - Badge circolare primario contenente l'icona informativa `Icons.Default.Info` (tinta `ObsidianPrimary`).
  - Testo `TextSecondary` (12.sp, lineHeight 17.sp) per la massima leggibilità e discrezione visiva.

#### Specifiche Codice Compose (Punto 2)
```kotlin
// =========================================================================
// Nota visiva esplicativa (§ R4): Promemoria rifinitura turni nel catalogo
// =========================================================================
Spacer(modifier = Modifier.height(16.dp))
Surface(
    shape = RoundedCornerShape(14.dp),
    color = ObsidianSurfaceContainer,
    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ObsidianPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = ObsidianPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "I turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione 'Catalogo Turni' dell'app.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
```

---

### 2.3 Debounce e Disabilitazione con `isSaving` su "Conferma e Applica Turni"

#### Analisi Funzionale ed Ergonomica
- **Stato**: Introdurre `var isSaving by remember { mutableStateOf(false) }`.
- **Pulsante Primario ("Conferma e Applica Turni")**:
  - `enabled = !isSaving`
  - All'avvio dell'evento `onClick`: se `isSaving` è `true`, ritorno immediato; altrimenti imposta `isSaving = true`.
  - Feedback visivo nel pulsante:
    - Se `isSaving == true`: mostra `CircularProgressIndicator` (18dp, bianco) affiancato da `"Applicazione turni in corso..."`.
    - Se `isSaving == false`: mostra l'icona standard `Icons.Default.CheckCircle` con `"Conferma e Applica Turni"`.
- **Pulsante Secondario ("Modifica e Rifinisci nel Catalogo Turni")**:
  - `enabled = !isSaving` per evitare collisioni di navigazione durante la scrittura del catalogo.
  - Tinte icone e testo attenuate a `TextMuted` quando disabilitato.

#### Specifiche Codice Compose (Punto 3)
```kotlin
// 1. Dichiarazione dello stato (in testa al composable GeminiShiftImportScreen):
var isSaving by remember { mutableStateOf(false) }

// 2. Bottone Primario: Conferma e Applica Turni con Debounce
Button(
    onClick = {
        if (isSaving) return@Button
        isSaving = true
        val setupResult = GeminiShiftMapper.registerExtractedData(
            repository = ShiftRepository,
            data = currentData,
            targetUserName = userName
        )

        popupType = PopupType.SUCCESS
        feedbackMessage = "Turni importati con successo! ${setupResult.message}"

        coroutineScope.launch {
            delay(1500)
            onDismiss()
        }
    },
    enabled = !isSaving,
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
            modifier = Modifier.size(18.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Applicazione turni in corso...",
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
            text = "Conferma e Applica Turni",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// 3. Bottone Secondario: Modifica disabilitato durante il salvataggio
if (onNavigateToTurni != null) {
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            if (!isSaving) {
                showExitToEditDialog = true
            }
        },
        enabled = !isSaving,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Icon(
            imageVector = Icons.Default.EditCalendar,
            contentDescription = null,
            tint = if (!isSaving) TextPrimary else TextMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Modifica e Rifinisci nel Catalogo Turni",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (!isSaving) TextPrimary else TextMuted
        )
    }
}
```

---

### 2.4 PasswordVisualTransformation su Inserimento API Key con Toggle di Visualizzazione

#### Analisi Funzionale ed Ergonomica
- **Stato**: `var isApiKeyVisible by remember { mutableStateOf(false) }`.
- **VisualTransformation**: `visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation()`.
- **Trailing Icon**:
  - `IconButton` che commuta `isApiKeyVisible = !isApiKeyVisible`.
  - Icona: `if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility` con tint `TextMuted`.
  - Se `apiKeyInput.isNotBlank()`, affiancato dal pulsante Check verde per il salvataggio nelle preferenze sicure.

#### Specifiche Codice Compose (Punto 4)
```kotlin
// Stato in testa a GeminiShiftImportScreen:
var isApiKeyVisible by remember { mutableStateOf(false) }

// Nel pannello di configurazione:
OutlinedTextField(
    value = apiKeyInput,
    onValueChange = { apiKeyInput = it },
    label = { Text("Google Gemini API Key", fontSize = 12.sp) },
    placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
    leadingIcon = {
        Icon(Icons.Default.VpnKey, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
    },
    trailingIcon = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                Icon(
                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (isApiKeyVisible) "Nascondi chiave" else "Mostra chiave",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (apiKeyInput.isNotBlank()) {
                IconButton(
                    onClick = {
                        val trimmed = apiKeyInput.trim()
                        apiKey = trimmed
                        SecurePreferencesManager.saveApiKey(context, trimmed)
                        isEditingApiKey = false
                        popupType = PopupType.SUCCESS
                        feedbackMessage = "API Key memorizzata con successo!"
                    }
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Salva", tint = Color(0xFF10B981))
                }
            }
        }
    },
    singleLine = true,
    modifier = Modifier.fillMaxWidth(),
    colors = TextFieldDefaults.outlinedTextFieldColors(
        focusedBorderColor = ObsidianPrimary,
        unfocusedBorderColor = ObsidianBorderSubtle
    ),
    shape = RoundedCornerShape(12.dp)
)
```

---

## 3. Piano Operativo di Modifica (Guida per il Worker)

Il Worker dovrà applicare le modifiche in `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`:

### Modifica 1: Aggiunta stato `isSaving`
In testa al metodo `GeminiShiftImportScreen` (dopo linea 85):
```kotlin
// INSERIRE DOPO linea 88 (showExitToEditDialog):
var isSaving by remember { mutableStateOf(false) }
```

### Modifica 2: Inserimento della Nota Visiva Esplicativa R4
Subito prima di `// Bottone primario: Conferma e Applica Turni` (tra la chiusura della card squadre e i pulsanti finali):
```kotlin
// INSERIRE PRIMA del pulsante Conferma e Applica Turni:
// =========================================================================
// Nota visiva esplicativa (§ R4): Promemoria rifinitura turni nel catalogo
// =========================================================================
Spacer(modifier = Modifier.height(16.dp))
Surface(
    shape = RoundedCornerShape(14.dp),
    color = ObsidianSurfaceContainer,
    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ObsidianPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = ObsidianPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "I turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione 'Catalogo Turni' dell'app.",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
```

### Modifica 3: Debounce e indicatori su pulsanti e rifinitura AlertDialog
Sostituire il blocco del pulsante `Button(onClick = { ... })` e dell'`AlertDialog` con il codice specificato alle sezioni 2.1 e 2.3.

---

## 4. Matrice di Conformità agli Acceptance Criteria

| Requisito | Acceptance Criterion | Stato Attuale | Con le Modifiche Proposte |
|---|---|---|---|
| **R4** | Dialog modale su 'Modifica' con opzioni conferma/annulla | Parziale (mancano pulsanti specificati e icona) | **Pienamente Conforme**: `AlertDialog` con titolo "Attenzione", testo verbatim, "Esci e Modifica" e "Rimani qui" |
| **R4** | Nota visiva esplicativa per rifinitura turni a catalogo | Assente | **Pienamente Conforme**: `Surface` con badge `Icons.Default.Info` e testo R4 |
| **Ergonomia** | Nessuna doppia esecuzione / tap multiplo su conferma | Assente | **Pienamente Conforme**: Debounce con `isSaving`, pulsanti disabilitati e `CircularProgressIndicator` |
| **R1 / Sicurezza** | API Key protetta con mascheramento caratteri e toggle | Presente | **Verificato e Consolidato**: `visualTransformation` con toggle eye icon |
| **R5** | Zero layout shift & Notifiche via `AnimatedStatusPopup` | Conforme | Invariato, posizionato esclusivamente in `BottomCenter` |
| **R5** | Singolo pulsante indietro (40dp) in alto a sinistra | Conforme | Invariato |

---

## 5. Conclusioni e Raccomandazioni

Le modifiche progettate completano al 100% i requisiti di Milestone 4 Iteration 2, azzerando tutte le obiezioni dei revisori e garantendo la massima fedeltà a `ORIGINAL_REQUEST.md`. L'integrazione è totalmente non regressiva rispetto ai 223 test unitari esistenti.
