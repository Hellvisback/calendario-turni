# BRIEFING — 2026-10-14T21:15:00Z

## Mission
Implementare la schermata unificata `GeminiShiftImportScreen.kt` e integrarla in `SettingsScreen.kt`, rispettando rigorosamente lo stile Obsidian Liquid Glass e l'ergonomia Riccardo Breccia UX.

## 🔒 My Identity
- Archetype: implementer
- Roles: implementer, qa, specialist
- Working directory: c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4
- Original parent: 90066a2c-0ee0-47dc-9cce-f552c7e66ada
- Milestone: Milestone 4 (Liquid Glass UI & Setup Experience)

## 🔒 Key Constraints
- File esclusivi di scrittura:
  - `android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt`
  - `android-app/app/src/main/java/com/turni/calendario/ui/screens/SettingsScreen.kt`
  - `android-app/app/src/test/java/com/turni/calendario/GeminiShiftImportUiTest.kt`
- Obsidian Liquid Glass & Riccardo Breccia UX:
  - Singolo pulsante indietro in alto a sinistra (40dp) con BackHandler. Nessun pulsante indietro in basso.
  - Zero card statiche inline di notifica: usare esclusivamente `AnimatedStatusPopup` ancorato in `Alignment.BottomCenter`.
  - Spacer 100dp in fondo alla scroll view per la Thumb Zone.
- Pannello unificato a monte:
  - API Key mascherata con modifica/salva via `SecurePreferencesManager`.
  - Nome utente con auto-salvataggio.
  - Selezione Immagini (JPEG, PNG, WEBP) e PDF (`application/pdf`) con miniatura anteprima via `DocumentProcessor.createThumbnail`.
  - Bottone "Analizza con Gemini Flash Vision" con loading indicator e chiamata asincrona su `Dispatchers.IO`.
- Risultati & Selezione Squadra:
  - Riepilogo turni estratti (shortCode, orari, colori).
  - Card squadre con lunghezza ciclo, lista colleghi e stella toggleabile (`ElasticStarButton`) pre-selezionata con `GeminiShiftMapper.matchUserSquad`.
  - Conferma turni registrata in `ShiftRepository` con popup di successo.
- Verifica obbligatoria:
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test` -> BUILD SUCCESSFUL
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL

## Current Parent
- Conversation ID: 90066a2c-0ee0-47dc-9cce-f552c7e66ada
- Updated: 2026-10-14T21:15:00Z

## Task Summary
- **What to build**: Schermata `GeminiShiftImportScreen.kt`, integrazione in `SettingsScreen.kt`, test unitari/UI in `GeminiShiftImportUiTest.kt`.
- **Success criteria**: Schermata completa e funzionale, zero banner statici, test e build con esito verde al 100%.
- **Interface contracts**: `SecurePreferencesManager`, `GeminiApiClient`, `DocumentProcessor`, `GeminiShiftMapper`.
- **Code layout**: `PROJECT.md` § Code Layout.

## Change Tracker
- **Files modified**: Nessuno ancora.
- **Build status**: Da verificare.
- **Pending issues**: Nessuno.

## Quality Status
- **Build/test result**: In attesa prima esecuzione.
- **Lint status**: 0 violazioni.
- **Tests added/modified**: In attesa creazione test suite `GeminiShiftImportUiTest.kt`.

## Loaded Skills
- None required for this phase.

## Key Decisions Made
- `GeminiShiftImportScreen` userà `rememberLauncherForActivityResult` per selezionare immagini e PDF.
- Miniatura mostrata con `Bitmap.asImageBitmap()` in `Box` stilizzato con `FluidGlassContainer` o bordi Obsidian.
- Pulsante indietro in alto a sinistra (40dp) e `BackHandler` per intercettare il tasto indietro del sistema.
- `ElasticStarButton` integrato con micro-animazione a molla elastica e feedback visivo.
- Dialog di avviso per "Modifica" conforme a R4.
- Notifiche esclusivamente con `AnimatedStatusPopup(Alignment.BottomCenter)`.

## Artifact Index
- `.agents/teamwork/worker_m4/DISPATCH.md` — Assegnazione e requisiti di dispatch
- `.agents/teamwork/worker_m4/BRIEFING.md` — Memoria di lavoro corrente
- `.agents/teamwork/worker_m4/progress.md` — Liveness heartbeat e avanzamento
- `.agents/teamwork/worker_m4/handoff.md` — Report finale 5 componenti
