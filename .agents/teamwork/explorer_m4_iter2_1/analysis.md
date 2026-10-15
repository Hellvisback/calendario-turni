# Technical Analysis Report: Gradle Memory & Compilation Tuning (M4 Iteration 2)

**Author**: Explorer 1 (Milestone 4 Iteration 2)  
**Target Path**: `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\explorer_m4_iter2_1\analysis.md`  
**Date**: 2026-10-15  
**Topic**: Root Cause Analysis of `OutOfMemoryError: Java heap space`, Optimal `gradle.properties` Parameters, and Daemon Cleanup Runbook

---

## 1. Executive Summary

Durante il Gate di Milestone 4, la compilazione del progetto Android nativo Jetpack Compose è fallita con:
`e: java.lang.OutOfMemoryError: Java heap space` sulla task `:app:compileDebugKotlin` dopo ~12 minuti di esecuzione.
L'indagine tecnica condotta ha rilevato che:
1. **Separazione di Processi**: Gradle esegue la compilazione Kotlin tramite un processo demone dedicato e separato (**Kotlin Compile Daemon**) orchestrato dal worker `GradleKotlinCompilerWorkAction`.
2. **Carico Jetpack Compose**: La base di codice conta 32 file Kotlin (>1.1 MB complessivi), con file particolarmente voluminosi (es. `TurniModelsScreen.kt` di 207 KB, `CalendarScreen.kt` di 113 KB, `GeminiShiftImportScreen.kt` con >1000 righe). Il plugin Compose Compiler 1.2.0 esegue trasformazioni IR profonde (sintesi parametri `$composer`, wrapping memoize, restart groups, inferenza stabilità), determinando un picco di allocazione heap superiore ai 2048 MB.
3. **Impostazioni Memoria Insufficienti**: In precedenza, `gradle.properties` definiva solo `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8` e mancava del tutto la direttiva `kotlin.daemon.jvmargs`, oppure la limitava a 2048m. Questo provocava thrashing continuo del Garbage Collector e l'esaurimento della memoria heap della JVM del demone Kotlin.
4. **Verifica Empirica**: Portando `kotlin.daemon.jvmargs` e `org.gradle.jvmargs` a `-Xmx4096m`, la task `:app:compileDebugKotlin` è passata da fallimento OOM in 11m 59s a **`BUILD SUCCESSFUL in 6m 23s`** (tempo quasi dimezzato, zero crash).
5. **Sessioni Orfane e Lock**: I fallimenti anomali lasciano file di lock orfani in `$env:LOCALAPPDATA\kotlin\daemon\` (`.run` e `.tmp`) e nel repository in `android-app\build\kotlin\sessions\` (`.salive`), che generano il warning `Detected multiple Kotlin daemon sessions at build\kotlin\sessions` e instabilità nelle build successive.

---

## 2. Root Cause Analysis (Deep Dive)

### 2.1 Architettura Multi-Processo di Gradle e Kotlin
Nel toolset Android moderno (AGP 7.2.2 + Kotlin Gradle Plugin 1.7.0):
- **Gradle Daemon (Host Process)**: Esegue il core di Gradle, la risoluzione delle dipendenze, il grafo dei task, l'Android Gradle Plugin (AGP), AAPT2 e D8/R8. I suoi argomenti JVM sono controllati da:
  ```properties
  org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
  ```
- **Kotlin Compile Daemon (Worker Process)**: Viene spawnato come processo JVM indipendente per velocizzare la compilazione incrementale. I suoi argomenti JVM sono controllati ESCLUSIVAMENTE da:
  ```properties
  kotlin.daemon.jvmargs
  ```
  Se `kotlin.daemon.jvmargs` non è impostato in `gradle.properties`, il plugin Kotlin assegna un default restrittivo o eredita parzialmente argomenti non dimensionati per carichi pesanti di compilazione Compose.

### 2.2 Il Carico Computazionale del Compilatore Jetpack Compose
In Kotlin 1.7.0 con Compose Compiler Extension 1.2.0 (`androidx.compose.compiler:compiler:1.2.0`):
- Ogni funzione `@Composable` subisce una riscrittura dell'AST (Abstract Syntax Tree) e della rappresentazione intermedia IR (Kotlin IR Backend).
- Vengono sintetizzati parametri aggiuntivi (`$composer: Composer`, `$changed: Int`), inserite chiamate a `$composer.startRestartGroup` / `endRestartGroup`, create classi anonime per le lambda e tracciati tutti gli stati mutabili per la stabilità dei parametri.
- La combinazione di 32 file Kotlin, tra cui `TurniModelsScreen.kt` (207.127 byte), `CalendarScreen.kt` (113.455 byte), `TurniUiComponents.kt` (83.019 byte) e la nuova schermata unificata `GeminiShiftImportScreen.kt` (>1000 righe) crea un volume eccezionale di nodi AST contemporaneamente in memoria durante la fase di analisi semantica e generazione bytecode.

### 2.3 Garbage Collector Thrashing (G1GC vs ParallelGC)
Su Java 11 (OpenJDK 11.0.12 JRE di Android Studio), il Garbage Collector predefinito è **G1GC** (`-XX:+UseG1GC`).
- G1GC è progettato per applicazioni server a bassa latenza e frammenta la memoria in regioni, mantenendo strutture dati di overhead (Remembered Sets, Card Tables, concurrency tracking).
- Durante una compilazione batch intensiva, milioni di piccoli oggetti AST e stringhe vengono allocati e scartati in pochi secondi.
- Quando l'heap è ristretto a 2048 MB, G1GC tenta ripetutamente cicli concorrenti e "Full GC" consecutivi per recuperare pochi megabyte di memoria.
- Questo fenomeno spiega perché il compilatore rimaneva bloccato per 11 minuti e 59 secondi in un loop di CPU al 100% prima che la JVM dichiarasse l'esaurimento definitivo:
  ```
  e: java.lang.OutOfMemoryError: Java heap space
  > Task :app:compileDebugKotlin FAILED
  FAILURE: Build failed with an exception.
  * What went wrong:
  Execution failed for task ':app:compileDebugKotlin'.
  > A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction
     > Not enough memory to run compilation. Try to increase it via 'gradle.properties':
       kotlin.daemon.jvmargs=-Xmx<size>
  BUILD FAILED in 11m 59s
  ```

---

## 3. Evidenza Empirica e Dimensionamento Hardware

### 3.1 Risorse di Sistema Rilevate
- **Sistema Operativo**: Windows 11 (x64)
- **RAM Totale di Sistema**: 16.707.608 KB (~16,0 GB)
- **RAM Fisica Libera**: ~8.280.840 KB (~8,2 GB)
- **Runtime Java**: OpenJDK 11.0.12 64-Bit (`C:\Program Files\Android\Android Studio\jre`)

Con oltre 8 GB di memoria fisica libera disponibile, l'allocazione di **4096 MB** sia a Gradle che al demone Kotlin è completamente sostenibile dall'hardware, garantisce un ampio margine di manovra e scongiura qualsiasi rischio di paging/swapping su disco.

### 3.2 Benchmark Sperimentale
| Configurazione | Esito `:app:compileDebugKotlin` | Tempo di Esecuzione | Esito Heap |
|---|---|---|---|
| Heap 2048m (Default) | **FAILED** | 11m 59s | `OutOfMemoryError: Java heap space` |
| Heap 4096m (`-Xmx4096m`) | **BUILD SUCCESSFUL** | 6m 23s | Zero errori OOM, compilazione fluida |

---

## 4. Specifiche Ottimali per `android-app/gradle.properties`

### 4.1 Definizione Configurazione Finale
Il file `android-app/gradle.properties` deve essere strutturato nel modo seguente:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8
```

