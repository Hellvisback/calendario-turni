# Verifica TurniSync 1.6.4 (36) — 27 ottobre 2026

## Modifiche

- Widget: eliminata la stima del riquadro mediante numero di cifre, altezza nominale del font e baseline fissa. Oggi, festività e cifra condividono una geometria centrata nella zona della data.
- Il contorno reale del testo viene misurato con `Paint.getTextPath` e `Path.computeBounds`; il numero si posiziona sul centro del riquadro compensando anche la larghezza asimmetrica delle cifre. La misura non usa l'altezza di ascender/descender, che introduceva lo scarto visibile.
- Entrambi i riquadri usano la stessa forma quadrata arrotondata. Il contorno tiene conto del proprio spessore e conserva il margine dalla parte superiore della cella e dal turno sottostante. Le misure estreme riducono il testo solo quando serve a contenerlo nella zona disponibile.
- Nei widget settimanali più bassi la zona della data cresce da 18 a 20 dp, mantenendo spazio per entrambe le righe del turno. Nessuna variazione alla testata del widget a due settimane.
- Calendario: linguetta dell'icona note spostata dall'angolo inferiore sinistro a quello inferiore destro, verso il numero del giorno. Posizione dell'icona, dimensioni e dissolvenza di inserimento/rimozione da 360 ms conservate; niente scale del testo o animazioni infinite.

## Verifiche sul Realme 8 Pro

- `assembleDebug test`: BUILD SUCCESSFUL. 375 test Debug + 375 Release superati, zero fallimenti/errori/test saltati.
- APK 1.6.4 (36) installato con `adb install -r`; verificata la versione installata.
- Screenshot del launcher reale: widget settimanale, due settimane, giornaliero e mese di novembre a sei righe, in entrambi i temi. Verificati oggi 27 e la festività del 1 novembre.
- Misura dei pixel di cinque campioni nei widget: oggi nel settimanale, due settimane e giornaliero; festività nel settimanale e due settimane. Differenza massima fra centro del numero e centro della cornice: **0.5 pixel**. Risultati in `pixel-centering.json`; la tolleranza include antialiasing e rasterizzazione del launcher.
- Nota temporanea aggiunta alla giornata del 12 ottobre con reperibilità e turno, per osservare la nuova icona, poi rimossa. Screenshot valido: `notes-right-final-dark.png`.
- Durante la QA un tap a coordinate fisse, dopo il ridimensionamento del pannello, aveva attivato Elimina. L'azione è stata annullata tramite la cronologia dell'app; i successivi comandi dei pannelli sono stati individuati per etichetta accessibile. La nota temporanea è stata rimossa e il confronto completo del DB è identico alla baseline, incluse identità e contenuti delle 122 assegnazioni, degli 11 modelli e delle informazioni giornaliere. Tema scuro iniziale ripristinato.
- `git diff --check` sui file dell'app: nessun errore.

## Artefatti

- APK: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/outputs/TurniSync-v1.6.4.apk`
- SHA-256: `72a16e29e84d1245894e846d1ef6a367558bf0cab747529e93a8548b717730b5`
- Cartella QA: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v164-qa`
- Screenshot finali: `widgets-verified-dark.png`, `month-verified-dark.png`, `month-light.png` (widget settimanale/due settimane/giornaliero chiari), `month-final-light.png` (widget mensile chiaro), `notes-right-final-dark.png`.
- Log build: `C:/Users/marco/Documents/Codex/2026-10-19/ciao-sei-il-master-orchestrator-dell/work/v164-build.log`

La misura dei pixel è limitata ai cinque campioni indicati; non certifica ogni launcher o impostazione di carattere. Nessuna pubblicazione GitHub effettuata. Restano i limiti funzionali delle verifiche precedenti.
