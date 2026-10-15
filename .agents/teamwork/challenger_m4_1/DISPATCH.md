# Dispatch: Challenger 1 (Milestone 4 Gate — UX & Input Stress Verification)

## Obiettivo
Sottoporre a stress test empirico e avversariale l'interfaccia utente `GeminiShiftImportScreen.kt`, i flussi di input, il rendering e le animazioni.

## File da leggere obbligatoriamente
1. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md`
2. `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
3. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\handoff.md`
4. `c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\src\main\java\com\turni\calendario\ui\screens\GeminiShiftImportScreen.kt`

## Verifiche di Stress Empiriche
1. Verifica che non vi siano card di stato statiche inline che spostino gli elementi della UI; solo `AnimatedStatusPopup` con anchor bottom.
2. Verifica che il pulsante indietro sia esattamente 1 (in alto a sinistra, 40dp) e che non esista alcun duplicato in basso.
3. Verifica la presenza dello spacer di 100dp in fondo alla colonna scorrevole.
4. Verifica la robustezza dell'animazione elastica di `ElasticStarButton` e la corretta gestione del toggle neutro (deselezione).
5. Esegui la suite di test completa:
   `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test`
6. Esegui la compilazione Android:
   `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug`

## Requisiti di Output
Redigi il report di handoff con verdetto esplicito (APPROVE o REJECT) in:
`c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_1\handoff.md`
Invia il messaggio di notifica all'orchestratore via `send_message`.


## 2026-10-14T23:15:23Z
Sei Challenger 1 per il Gate di Milestone 4 (UX & Input Stress).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_1

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_1\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_replacement\handoff.md
- android-app/app/src/main/java/com/turni/calendario/ui/screens/GeminiShiftImportScreen.kt

Verifica avversarialmente i vincoli di non-regressione e stress UX:
1. Zero card inline statiche di errore/successo: verifica che non esistano elementi composable statici verdi o rossi che spostino il layout durante la visualizzazione di feedback.
2. Unico tasto back in alto a sinistra (40dp) con BackHandler; verifica assenza di tasti indietro in fondo.
3. Thumb Zone Spacer 100dp presente.
4. Esegui la verifica empirica:
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
Scrivi handoff.md con verdetto (APPROVE o REJECT) in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_1\handoff.md e invia un messaggio a me.