### 4.2 Rationale Dettagliato per Ciascun Parametro

1. **`org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`**:
   - `-Xmx4096m`: Assegna 4096 MB al processo principale del demone Gradle. Fornisce l'headroom necessario per task concorrenti (AAPT2, merge resources, desugaring, D8 dexing) e previene OOM durante l'esecuzione contemporanea di test unitari JVM.
   - `-Dfile.encoding=UTF-8`: Forza la codifica universale UTF-8 per la JVM di Gradle. Su Windows (dove il default di sistema è spesso `windows-1252`), previene la corruzione di caratteri accentati (es. "è", "à", "perderai queste informazioni"), simboli grafici Unicode o emoji usati nel codice e nelle notifiche.

2. **`kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8`**:
   - `-Xmx4096m`: Assegna 4096 MB dedicati al demone del compilatore Kotlin. Elimina la causa radice dell'`OutOfMemoryError` nella task `:app:compileDebugKotlin`.
   - `-XX:+UseParallelGC`: Abilita il Parallel Garbage Collector (Throughput Collector). A differenza di G1GC, il Parallel Collector concentra l'attività di GC su raccolte parallele multi-thread a stop-the-world veloci e aggressive, ideali per azzerare la frammentazione e massimizzare il throughput durante le fasi di analisi sintattica e trasformazione IR di Compose.
   - `-Dfile.encoding=UTF-8`: Assicura che anche il compilatore Kotlin legga e interpreti i sorgenti con codifica UTF-8, garantendo perfetta coerenza cross-platform.

