# Original User Request

## 2026-10-13T20:39:55Z

This is a single self-contained fix; keep it small and focused.
Riprogettare e ottimizzare l'esperienza di Importazione Turni nella schermata "Altro" dell'applicazione Android Jetpack Compose Calediario Turni, migliorando l'elaborazione del testo e dei file caricati, semplificando la raccolta dati e le azioni utente con animazioni fluide e conferme di sicurezza.

Working directory: c:\Users\marco\Antigravity Progetti\Calediario Turni
Integrity mode: demo

## Requirements

### R1. Elaborazione Robusta di File e Testo Multi-Formato
- Il sistema deve ripulire il testo estratto dai file (PDF, fogli di calcolo, CSV, testo incollato) rimuovendo caratteri non validi, binari o sequenze illeggibili prima della visualizzazione.
- L'algoritmo di estrazione deve essere flessibile per interpretare turni e schemi tabellari oltre al modello standard RFI.
- Il testo decodificato deve essere mostrato in un'area modificabile che consenta all'utente di correggere eventuali incongruenze ed eseguire nuovamente l'analisi in tempo reale.

### R2. Flusso di Inserimento Unificato e Selezione Turnista a Monte
- Rimuovere il pulsante "Carica esempio".
- Raggruppare la selezione del file ("Sfoglia file") e l'inserimento/modifica manuale del testo all'interno di un unico pannello chiaro ed ergonomico.
- Richiedere il nome del lavoratore/dipendente direttamente nella fase iniziale di caricamento (es. campo "Nome da cercare nel file"), in modo da filtrare ed evidenziare automaticamente i turni di quella specifica persona nel risultato dell'analisi.

### R3. Controllo Turno Predefinito con Feedback Tattile e Visivo
- La stellina per impostare il turno come predefinito deve essere commutabile (toggle): un tocco imposta il turno come predefinito, un secondo tocco revoca la scelta tornando allo stato neutro senza costringere a selezionarne un altro.
- Rimuovere la scritta testuale "Predefinito" accanto alla stellina, delegando lo stato interamente all'icona visiva.
- Integrare una micro-animazione elastica (spring scale e feedback visivo immediato) direttamente sull'icona della stella al tocco, in sinergia con la notifica HUD a scomparsa in basso.

### R4. Azioni Chiave di Chiusura e Salvaguardia Dati
- Sostituire l'azione "Applica ciclo e salva nel calendario" con due azioni distinte: "Salva nel catalogo" e "Modifica".
- "Salva nel catalogo" registra la rotazione e le definizioni turno nel catalogo dell'applicazione.
- Includere una nota visiva esplicativa che ricordi all'utente che i turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione dell'app.
- Alla pressione di "Modifica", mostrare un dialog modale di avviso: "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni", consentendo di confermare il passaggio o annullare.

### R5. Vincoli di Ambiente e Infrastruttura Controllata
- Eseguire la compilazione e verifica tramite Gradle su Android Studio (`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"`, `.\gradlew.bat assembleDebug` nella cartella `android-app`).
- Mantenere lo stile grafico Obsidian Liquid Glass e l'ergonomia Riccardo Breccia senza introdurre banner statici che spostano il layout.

## Acceptance Criteria

### Estrazione & Normalizzazione Dati
- [ ] Nessun carattere illeggibile o binario appare nel box di testo dopo l'importazione di file o PDF.
- [ ] L'inserimento manuale del testo consente modifiche e re-analisi senza perdita di dati.
- [ ] L'input del nome/dipendente a monte filtra e pre-seleziona la riga corrispondente nei risultati.

### Ergonomia dell'Interfaccia
- [ ] Il tasto "Carica esempio" non è più presente.
- [ ] Sfoglia file, inserimento manuale e campo nome sono integrati in un unico blocco visivo coerente.
- [ ] La stellina del predefinito si attiva e disattiva con tocchi successivi sulla stessa riga.
- [ ] L'icona della stella reagisce con animazione elastica al click ed è priva di etichette testuali ridondanti.
- [ ] I pulsanti finali sono "Salva nel catalogo" e "Modifica".
- [ ] Il click su "Modifica" apre il dialog modale di avviso di uscita con opzioni di conferma/annulla.

### Build e Verifica Operativa
- [ ] Il progetto compila con successo via Gradle (`assembleDebug`) senza errori.
- [ ] L'interfaccia rispetta la navigazione con un unico pulsante indietro e corretta gestione del back button.
