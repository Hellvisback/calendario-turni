# Report di Analisi Architettura UI, Navigazione e Design System
**Progetto**: Calendario Turni (RFI Pro / SuperShift Evolution)  
**Modulo**: `android-app/app/src/main/java/com/turni/calendario/`  
**Autore**: Explorer 2 (Codebase Explorer - UI Architecture, Navigation & Design System)  
**Data**: 2026-10-14  
**Stato**: Completato — Indagine Read-Only  

---

## 1. Sintesi Esecutiva dell'Indagine

L'applicazione Android **Calendario Turni** è interamente implementata con **Kotlin Jetpack Compose** e **Material 3 Expressive**, adottando una rigorosa coerenza visiva e interattiva basata su due pilastri concettuali:
1. **Obsidian Liquid Glass**: un design system dark OLED profondo (`#080B11`), caratterizzato da superfici fumé satinate, rifrazioni ottiche prismatiche multi-strato (`prismaticRefractionBrush`), caustiche interne e bordi luminescenti.
2. **Riccardo Breccia UX**: un'ergonomia centrata sull'usabilità a una mano nella *Thumb Zone*, una floating dock a lente ottica convessa (`LiquidGlassLensNavBar`), micro-interazioni tattili a molla elastica (`bouncyClickable`, `ElasticStarButton`), la rigorosa disciplina del **singolo pulsante indietro in alto a sinistra** (38-44dp) e l'assoluto divieto di card di stato statiche inline che causano sobbalzi di layout (layout-shifting), sostituite interamente dall'overlay flottante `AnimatedStatusPopup` ancorato in `Alignment.BottomCenter`.

L'indagine ha identificato con precisione l'entry point ideale per la nuova funzionalità **Gemini Shift Import Screen**, l'adeguamento dei componenti del pannello unificato a monte (gestione API Key con mascheramento, visualizzazione miniatura Bitmap nativa, filtro turnista) e ha rilevato una criticità infrastrutturale essenziale: l'`AndroidManifest.xml` corrente è privo del permesso di rete `android.permission.INTERNET`, indispensabile per le future chiamate REST multimodali verso Google Gemini Flash.

---

## 2. Mappa Completa delle Schermate & Architettura di Navigazione

### 2.1 Alberatura delle Schermate Jetpack Compose
L'albero dei componenti UI parte da `MainActivity.kt` e si articola come segue:

```text
MainActivity (ComponentActivity)
│
└── CalendarioTurniTheme (MaterialTheme con DarkColorScheme)
    │
    └── MainScreen (Screen Host Principale con Navigation State & Floating Dock)
        │
        ├── [Tab 0: NavItem.Calendario] ──────────────── CalendarScreen
        │   ├── Header Mese & Selettore Viste
        │   ├── Griglia Calendario Mensile Jetpack Compose
        │   ├── DayInspectionCard (Scheda fluida ispezione giorno con Liquid Glass)
        │   └── MagicalActionFeedbackHUD / AnimatedStatusPopup
        │
        ├── [Tab 1: NavItem.Report] ──────────────────── ReportScreen
        │   ├── Selettore Range (Mese vs Anno)
        │   ├── Statistiche Ore Lavorate, Straordinari, Reperibilità e Riposi
        │   └── Card Dettaglio Categorie Turno
        │
        ├── [Tab 2: NavItem.Turni] ───────────────────── TurniModelsScreen
        │   ├── Tab 0: Catalogo Definizioni Turno (M, P, N, Riposo, etc.)
        │   ├── Tab 1: Profili Lavoro & CCNL (WorkProfileEditorScreen)
        │   └── Tab 2: Rotazioni Cicliche (Editor sequenza 28 giorni)
        │
        └── [Tab 3: NavItem.Altro] ───────────────────── SettingsScreen (Hub Centrale Impostazioni)
            │
            ├── Hero Card (Profilo, Quick Status Pills: Drive Cloud, Sincronizzato, Offline Safe)
            ├── Sezione CLOUD & SINCRONIZZAZIONE
            │   └── SettingsSubScreen.CLOUD_BACKUP ─────────── CloudBackupScreen
            │
            ├── Sezione DATI, EXCEL & CONDIVISIONE
            │   ├── SettingsSubScreen.IMPORT_SHIFTS ────────── ImportShiftsScreen ◄── [ENTRY POINT GEMINI IMPORT]
            │   ├── SettingsSubScreen.PDF_EXPORT ───────────── PdfExportScreen
            │   └── SettingsSubScreen.GOOGLE_CALENDAR_SYNC ─── GoogleCalendarSyncScreen
            │
            ├── Sezione REPERIBILITÀ SOFTWARE & SVEGLIE
            │   ├── SettingsSubScreen.REPERIBILITA ─────────── ReperibilitaSettingsScreen
            │   └── SettingsSubScreen.SMART_ALARMS ─────────── SmartAlarmsSettingsScreen
            │
            └── Sezione PREFERENZE CALENDARIO & CONTRATTO
                ├── SettingsSubScreen.HOLIDAYS ─────────────── HolidaysSettingsScreen
                ├── Dialog Inizio Settimana (Lunedì vs Domenica)
                ├── Dialog Formato Orario (24h vs 12h AM/PM)
                └── Toggle Mostra Ex-Turno Sostituito
```

