# Verifica v1.7.0 — importazione assistita da AI esterna

28 ottobre 2026. APK 1.7.0 / versionCode 42 installato sul Realme 8 Pro con install -r. Nessuna pubblicazione GitHub e nessuna disinstallazione dell’app principale.

## Flusso prodotto

L’unico percorso esposto nelle impostazioni è Importazione con AI: nome del lavoratore, istruzioni standard da copiare nella propria AI, PDF/immagini allegati dall’utente in quel servizio, ritorno mediante testo incollato o file JSON, revisione e salvataggio nel catalogo. Non sono presenti campi chiave API, quote condivise, analisi dirette o caricamenti automatici del documento a servizi AI. I collegamenti ChatGPT/Gemini/Claude aprono semplicemente il browser; non trasferiscono allegati o istruzioni automaticamente.

Il selettore PDF/immagine è facoltativo: controlla il file sul dispositivo, visualizza nome e numero di pagine e non sostituisce l’allegato nella chat esterna. PDF fino a 12 MiB e 100 pagine, senza troncamenti silenziosi. La risposta JSON è limitata a due milioni di caratteri e il file viene decodificato UTF-8 rigorosamente. File e stream chiusi; PDF temporanei rimossi nel finally. Nessun calendario modificato durante lettura o revisione.

## SuperShift

Rimossi dal prodotto la card Passa da SuperShift, le due destinazioni dedicate nelle impostazioni, la schermata di migrazione e la guida alla richiesta email. Conservate le copie di recupero eventualmente già presenti, con etichetta generica; nessun dato utente o archivio precedente è stato cancellato. Il decoder calendario condiviso con Google Calendar è rimasto interno per non alterare la sincronizzazione e i relativi test: nessun pulsante SuperShift è esposto.

## Prompt e parsing

AiRosterImport è la sorgente del prompt universale. Nome delimitato come stringa JSON; documenti trattati esclusivamente come dati. Lettura di tutte le pagine, date ruotate e celle unite, legende/orari, riga base di squadra distinta dalle variazioni personali, omonimie e nome/cognome invertiti. Non suggerisce un settore predefinito o orari standard da inventare. Reperibilità condizionale e trasferta non vengono convertite in turni autonomi. Incognite, multi-turno non rappresentabile nel ciclo e regole da verificare devono essere dichiarati nelle osservazioni.

Gli indici dei giorni sono preservati, inclusi vuoti e sigle sconosciute. Nessuna suddivisione di sigle su spazi/virgole che allunghi il ciclo. Origine del ciclo esplicita: documentato, ripetizione osservata o solo periodo osservato. La semplice presenza di quattro settimane non dimostra un ciclo perpetuo. Nome ambiguo/non trovato: nessuna selezione automatica della prima squadra. Orari mancanti non vengono sostituiti da Mattina 06–13:36; la bozza è marcata da completare e il salvataggio resta indisponibile.

Il parser usa scansione bilanciata dei delimitatori JSON, preservando URL, parentesi e commenti letterali dentro stringhe. Rifiuta risultati incompleti, sigle sorgenti duplicate, limiti eccessivi e strutture incompatibili. Gli errori di sintassi/file hanno spiegazioni leggibili.

## Revisione e salvataggio

Editor di modelli e matrice rotazioni condivisi con il catalogo, alimentati dalle definizioni della bozza. Durante la revisione della matrice non sono disponibili modifiche alla predefinita globale. Scelta del lavoro disabilitata nel singolo modello in bozza per non contraddire il lavoro destinatario; ulteriore validazione al salvataggio. Correggibili nomi, sigle (massimo quattro caratteri), categorie, orari, giornata intera, pause e colori. Si possono rimuovere o aggiungere modelli mancanti e comporre una nuova sequenza; le posizioni sconosciute restano visibili con ? fino alla correzione. Anteprima estesa del solo ciclo selezionato.

Conferma globale della revisione e conferma separata del ciclo. Si possono salvare solo i modelli. Solo la rotazione scelta entra nel catalogo; l’impostazione predefinita cambia soltanto se attivata esplicitamente. Nessuna assegnazione viene applicata automaticamente al calendario.

Salvataggio additivo: corrispondenze esatte di lavoro, nome/sigla, orari, categoria, giornata intera e pausa riutilizzate senza cambiare colori o parametri già presenti. Nome o sigla da soli non autorizzano sovrascritture. Nuove varianti ricevono identificativi nativi; tutti i giorni della rotazione puntano agli identificativi definitivi. Reimportazione identica non duplica modelli/rotazioni. Validazione prima delle modifiche, controllo limiti, ripristino snapshot in caso di errore.

## Documento di riferimento e limiti della prova

Letto e renderizzato localmente il PDF fornito Turni 21.09_18.10 UMIS3.pdf, una pagina. Verificati nella legenda M 06:00–13:36, P 14:00–21:36, N 22:00–05:36, MD 08:36–16:12 e l’associazione del nome alla Squadra B. La riga base di 28 giorni è stata confrontata visivamente con una fixture di riferimento manuale. TGF compare ma non ha un orario nella legenda: mantenuto da completare; non dedotto dalle abitudini dell’utente. Le sigle RGLS e RISE sono conservate senza espansione inventata.

La fixture dei test nel repository è anonimizzata. Il PDF originale e i dettagli privati di confronto restano nel workspace di QA. Le prove con JSON sono risposte campione costruite per verificare il software, NON risultati di una chiamata AI reale. Nessuna richiesta a un servizio AI è stata eseguita: nel flusso concordato la chat esterna è scelta e gestita dall’utente. L’accuratezza del risultato dell’AI va controllata nella revisione; non dichiarare una migrazione/estrazione perfetta e automatica.

## Verifica

- assembleDebug e test riusciti: 426 test Debug e 426 Release, zero errori/fallimenti.
- Sedici nuovi test: documento di riferimento, notti e colonne, orari mancanti, nomi invertiti/ambigui/assenti, sigle duplicate, posizioni sconosciute, URL/parentesi nelle note, troncamenti e limiti, categorie invalide, conferma del ciclo, immutabilità della bozza, salvataggio additivo e idempotente, lavoro cambiato e stream file.
- Sul telefono: selezionata una copia temporanea del PDF reale; caricata fixture JSON con nome sintetico tramite selettore Android. Verificate revisione, modello da completare, modifica modello, matrice del ciclo con soli modelli della bozza, rimozione del modello incerto, conferme e abilitazione finale del tasto Salva. Nessun dato fittizio salvato nel catalogo del telefono; il salvataggio effettivo è esercitato dai test con ripristino del repository nel finally.
- Margine della barra di stato corretto per gli editor a schermo intero, verificato nella matrice; interfaccia sorgente verificata chiara/scura, ripristinato il tema scuro originale.
- Database del telefono prima/dopo identico, escluso exportDate tecnico. Turni, lavori, rotazioni, note e configurazioni conservati. Nessun evento Google sincronizzato o cancellato.
- Rimossi soltanto i file temporanei turnisync-ai-review-test.json e turnisync-roster-read-test.pdf dal Download del telefono.

APK: outputs/TurniSync-v1.7.0.apk nel workspace Codex.
SHA256: d1ee862584a92a0b46f9b047d6860de10fcf6e977326d5fb8b6386f1b1b183ad