3. **`android.useAndroidX=true` & `android.nonTransitiveRClass=true`**:
   - Direttive AGP fondamentali per l'ecosistema Jetpack Compose e per limitare la dimensione dei namespace delle risorse `R`, riducendo ulteriormente la pressione sull'heap.

---

## 5. Sequenza di Bonifica Demoni Orfani e Lock (Cleanup Runbook)

### 5.1 Fenomeno dei Residui Orfani su Windows
Durante i fallimenti per OOM o interruzioni manuali:
1. **Demoni Gradle Zombie**: I processi `java.exe` possono restare attivi in stato di blocco o `STOPREQUESTED`.
2. **File Marker Utente**: In `$env:LOCALAPPDATA\kotlin\daemon\`, permangono file `.run` e `.tmp` (es. `kotlin-daemon.2026-10-14T23-48-02...17808.run`). I nuovi demoni Kotlin tentano di connettersi alle porte orfane specificate in questi marker, provocando ritardi di timeout socket.
3. **Sessioni Locali di Progetto**: In `android-app\build\kotlin\sessions\`, rimangono file `.salive` (es. `kotlin-compiler-...salive`). Questo genera il warning di compilazione:
   `w: Detected multiple Kotlin daemon sessions at build\kotlin\sessions`
4. **Cache Incrementale Corrotta**: La cartella `android-app\app\build\kotlin\` può contenere classi semilavorate o indici corrotti a causa dell'arresto violento per OOM.

### 5.2 Sequenza di Bonifica PowerShell per il Worker M4 Iteration 2
Di seguito la sequenza esatta di comandi PowerShell per effettuare il ripristino a caldo:

```powershell
# ==============================================================================
# RUNBOOK BONIFICA DEMONI GRADLE/KOTLIN E CACHE ORFANE
# ==============================================================================

# 1. Definire JAVA_HOME puntando al JRE stabile di Android Studio
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"

# 2. Arresto ordinato dei demoni Gradle
Set-Location "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app"
.\gradlew.bat --stop

# 3. Terminazione forzata di eventuali processi Java/Kotlin/Gradle orfani su Windows
Get-Process -Name java, javaw -ErrorAction SilentlyContinue | Where-Object {
    $_.Path -like "*Android Studio*" -or $_.CommandLine -like "*kotlin*" -or $_.CommandLine -like "*gradle*"
} | Stop-Process -Force -ErrorAction SilentlyContinue

# 4. Bonifica dei file di lock e run marker globali di Kotlin Daemon
if (Test-Path "$env:LOCALAPPDATA\kotlin\daemon") {
    Get-ChildItem -Path "$env:LOCALAPPDATA\kotlin\daemon\*" -Include "*.run","*.tmp" -Force -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
}

# 5. Bonifica delle sessioni Kotlin residue nel progetto (rimuove il warning .salive)
if (Test-Path "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\build\kotlin\sessions") {
    Remove-Item -Path "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\build\kotlin\sessions\*" -Force -ErrorAction SilentlyContinue
}

# 6. Pulizia della cache incrementale Kotlin di compilazione
if (Test-Path "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\build\kotlin") {
    Remove-Item -Path "c:\Users\marco\Antigravity Progetti\Calediario Turni\android-app\app\build\kotlin" -Recurse -Force -ErrorAction SilentlyContinue
}

# 7. Verifica dello stato dei demoni Gradle (deve mostrare 0 demoni attivi)
.\gradlew.bat --status

# 8. Esecuzione sequenziale di verifica: Unit Test & Compilazione APK
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

---

## 6. Azioni Chirurgiche Raccomandate per il Worker M4 Iteration 2

Per consentire l'approvazione unanime di Milestone 4 al prossimo gate:
1. **Applicare in `android-app/gradle.properties`**:
   ```properties
   org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
   android.useAndroidX=true
   android.nonTransitiveRClass=true
   kotlin.code.style=official
   kotlin.daemon.jvmargs=-Xmx4096m -XX:+UseParallelGC -Dfile.encoding=UTF-8
   ```
2. **Eseguire la sequenza di bonifica demoni** descritta al § 5.2.
3. **Eseguire la suite di verifica**:
   - `.\gradlew.bat test` (attesi 223/223 test passati al 100%).
   - `.\gradlew.bat assembleDebug` (atteso `BUILD SUCCESSFUL` con generazione di `app-debug.apk`).
