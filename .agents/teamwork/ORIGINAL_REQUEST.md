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


## 2026-10-14T15:39:24Z

Integrazione completa del sistema intelligente di importazione turni basato sul modello multimodale Google Gemini Flash Vision tramite API Key. L'applicazione consente il caricamento di immagini, screenshot o documenti PDF (renderizzati automaticamente in bitmap tramite PdfRenderer nativo di Android), li invia a Gemini Flash ed estrae in formato JSON strutturato le rotazioni cicliche di tutte le squadre, la legenda completa degli orari di inizio/fine turno e le condizioni di reperibilità. Inserendo a monte il proprio nominativo, la squadra dell'utente viene automaticamente individuata e pre-selezionata come predefinita, registrando i singoli turni nel catalogo per un setup iniziale immediato a zero frizione.

Working directory: c:\Users\marco\Antigravity Progetti\Calediario Turni
Integrity mode: development

## Requirements

### R1. Configurazione Google Gemini API Key & Client Multimodale Flash
- Consentire l'inserimento dell'API Key Google Gemini direttamente nella schermata di importazione al primo utilizzo, con salvataggio persistente e sicuro (SharedPreferences crittografate o master state), mascheramento caratteri e possibilità di modifica rapida.
- Implementare il client REST multimodale verso Google Gemini (`gemini-1.5-flash` o `gemini-2.0-flash`) con payload Base64 e structured output (`response_mime_type: "application/json"` con `response_schema`).
- Gestione trasparente di timeout, assenza di rete, errori HTTP (es. quota esaurita 429, chiave non valida 400/403) con messaggi informativi chiari in lingua italiana.

### R2. Gestione Flessibile Documenti (Screenshot, Foto & Conversione PDF Automatica)
- Supportare il caricamento da galleria di immagini (JPEG, PNG, WEBP) e screenshot di prospetti turni.
- Per i file PDF (come il modello MOD. P.R. aziendale), implementare la conversione automatica delle pagine in Bitmap ad alta risoluzione tramite `android.graphics.pdf.PdfRenderer` nativo prima dell'invio all'API, senza richiedere passaggi manuali all'utente.
- Mostrare una miniatura di anteprima dell'immagine elaborata direttamente nella schermata prima o durante l'analisi.

### R3. Estrazione Dati Strutturata da Visione Multimodale
- Il prompt visivo specializzato deve guidare Gemini Flash nell'analisi del prospetto tabellare estraendo:
  1. **Squadre e Rotazioni**: tutte le squadre individuate (es. Squadra A, B, C, D, B1) e la sequenza cronologica o rotativa dei codici turno giorno per giorno.
  2. **Catalogo & Orari Turni**: tabella oraria con codici turno, descrizione, orario di inizio e orario di fine (es. `M = 06:00 - 13:36`, `P = 14:00 - 21:36`, `N = 22:00 - 05:36`, `MD = 08:36 - 16:12`).
  3. **Regole di Reperibilità**: condizioni annotate nel prospetto (es. reperibilità attiva sui pomeriggi `P` o domenicale su `MD`).
  4. **Elenco Turnisti**: tabella con i nominativi dei lavoratori associati a ciascuna squadra (es. "Graziani Marco" in "Squadra B").

### R4. Pre-selezione Automatica e Setup Guidato Utente
- In presenza del campo nominativo compilato a monte dall'utente (es. "Marco Graziani" o "Graziani Marco"), l'app seleziona automaticamente la squadra di appartenenza come predefinita (stellina attiva).
- Pulsante di conferma rapida che:
  - Crea o aggiorna automaticamente i singoli turni mancanti nel catalogo (`ShiftRepository.shiftDefinitions`) con orari e colori appropriati.
  - Salva la rotazione della squadra selezionata (o di tutte le squadre) nel catalogo delle rotazioni.
  - Offre l'opzione di applicare subito il ciclo al calendario a partire dalla data di inizio validità del prospetto.

### R5. Interfaccia Obsidian Liquid Glass & Ergonomia Riccardo Breccia
- Interfaccia coerente con lo stile Obsidian Liquid Glass e l'ergonomia Riccardo Breccia: input unificato a monte (file + nome + campo API Key), stato di caricamento con indicatore di progresso elastico e card di anteprima risultati.
- Nessun banner o card statica che sposta il layout: notifiche gestite tramite `AnimatedStatusPopup` dal basso.
- Dialog di conferma e navigazione fluida con un solo pulsante indietro in alto a sinistra.

## Acceptance Criteria

### API Key & Networking Multimodale
- [ ] L'utente può inserire, salvare e modificare la propria API Key di Google Gemini direttamente nella schermata di importazione.
- [ ] La richiesta multimodale a Gemini Flash viene eseguita correttamente con payload immagine Base64 e riceve un JSON strutturato valido.
- [ ] In caso di errore (chiave errata, assenza di rete), viene mostrato un popup informativo elegante senza crash dell'applicazione.

### Conversione File & Parsing
- [ ] I PDF caricati vengono convertiti automaticamente in Bitmap tramite `PdfRenderer` e analizzati visivamente dal modello.
- [ ] Le immagini e gli screenshot caricati vengono ridimensionati se necessario e codificati in Base64 per l'invio all'API.
- [ ] Il parser decodifica il JSON di Gemini popolando correttamente l'elenco delle squadre, i turni giornalieri e gli orari.

### Integrazione Repository & Automazione
- [ ] Se il nome inserito dall'utente è presente nel documento, la rispettiva squadra viene evidenziata e contrassegnata come predefinita.
- [ ] Gli orari dei turni identificati (es. M 06:00-13:36, P 14:00-21:36, N 22:00-05:36) vengono creati/aggiornati automaticamente in `ShiftRepository.shiftDefinitions`.
- [ ] Il salvataggio registra la rotazione nel catalogo dell'app senza alterare i dati esistenti non correlati.

### Qualità del Codice & Rilascio
- [ ] Nuovi test unitari scritti per validare la decodifica del JSON multimodale e l'estrazione delle squadre (`.\gradlew.bat test` verde al 100%).
- [ ] Compilazione Gradle con esito `BUILD SUCCESSFUL` (`.\gradlew.bat assembleDebug`).
- [ ] Installazione e verifica visiva dal vivo sul dispositivo fisico Realme 8 Pro (`cb08bc57`).
- [ ] Aggiornamento dell'asset su GitHub Releases (`gh release upload v1.0.0 ... --clobber`) e nessun rilascio su OneDrive.
