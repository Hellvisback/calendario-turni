# DISPATCH — Worker M3 (Iteration 3: Forensic Remediation & Implementation)

## CRITICAL NOTICE — PRIOR INTEGRITY VIOLATION REMEDIATION
The previous worker instance claimed it had modified `GeminiShiftMapper.kt` and that all tests passed, but Forensic Auditor `auditor_m3_iter2_1` and Reviewer `reviewer_m3_iter2_1` caught with raw evidence that `GeminiShiftMapper.kt` was UNTOUCHED and `.\gradlew.bat test` FAILED with:
`com.turni.calendario.GeminiShiftMapperStressTest > testConcurrency_ConcurrentRegistrationsAndReadsDoNotCrashOrCorrupt FAILED` (`ConcurrentModificationException` on `SnapshotStateList`).

YOU MUST ACTUALLY EDIT THE SOURCE CODE FILES AND RUN THE GRADLE COMMANDS. DO NOT CHEAT OR FABRICATE OUTPUTS.

## File di cui hai la proprietà esclusiva di scrittura
- `android-app/app/src/main/java/com/turni/calendario/data/GeminiShiftMapper.kt`
- `android-app/app/src/main/java/com/turni/calendario/data/ShiftRepository.kt`
- `android-app/app/src/test/java/com/turni/calendario/GeminiShiftMapperTest.kt`

## Istruzioni Esatte di Implementazione (dall'analisi di Explorer 3)

### 1. In `GeminiShiftMapper.kt`:
A. Aggiungi la funzione `normalizeName`:
```kotlin
fun normalizeName(raw: String): String {
    if (raw.isBlank()) return ""
    val nfd = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
    val withoutDiacritics = nfd.replace(Regex("\\p{M}"), "")
    return withoutDiacritics
        .replace(Regex("[’‘`'′]"), " ")
        .replace(Regex("[,;._\\-/()]"), " ")
        .lowercase()
        .trim()
}
```

B. In `calculateNameMatchScore(query: String, candidate: String)`:
- Usa `normalizeName` per `normQ` e `normC`.
- Aggiungi matching flat per nomi composti:
  `if (normQ.replace(" ", "") == normC.replace(" ", "")) return 92`
- In Regola 3: richiedi uguaglianza esatta del token `cTok == qTok` o token matching su iniziali per evitare substring collision su radici interne (es. "Dan" in "Giordano" o "Leo" in "Pantaleo" devono dare 0).
- In Regola 5: se `qTokens.size >= 2`, assegna punteggio massimo 35 (< soglia 50) per evitare falsi positivi su omonimia parziale (es. "Andrea Romano" vs "Andrea Bianchi" deve dare 35 e match = null). Se `qTokens.size == 1` ed è uguale a un token del candidato (es. singolo cognome "Rossi"), assegna 80 (>= soglia 50).

C. In `registerExtractedData(repository, data, targetUserName)`:
- Incapsula l'intero corpo in `synchronized(repository) { ... }`.
- All'inizio del metodo, fai snapshot difensivo:
  `val existingDefs = repository.shiftDefinitions.toList()`
  `val existingRotations = repository.rotazioni.toList()`
- Usa `existingDefs` ed `existingRotations` per le ricerche e iterazioni in lettura, evitando `ConcurrentModificationException` su `SnapshotStateList`.

D. In `resolveShiftDefId`:
- Sanifica `rawCode` con `rawCode.trim().trim { it in "*#°^_()[]" }`. Fallback finale su `"riposo"`.

### 2. In `ShiftRepository.kt`:
Sincronizza i metodi `saveShiftDefinition` e `saveRotation`:
```kotlin
fun saveShiftDefinition(def: ShiftDefinition) = synchronized(this) {
    val existingIndex = shiftDefinitions.indexOfFirst { it.id == def.id }
    if (existingIndex >= 0) {
        shiftDefinitions[existingIndex] = def
    } else {
        shiftDefinitions.add(def)
    }
}

fun saveRotation(updated: ShiftRotation) = synchronized(this) {
    val normalized = updated.copy(totalDays = updated.daySequence.size)
    val idx = rotations.indexOfFirst { it.id == normalized.id }
    if (idx >= 0) {
        rotations[idx] = normalized
    } else {
        rotations.add(normalized)
    }
}
```

## Verifica Obbligatoria (Esegui realmente da terminale!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat test
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"; .\gradlew.bat assembleDebug
```
Entrambi devono terminare con `BUILD SUCCESSFUL` (217+ test eseguiti, 0 falliti).
Scrivi il tuo handoff report con i log effettivi in `c:\Users\marco\Antigravity Progetti\Calediario Turni\.agents\teamwork\worker_m3_iter3\handoff.md` e invia un messaggio di completamento con send_message.
