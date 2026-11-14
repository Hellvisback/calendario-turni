# TurniSync

Applicazione Android nativa per organizzare turni, lavori e rotazioni, sviluppata in Kotlin e Jetpack Compose. Il calendario si adatta ai modelli creati dall'utente, senza dipendere da una professione specifica.

**Stato sincronizzato: 1.8.0-beta.17, versionCode 62.** È la versione verificata sul Realme 8 Pro il 14 novembre 2026; 480 test automatici superati. Questa sincronizzazione dei sorgenti non crea una nuova release APK.

## Riprendere il lavoro da un altro computer o in cloud

Leggere per primo **[docs/CLOUD_HANDOFF.md](docs/CLOUD_HANDOFF.md)**: contiene stato del lavoro, configurazione, comandi, riferimenti grafici e limiti delle prove.

```bash
git clone https://github.com/Hellvisback/calendario-turni.git
cd calendario-turni
# Linux x86_64, con JDK 17, curl e unzip installati:
bash scripts/cloud-setup.sh --accept-licenses
```

Il flag accetta le licenze Android SDK, disponibili sul [sito Android](https://developer.android.com/studio#command-line-tools-only). Se l'SDK è già installato e le licenze sono accettate, il flag si può omettere. Lo script configura l'SDK e prepara le dipendenze eseguendo build e test.

Per le verifiche successive:

```bash
bash scripts/check-android.sh
```

Su Windows, con JDK 17 e Android SDK installati:

```powershell
.\scripts\check-android.ps1 -JavaHome 'C:\percorso\del\jdk-17'
```

Il wrapper Gradle è incluso, insieme al relativo checksum. Non è necessario installare Gradle globalmente.

## Funzioni presenti

- Calendario con più turni al giorno, reperibilità, festività configurabili, note, etichette e avvisi di riposo minimo.
- Modifica rapida, aggiunta di turni, intervalli con pressione prolungata, gomma selettiva, annullamento e rotazioni con anteprima.
- Modelli turno e lavori personalizzabili; conservazione del turno originale delle rotazioni per l'indicazione «ex turno».
- Straordinario calcolato dall'ora di uscita effettiva e trasferte associate al singolo turno.
- Statistiche dinamiche per periodo, lavoro, modelli ed etichette, con regole di conteggio configurabili.
- Temi chiaro/scuro/automatico e palette Grafite con accento cobalto; dock animato con sole icone.
- Widget giornaliero, settimanale, due settimane e mese; sveglie e relative autorizzazioni Android.
- Backup/ripristino, integrazioni Google Drive/Calendar, esportazione PDF e importazione assistita tramite la propria AI esterna.
- Introduzione iniziale e guida interattiva.

## Struttura

| Percorso | Contenuto |
| --- | --- |
| `android-app/` | App effettiva, risorse, test unitari e strumentazione Android |
| `scripts/` | Configurazione, verifiche e strumenti di progetto |
| `docs/CLOUD_HANDOFF.md` | Punto di ingresso aggiornato per un nuovo sviluppatore/agente |
| `docs/VERIFICA_v1.8.0-beta.17.md` | Verifica dell'ultima beta installata |
| `design/reference/` | Prototipi HTML storici, conservati con iframe sandbox e CSP |
| `design/svg/` | Schermate vettoriali del precedente studio grafico, con licenze |
| `design/studies/` | Studi cromatici e forme; esperimenti, non la palette corrente |
| `design/qa-beta17/` | Schermate sintetiche delle ultime correzioni |
| `esApp/`, `stitch_assets/` | Riferimenti storici già presenti; non sono il runtime Android |

## Firma, account e dati personali

Una build eseguita su un computer nuovo genera normalmente una chiave debug diversa. **Non potrà aggiornare l'app già installata né utilizzare automaticamente il suo client OAuth Android.** Per una build compatibile occorre fornire privatamente la chiave originale; le variabili supportate sono descritte nella guida cloud. Chiavi, password, token, backup personali e database del telefono non sono nel repository.

I controlli GitHub Actions compilano e testano il progetto, senza pubblicare APK né modificare i dati di un dispositivo. Le release APK restano una procedura distinta, tramite `scripts/release.ps1` su una macchina configurata per la firma.

La guida cloud e le verifiche più recenti hanno precedenza sulle note storiche in `HANDOVER_NOTES.md` e `HANDOVER_CODIUM.md`.
