# Verifica 1.8.0-beta.12 (57)

## Intervento

- Ridimensionamento del calendario guidato da una singola molla smorzata, condivisa con la geometria dei bersagli di tocco. I mesi fuori schermo usano la geometria finale e non vengono ridisegnati a ogni fotogramma.
- Tratteggio unico sotto testi e indicatori: opacità 10% bianca in scuro e 7,5% nera in chiaro, spessore 0,6 dp. Resta limitato alla griglia.
- Prima immagine di una modifica preparata durante la composizione: il testo del turno è visibile, ma il suo riquadro non anticipa il riempimento della cella. I giorni già modificati non ripetono l'animazione.
- In uscita la superficie colorata si contrae verso il riquadro dell'assegnazione corrispondente, anche quando è il secondo turno. La geometria considera le bande dei metadati; i dati non vengono cambiati dall'effetto.
- Disattivato il ripple rettangolare standard sui bersagli trasparenti della griglia; restano i feedback personalizzati di selezione e applicazione.
- Numeri dei giorni e cicli centrati sui contorni reali dei glifi, con misure memorizzate. L'icona notebook è centrata nel proprio viewport. Nota o sola etichetta mostrano la stessa icona di testo; il pulsante del giorno resta notebook con indicatore di presenza.
- Note, etichette e creazione etichetta condividono la superficie del dettaglio giorno. Contenuti con transizione e dimensionamento dedicati: nessuna catena di dialoghi dal basso. Freccia Indietro per il passaggio precedente; X per chiudere il dettaglio intero.
- Rimosse le espansioni annidate di note e metadati. Cambiando giorno viene ridimensionato il corpo centrale, mantenendo il footer fermo. I cambi di pagina interni usano un'unica transizione di dimensione.
- Altezza massima compatibile con la tastiera, titolo e salvataggio fuori dallo scorrimento. Salvataggio con chiusura tastiera; bozza annullata senza persistenza.

## Prove con dati sintetici sul Realme 8 Pro

Pacchetto separato com.turni.calendario.setupqa. Il pacchetto personale non è stato usato per inserimenti di prova.

- Acquisizioni native dei fotogrammi di entrata in modifica, applicazione e uscita. Lo strumento screenrecord non è presente nel dispositivo: usata una piccola Instrumentation temporanea e screenshot memorizzati dopo la sequenza.
- Applicazione singola in scuro e secondo turno in chiaro; verificati gli stati di modifica senza il nuovo riquadro e il ritorno ai due riquadri normali. Le acquisizioni immediate dopo un tap comprendono intenzionalmente stati intermedi: non sono screenshot degli stati a riposo.
- Centratura su PNG 1080×2400: numero 13 nel riquadro blu, scarto raster +1 px orizzontale / 0 verticale; numero 1 festivo, -0,5 px orizzontale / 0 verticale. Sono misure dei pixel pieni dei glifi, non della larghezza di avanzamento del font.
- Cambio dal giorno 13 con nota del turno, nota giornaliera ed etichette al 14 senza note: in 15 fotogrammi, il pulsante inferiore mantiene sempre l'intervallo verticale 652–687 nel campione 360×800. Il bordo superiore si sposta con la contrazione del corpo, senza traslazione anticipata del footer.
- Apertura note, nuova etichetta, tastiera, salvataggio etichetta e salvataggio giorno: dati conservati, tastiera chiusa e ritorno al dettaglio. Creata solo la fixture Visita QA nel pacchetto temporaneo.
- Bozza di nota modificata e chiusa con X: dayExtraInfo identico alla copia precedente. Nuova etichetta Annullata chiusa con X: nessun elemento salvato, nessun pannello precedente riapparso.
- Tasto Android Indietro nella creazione etichetta: ritorno a Note ed etichette; X chiude il dettaglio. Tema chiaro e tema scuro ispezionati.

## Limiti

Le acquisizioni con UIAutomation servono a verificare la geometria, non a certificare il frame rate: la cattura altera i tempi di rendering. Non è dichiarata l'assenza di ogni frame lento. Nessun nuovo comportamento dei backup o della sincronizzazione Google è incluso in questa revisione.

## Consegna e integrità finale

- Build Beta R8 e lintVital riusciti, 469 test JVM con zero errori/fallimenti. Restano avvisi di deprecazione preesistenti.
- APK finale com.turni.calendario, versione 57 / 1.8.0-beta.12, min SDK 26, target SDK 36. Firma v2 verificata, stessa firma compatibile con la beta precedente. Nessuna modifica alla procedura di firma store.
- Installato con aggiornamento sul Realme autorizzato. Verificato anche il percorso Note → Nuova etichetta → tastiera → X nell'APK non debuggable: ritorno al solo calendario, bozza non salvata.
- Impronta database prima, dopo l'installazione e dopo la prova di annullamento: a135d503bc9482c98e9fba8d1f5f5e5fc201d37630b82456855052f73696beac. Identità completa: nessun dato o preferenza modificato dalle prove sull'app personale.
- APK locale e base.apk installato hanno entrambi SHA-256 569d700b4ca3f2b8cd37d626ec27b9fd1d78084488408ac33d85838a9f297817. Dimensione 3.268.741 byte.
- Caricato tramite Google Drive e metadati riletti: id 1IvjIwFc0VxQ7Ed4qtjXGwRN6d-GuokzL. Nessuna modifica alla condivisione del file.
- Link: https://drive.google.com/file/d/1IvjIwFc0VxQ7Ed4qtjXGwRN6d-GuokzL/view?usp=drivesdk
- Pacchetti QA, sonda di acquisizione e sonda di integrità rimossi. Dati personali intatti, tema scuro originario conservato. Nessuna pubblicazione GitHub o modifica al sito privato.
