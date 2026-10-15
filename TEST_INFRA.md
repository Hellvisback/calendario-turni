# E2E Test Infra: Calediario Turni — Gemini Flash Vision Import

## Test Philosophy
- Opaque-box, requirement-driven, disaccoppiato dai dettagli interni di implementazione.
- Metodologia: Category-Partition + Boundary Value Analysis + Pairwise Combinatorial + Real-World Workload Testing.
- Test suite eseguibile via Gradle (`.\gradlew.bat test`) su JVM con `testImplementation("org.json:json:20231013")`.

## Feature Inventory Mapping
| # | Feature | Source (Requirement) | Tier 1 (Coverage) | Tier 2 (Boundary/Edge) | Tier 3 (Cross-Feature) | Tier 4 (Workload) |
|---|---------|----------------------|:-----------------:|:----------------------:|:----------------------:|:-----------------:|
| F1 | Gemini API Key Management | ORIGINAL_REQUEST § R1 | 5 | 5 | ✓ | ✓ |
| F2 | Multimodal REST Client | ORIGINAL_REQUEST § R1 | 5 | 5 | ✓ | ✓ |
| F3 | Image Document Processing | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| F4 | Native PDF Conversion | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| F5 | Document Preview Thumbnail | ORIGINAL_REQUEST § R2 | 5 | 5 | ✓ | ✓ |
| F6 | Structured Data Extraction | ORIGINAL_REQUEST § R3 | 5 | 5 | ✓ | ✓ |
| F7 | Shift Catalog Registration | ORIGINAL_REQUEST § R3, R4 | 5 | 5 | ✓ | ✓ |
| F8 | Auto User Squad Matching | ORIGINAL_REQUEST § R4 | 5 | 5 | ✓ | ✓ |
| F9 | One-Click Calendar Setup | ORIGINAL_REQUEST § R4 | 5 | 5 | ✓ | ✓ |
| F10 | Obsidian Liquid Glass UI | ORIGINAL_REQUEST § R5 | 5 | 5 | ✓ | ✓ |
| F11 | Riccardo Breccia Ergonomics | ORIGINAL_REQUEST § R5 | 5 | 5 | ✓ | ✓ |
| F12 | Zero-Shift Animated Notifications | ORIGINAL_REQUEST § R5 | 5 | 5 | ✓ | ✓ |

## Test Architecture
- **Location**: `android-app/app/src/test/java/com/turni/calendario/`
- **Runner**: JUnit 4 runner standard Android (`.\gradlew.bat test`)
- **JSON Engine**: `org.json:json:20231013` aggiunto a `testImplementation` per consentire manipolazioni JSON realistiche senza dipendenza dall'ambiente Android runtime.
- **Pass/Fail Semantics**: 100% verde (exit code 0), zero stub test o test con asserzioni fittizie (true == true).

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Importazione completa da PDF Turni RFI con 4 squadre e legenda orari | F1, F2, F4, F6, F7, F8, F9 | High |
| 2 | Screenshot tabellare con reperibilità su turni pomeridiani (P) e domeniche (MD) | F1, F2, F3, F6, F7, F9 | High |
| 3 | Matching nominativo utente esatto e case-insensitive ("Graziani Marco" -> "Squadra B") con preselezione | F1, F6, F7, F8 | Medium |
| 4 | Errore quota 429 da Gemini con notifica AnimatedStatusPopup e retry elegante | F1, F2, F12 | Medium |
| 5 | Documento PDF corrotto o immagine vuota: gestione errore controllata senza crash | F3, F4, F12 | Medium |

## Coverage Thresholds
- **Tier 1 (Feature Coverage)**: >=5 test case per feature (min 60 test)
- **Tier 2 (Boundary & Corner Cases)**: >=5 test case per feature (min 60 test)
- **Tier 3 (Cross-Feature Combinations)**: Combinazioni pairwise tra API Key, Immagini/PDF, parsing turni e mapping utente
- **Tier 4 (Real-World Scenarios)**: >=5 scenari reali completi end-to-end
