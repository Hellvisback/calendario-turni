# Dispatch — Challenger 2 (Milestone 4 Iteration 2 Gate: Build & Test Suite Stress)

## Mission
Verificare empiricamente da linea di comando che l'intera suite di test del progetto (Tiers 1-4) e la compilazione completa dell'APK di debug terminino con successo senza alcun errore di memoria o regressione.

## Input Files
1. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md` (leggi prima di qualsiasi operazione!)
2. `c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md`
3. `c:\Users\marco\Antigravity Progetti\Calediario Turni\TEST_READY.md`
4. `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_iter2\handoff.md`

## Compito
- Esegui empiricamente i comandi:
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat --stop`
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test` (verifica che tutti i 223+ test siano passati con 0 fallimenti)
  - `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug` (verifica che l'APK `app-debug.apk` sia generato con successo)
- Ispeziona il file APK generato (dimensione, data, validità).
- Scrivi handoff.md con verdetto (APPROVE o REJECT) e invia notifica formale.


## 2026-10-15T01:41:34Z
Sei Challenger 2 per il Gate di Milestone 4 Iteration 2 (Build & Test Suite Stress).
Il tuo working directory dedicato è:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_iter2_2

Devi leggere OBBLIGATORIAMENTE il file dei requisiti originali prima di qualsiasi azione:
c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\ORIGINAL_REQUEST.md

Leggi anche:
- c:\Users\marco\Antigravity Progetti\Calediario Turni\PROJECT.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\TEST_READY.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_iter2_2\DISPATCH.md
- c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m4_iter2\handoff.md

Esegui lo stress test empirico di compilazione e test suite:
1. Esegui:
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat --stop
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
   Tutti i 223+ test DEVONO risultare verdi (0 failed, 0 errors).
2. Esegui:
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
   Il build deve completare con BUILD SUCCESSFUL e generare l'APK (app/build/outputs/apk/debug/app-debug.apk).
Scrivi handoff.md con verdetto (APPROVE o REJECT) in c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\challenger_m4_iter2_2\handoff.md e invia un messaggio a me.