### 2.2 Logica di Navigazione e Transizioni in `MainScreen.kt`
- **Stati di Navigazione Principali**:
  - `selectedItem: NavItem`: memorizza la scheda attiva tra `Calendario`, `Report`, `Turni`, `Altro`.
  - `isSubScreenActive: Boolean`: flag sollevato quando un sotto-schermo (es. all'interno di `SettingsScreen` o un editor in `TurniModelsScreen`) è aperto.
  - `isCalendarEditModeActive: Boolean`: flag sollevato durante la modifica rapida del calendario.
  - `targetRotationToEdit: ShiftRotation?`: parametro opzionale per transitare direttamente alla modifica di una specifica rotazione in `TurniModelsScreen`.
- **Transizioni Orizzontali (Zero Scatti)**:
  Il passaggio tra i 4 tab principali sfrutta `AnimatedContent` con fisica `spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)` e slide bidirezionale calibrata sul 45% della larghezza schermo (`slideInHorizontally` + `fadeIn` / `slideOutHorizontally` + `fadeOut`).
- **Comportamento del Floating Dock (`LiquidGlassLensNavBar`)**:
  La barra di navigazione inferiore è racchiusa in un `AnimatedVisibility` ancorato a `Alignment.BottomCenter`:
  ```kotlin
  AnimatedVisibility(
      visible = !isSubScreenActive && !isCalendarEditModeActive,
      enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(...)) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter)
  )
  ```
  Quando l'utente entra in una sotto-schermata (come `ImportShiftsScreen`), il Floating Dock scompare scorrendo dolcemente verso il basso, liberando il 100% dell'area schermo per l'operazione in corso.
- **Gestione del Tasto Indietro (Back Handling)**:
  - In `MainScreen.kt`: se ci si trova su `Report`, `Turni` o `Altro` a livello root, `BackHandler` reindirizza istantaneamente a `NavItem.Calendario`.
  - In `SettingsScreen.kt`: `BackHandler(enabled = isSubScreenActive) { activeSubScreen = SettingsSubScreen.NONE }` garantisce che il tocco del tasto indietro di sistema chiuda la sub-schermata tornando al menu impostazioni senza uscire dall'app.
  - Nei sub-schermi: ogni schermata figlia (`ImportShiftsScreen`, `CloudBackupScreen`, etc.) implementa `BackHandler(onBack = onDismiss)` e il proprio singolo pulsante freccia in alto a sinistra.

---

## 3. Analisi del Design System "Obsidian Liquid Glass"

L'identità visiva è codificata nei file `com/turni/calendario/ui/theme/Color.kt`, `Theme.kt` e nei componenti avanzati di `TurniUiComponents.kt`.

### 3.1 Palette Cromatica OLED Dark & Gerarchia delle Superfici
| Token Colore | Valore Esadecimale | Ruolo Architetturale & Semantica |
|---|---|---|
| `ObsidianBackground` | `#080B11` | Sfondo OLED profondo a bassissimo consumo energetico |
| `ObsidianSurface` | `#111622` | Superficie primaria di card, pannelli e dialog |
| `ObsidianSurfaceContainer` | `#171E2D` | Superficie secondaria elevata (box interni, campi, righe) |
| `ObsidianSurfaceHigh` | `#20293D` | Controlli elevati, chip interattivi e badge |
| `ObsidianSurfaceHighest` | `#2B3751` | Stato selezionato ad alto rilievo e contrasto |
| `ObsidianBorder` | `#1E273A` | Bordo strutturale discreto per card (spessore 1.dp) |
| `ObsidianBorderSubtle` | `#151B28` | Bordo interno, divisori e profili non attivi (0.8dp) |
| `ObsidianBorderActive` | `#38BDF8` | Bordo ciano luminescente per focus/selezione attiva |

### 3.2 Accenti di Brand & Gradienti Spettacolari
- **Colori Primari**:
  - `ObsidianPrimary`: `#38BDF8` (Azzurro ciano vivido)
  - `ObsidianPrimaryContainer`: `#0284C7` (Blu ciano saturato per bottoni primari)
  - `ObsidianPrimarySubtle`: `#0C4A6E` (Ciano scuro per sfondi badge)
  - `ObsidianSecondary`: `#818CF8` (Lavanda/indaco per accenti secondari)
  - `ObsidianTertiary`: `#34D399` (Verde smeraldo brillante per successi e sync)
- **Gradienti di Rifrazione**:
  - `PrimaryGradient`: orizzontale da `#0284C7` a `#38BDF8`
  - `AmberGradient`: da `#D97706` a `#F59E0B` (avvisi e reperibilità)
  - `EmeraldGradient`: da `#059669` a `#10B981` (conferme e salvataggi)
  - `PurpleGradient`: da `#7C3AED` a `#A855F7` (turni notte)
  - `RoseGradient`: da `#E11D48` a `#FB7185` (azioni distruttive o alert)

### 3.3 Rifrazione Ottica & Glassmorphism (`prismaticRefractionBrush` & `FluidGlassContainer`)
- `prismaticRefractionBrush(highlightColor, dispersionExit)`:
  Genera un gradiente lineare che simula fisicamente la dispersione della luce nel cristallo satinato:
  - Ingresso ciano luminescente (`highlightColor.copy(alpha = 0.95f)`)
  - Cresta speculare zenitale bianco puro (`#EEFFFFFF`)
  - Rifrazione interna lavanda (`#80818CF8`)
  - Uscita cromatica fucsia/magenta (`#EC4899`, alpha 0.65f)
- `FluidGlassContainer`:
  Incapsula una superficie con:
  - Ombreggiatura volumetrica a doppio tono (`spotColor` e `ambientColor` con rifrazione colorata)
  - Bordo prismatico molato da 1.3.dp
  - Corpo a gradiente verticale traslucido (`0xF6111B2C` -> `0xFA0A0E18`)
  - Caustica di diffusione radiale superiore alta 60.dp
  - Cresta speculare orizzontale da 1.3.dp in cima alla card

### 3.4 Tipografia e Accessibilità WCAG
- `TextPrimary` (`#F8FAFC`): Bianco ardesia ad altissima luminosità (> 98%), contrasto > 15:1 su `ObsidianBackground`.
- `TextSecondary` (`#94A3B8`): Grigio ardesia medio per etichette secondarie, contrasto > 7:1.
- `TextMuted` (`#64748B`): Grigio attenuato per timestamp e note legali, contrasto > 4.5:1.

---

## 4. Analisi dei Pattern Ergonomici "Riccardo Breccia UX"

L'esperienza d'uso segue i principi cardine di Riccardo Breccia per dispositivi mobili moderni:

### 4.1 Thumb Zone Ergonomics (Zona Ergonomica del Pollice)
- I pulsanti di azione primaria ("Sfoglia File", "Analizza", "Salva nel catalogo", "Applica i Miei Turni") occupano l'intera larghezza (`fillMaxWidth()`), con altezze ergonomiche tra 42.dp e 48.dp, raggi di curvatura arrotondati (12-14.dp) e target di tocco ampiamente superiori agli standard minimi di 48x48dp.
- A fondo schermata è sempre presente un distanziatore di rispetto (`Spacer(modifier = Modifier.height(100.dp))`), che permette all'utente di scrollare l'intero contenuto fin sopra il floating dock, evitando che le dita interferiscano con la barra di sistema o con il bordo inferiore del dispositivo.

### 4.2 Floating Navigation Dock (`LiquidGlassLensNavBar`)
- Posizionata in basso al centro (`Alignment.BottomCenter`) con padding dalle barre di navigazione (`navigationBarsPadding()`).
- Simula una "Lente d'Ingrandimento" in vetro ottico puro che deborda verticalmente di 10.dp rispetto al corpo della barra (dimensione 70.dp x 60.dp contro i 56.dp di altezza della barra).
- L'icona e l'etichetta del tab attivo vengono ingrandite dinamicamente del +18% (scala 1.18x) tramite interpolazione di prossimità continua.
- Supporta sia il tocco istantaneo (tap) sia lo scorrimento continuo (drag) con feedback aptico a scatto (`TextHandleMove`) al passaggio su ciascun tab.

### 4.3 Micro-interazioni Elastiche e Feedback Tattile
- `Modifier.bouncyClickable`: comprime l'elemento allo scale `0.92f` durante la pressione, aziona la vibrazione aptica istantanea e rimbalza al rilascio con una molla fluida (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMediumLow`).
- `ElasticStarButton`: pulsante a stella per il turno predefinito con animazione snap allo `0.72f` e rilascio elastico a molla; privo di etichette testuali ridondanti (lo stato è interamente demandato all'icona dorata piena `Icons.Default.Star` vs outline `Icons.Default.StarBorder`).

### 4.4 Rigorosa Disciplina del Singolo Tasto Indietro
- Nelle schermate secondarie è ammesso un **unico pulsante indietro** situato in alto a sinistra nella TopBar (`Surface(onClick = onDismiss, shape = CircleShape, modifier = Modifier.size(40.dp..44.dp))`).
- È tassativamente vietato posizionare pulsanti "Indietro", "Chiudi" o "Annulla" duplicati sul fondo dello schermo, per evitare tocchi accidentali durante le interazioni a una mano.

---

## 5. Implementazione di `AnimatedStatusPopup`

Il componente `AnimatedStatusPopup.kt` (`com.turni.calendario.ui.components`) è lo standard di notifica dell'applicazione.

### 5.1 Struttura e Ancoraggio
- **Posizionamento in Overlay**:
  Viene sempre istanziato all'interno di un `Box(modifier = Modifier.fillMaxSize())`, tipicamente come ultimo elemento figlio in z-index.
  È ancorato in basso tramite:
  ```kotlin
  AnimatedStatusPopup(
      message = feedbackMessage,
      type = popupType,
      onDismiss = { feedbackMessage = null },
      modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 16.dp)
  )
  ```
- **Assoluta Neutralità di Layout**:
  Non essendo inline nel flusso verticale (`Column`), la sua comparsa o scomparsa **non deforma, non sposta e non fa sobbalzare** i campi di testo o i bottoni della schermata (zero layout-shifting).

### 5.2 Tipologie di Popup (`PopupType`)
1. **`PopupType.SUCCESS`**:
   - Sfondo verde profondo (`#064E3B`), bordo smeraldo (`#10B981`), icona ciano/menta (`#34D399`, `Icons.Default.CheckCircle`).
   - Genera un'animazione speciale di coriandoli fisici (`ConfettiParticle`) tramite un layer `Canvas` a 45 particelle che eruttano verso l'alto con effetto gravità e rotazione continua.
2. **`PopupType.ERROR`**:
   - Sfondo rosso bordeaux (`#7F1D1D`), bordo rosso acceso (`#EF4444`), icona rosa corallo (`#FCA5A5`, `Icons.Default.Error`).
   - Aziona una micro-animazione di tremolio elastico (haptic shake a 3 cicli: +12dp, -12dp, 0dp) sull'asse orizzontale.
3. **`PopupType.WARNING`**:
   - Sfondo ambra scuro (`#78350F`), bordo oro (`#F59E0B`), icona ambra chiara (`#FDE68A`, `Icons.Default.Warning`).
4. **`PopupType.INFO`**:
   - Sfondo oceano profondo (`#0C4A6E`), bordo ciano (`#38BDF8`), icona cielo (`#7DD3FC`, `Icons.Default.Info`).

### 5.3 Animazioni di Entrata e Uscita
- **Entrata**: `slideInVertically(initialOffsetY = { fullHeight -> fullHeight }, spring(dampingRatio = MediumBouncy, stiffness = MediumLow)) + fadeIn(220ms)`.
- **Uscita**: `slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }, spring(NoBouncy, Medium)) + fadeOut(180ms)`.
- **Auto-Dismiss**: timer coroutine configurabile (default `4200ms`) con possibilità di chiusura manuale immediata tramite tasto `Icons.Default.Close`.

---

## 6. Progettazione della Nuova Schermata "Gemini Shift Import Screen"

### 6.1 Entry Point Ideale
- **Punto d'Ingresso Primario**: `SettingsScreen.kt` all'interno della sezione `"DATI, EXCEL & CONDIVISIONE"`, collegato al valore enum esistente `SettingsSubScreen.IMPORT_SHIFTS`.
- **Aggiornamento Voce di Menu**:
  ```kotlin
  SettingsRow(
      icon = Icons.Default.AutoAwesome, // Icona stella magica/AI
      title = "Importazione Turni Intelligente (Gemini Flash)",
      subtitle = "Scansione multimodale visiva da PDF, foto e screenshot",
      onClick = { activeSubScreen = SettingsSubScreen.IMPORT_SHIFTS }
  )
  ```
- **Punto d'Ingresso Secondario (Opzionale)**: In `TurniModelsScreen.kt` (nella tab Rotazioni) o in un banner di primo avvio per il setup immediato a zero frizione.

### 6.2 Architettura dei Componenti del Layout Unificato (Conforme a R5)

La schermata sostituirà/evolverà `ImportShiftsScreen.kt` con i seguenti blocchi funzionali integrati:

```text
┌────────────────────────────────────────────────────────────────┐
│ [←]  Importazione Turni Gemini AI                              │  Top Bar (Unico tasto back)
│      Visione multimodale con Gemini Flash                      │
├────────────────────────────────────────────────────────────────┤
│ ┌─ CARD PANNELLO UNIFICATO A MONTE (ObsidianSurface) ────────┐ │
│ │ [★] CONFIGURAZIONE & CARICAMENTO PROSPETTO                 │ │
│ │                                                            │ │
│ │ 1. Inserimento / Modifica Gemini API Key                  │ │
│ │    [ ••••••••••••••••••••  (👁) (Salva/Modifica) ]          │ │
│ │    (Salvataggio crittografato / master state sicuro)        │ │
│ │                                                            │ │
│ │ 2. Filtro Nominativo Lavoratore a Monte                    │ │
│ │    [ Es. Graziani Marco                      (X) ]         │ │
│ │                                                            │ │
│ │ 3. Selettore Documento & Immagine                           │ │
│ │    [ Sfoglia Documento (Foto, Screenshot, PDF) ]           │ │
│ │                                                            │ │
│ │ 4. Box Miniatura / Anteprima Grafica                        │ │
│ │    ┌────────────────────────┐                              │ │
│ │    │ [Miniatura Bitmap PDF] │  MOD_PR_OTTOBRE.pdf          │ │
│ │    │ (PdfRenderer nativo)   │  1840x2600 px • 1 Pagina     │ │
│ │    └────────────────────────┘                              │ │
│ │                                                            │ │
│ │ 5. Azione Primaria di Analisi                               │ │
│ │    [ ⚡ Analizza con Gemini Flash (Micro-animazione) ]     │ │
│ └────────────────────────────────────────────────────────────┘ │
│                                                                │
│ ┌─ CARD RISULTATI ESTRAZIONE (Condizionale post-analisi) ────┐ │
│ │ Periodo: 21/09/2026 - 18/10/2026 • 4 Orari Identificati    │ │
│ │ [M: 06:00-13:36] [P: 14:00-21:36] [N: 22:00-05:36]...       │ │
│ │                                                            │ │
│ │ SQUADRE E ROTAZIONI RILEVATE:                              │ │
│ │ ┌────────────────────────────────────────────────────────┐ │ │
│ │ │ (•) Squadra B (TUA SQUADRA - Rilevato "Graziani M.")  [★]│ │ │
│ │ │     28 giorni • 13 turnisti • Reperibilità attiva      │ │ │
│ │ │     [P] [P] [R] [N] [N] [M] [R] [P] [P] [N] [N]...     │ │ │
│ │ │     [ Salva nel catalogo ]  [ Modifica ]               │ │ │
│ │ └────────────────────────────────────────────────────────┘ │ │
│ │ ┌────────────────────────────────────────────────────────┐ │ │
│ │ │ ( ) Squadra C                                         [☆]│ │ │
│ │ │     28 giorni • 12 turnisti                            │ │ │
│ │ └────────────────────────────────────────────────────────┘ │ │
│ └────────────────────────────────────────────────────────────┘ │
│                                                                │
│ ┌─ AZIONI FINALI DI CHIUSURA (ObsidianSurface) ──────────────┐ │
│ │ [ Salva nel catalogo ]  [ Modifica (con Dialog Avviso) ]   │ │
│ └────────────────────────────────────────────────────────────┘ │
│                                                                │
│ [Spacer 100.dp per rispetto Thumb Zone]                        │
└────────────────────────────────────────────────────────────────┘
 [ AnimatedStatusPopup in overlay ancorato a BottomCenter ]
```

---

## 7. Rilevazione Criticità di Sistema (AndroidManifest)

Durante l'ispezione di `android-app/app/src/main/AndroidManifest.xml`, è emerso che i permessi attualmente dichiarati sono:
- `com.android.alarm.permission.SET_ALARM`
- `android.permission.SCHEDULE_EXACT_ALARM`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.VIBRATE`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.READ_CALENDAR`
- `android.permission.WRITE_CALENDAR`
- `android.permission.GET_ACCOUNTS`

⚠️ **CRITICITÀ INDIVIDUATA**: Il file **NON** include `<uses-permission android:name="android.permission.INTERNET" />`.  
Poiché l'integrazione di Google Gemini Flash richiede comunicazioni HTTPS REST verso gli endpoint di Google AI (`generativelanguage.googleapis.com`), questo permesso è **vincolante e obbligatorio** affinché le chiamate di rete non falliscano a runtime con `SecurityException` o `UnknownHostException`.

---

## 8. Specifiche Tecniche dei Componenti Jetpack Compose Proposti

### 8.1 Gestione API Key con Mascheramento e Persistenza Sicura
```kotlin
@Composable
fun GeminiApiKeyInput(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    isKeySaved: Boolean
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(!isKeySaved) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Google Gemini API Key",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (isKeySaved && !isEditing) {
                TextButton(
                    onClick = { isEditing = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Modifica Chiave", fontSize = 11.sp, color = ObsidianPrimary)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            enabled = isEditing,
            placeholder = { Text("Incolla qui la tua API Key di Google AI Studio...", fontSize = 11.5.sp, color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Key, contentDescription = null, tint = if (isKeySaved) Color(0xFF10B981) else ObsidianPrimary, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Mostra/Nascondi",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = ObsidianPrimary,
                unfocusedBorderColor = if (isKeySaved) Color(0xFF10B981).copy(alpha = 0.5f) else ObsidianBorderSubtle
            )
        )
    }
}
```

### 8.2 Rendering e Anteprima Miniatura Immagine/PDF
Poiché la codebase non include librerie di terze parti come Coil o Glide, l'anteprima sfrutta direttamente le API native di Android e Jetpack Compose:
- Per i PDF: apertura del file tramite `android.graphics.pdf.PdfRenderer` (nativo da Android 5.0+), rendering della prima pagina su un `android.graphics.Bitmap` ARGB_8888 ad alta risoluzione.
- Per le immagini (JPEG, PNG, WEBP): decodifica con `BitmapFactory.decodeStream(contentResolver.openInputStream(uri))`.
- Visualizzazione in Compose: `androidx.compose.foundation.Image(bitmap = previewBitmap.asImageBitmap(), contentDescription = "Anteprima", modifier = Modifier.clip(RoundedCornerShape(12.dp))...)` a zero dipendenze esterne.

### 8.3 Indicatore di Caricamento Elastico & Feedback
Durante l'elaborazione multimodale, il pulsante primario disabilita le interazioni e mostra:
```kotlin
if (isProcessing) {
    CircularProgressIndicator(
        color = Color.White,
        strokeWidth = 2.dp,
        modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text("Analisi Visiva Gemini Flash in corso...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
} else {
    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text("Analizza con Gemini Flash Vision", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}
```

### 8.4 Tasto Stella Predefinito Toggleabile con Micro-Animazione Spring
L'implementazione esistente di `ElasticStarButton` (con `scale.snapTo(0.72f)` e rimbalzo elastico a molla su `isDefault`) rispetta al 100% il requisito R3 (commutabile on/off senza testo ridondante) e deve essere mantenuta e riutilizzata per ciascuna squadra estratta da Gemini Flash.

---

## 9. Conclusioni per l'Implementazione

1. **Continuità Architetturale**: L'aggiornamento della schermata di importazione si innesta in modo fluido e organico all'interno di `SettingsScreen.kt` (`SettingsSubScreen.IMPORT_SHIFTS`), preservando l'intera navigazione a 4 tab e il floating dock di `MainScreen.kt`.
2. **Conformità Stilistica Rigorosa**: L'adozione dei token `ObsidianBackground`, `ObsidianSurface`, `ObsidianBorder` e l'uso esclusivo di `AnimatedStatusPopup` garantiscono l'assenza totale di sfarfallii o sobbalzi di layout.
3. **Prerequisiti Operativi**: Prima dell'avvio della fase di implementazione del client di rete multimodale, è imperativo aggiungere `<uses-permission android:name="android.permission.INTERNET" />` all'`AndroidManifest.xml`.
