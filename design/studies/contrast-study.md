# TurniSync — studio cromatico 3

Tre direzioni complete, ognuna progettata in scuro e chiaro. Sono proposte da confrontare prima di modificare l'app. I token esatti, le 48 coppie dei turni e i risultati individuali sono in `palettes.json`. I valori si possono rigenerare con `audit-palettes.py`.

## Il problema da risolvere

Le precedenti proposte cambiavano soprattutto l'accento dell'interfaccia, mantenendo praticamente lo stesso assortimento pastello per i turni. In un calendario i turni occupano una porzione consistente dell'immagine: il loro colore contribuisce all'identità quanto la barra di navigazione. Cambiare soltanto il bottone rende facile ottenere tre stili che non appartengono alla stessa famiglia.

Questo studio cambia insieme tre livelli:

1. **Superfici:** quasi neutre, con una piccola impronta del sistema cromatico. La profondità viene dalla luminosità, non da bordi pesanti.
2. **Interazione:** una primaria riconoscibile per navigazione, pulsanti e selezione; una secondaria dosata, utile per contenitori e dettagli.
3. **Dati:** otto famiglie coerenti per i turni, con toni diversi per card e grafici. Nessuna dipendenza dai nomi Mattina, Pomeriggio o da un settore lavorativo specifico.

La separazione tra colore del contenitore e colore del suo contenuto segue il modello di [ruoli cromatici di Android/Material](https://developer.android.com/design/ui/mobile/guides/styles/color). Le tinte scelte qui sono una proposta originale: non vengono presentate come palette ufficiali generate automaticamente da Material Color Utilities.

## Tre direzioni

### Lime botanico

Il lime conserva il carattere acceso apprezzato nel tema scuro, mentre i turni seguono il percorso **miele → foglia → eucalipto → denim → iris**, con salvia, ardesia e pietra a completare il sistema. Il corallo e l'arancione saturo scompaiono dai turni: non serve un secondo accento caldo molto energico vicino al lime.

Il tema chiaro non ripete semplicemente il lime scuro sul bianco. Usa oliva profondo per testo e comandi, lime tenue solo per il contenitore attivo. La primaria scura è `#D0EF78`; quella chiara è `#486716`. L'identità è la stessa, il tono è adatto al supporto.

**Da scegliere per:** un risultato giovane e pop. **Da controllare nella prova visiva:** non diffondere l'accento brillante su grandi superfici, altrimenti i dati perdono gerarchia. Il lime è un segnale, non lo sfondo dell'intera app.

### Cobalto e burro

Il blu è il protagonista, il burro porta luce; i turni restano prevalentemente in un registro **fiordaliso → pervinca → viola blu → acqua → giada**, con burro e sabbia come contrappunti. Il vecchio pomeriggio corallo viene sostituito dal fiordaliso per evitare un terzo protagonista cromatico.

Nel tema scuro `primary: #ACC0FF` è il colore leggibile per scritte e icone sulla grafite. Il vero blu elettrico `brandFill: #395AE0`, con bianco `onBrandFill`, serve al pulsante principale o a un singolo elemento forte. Sono due ruoli distinti: il blu saturo non va usato come testo piccolo sulla superficie scura. Nel tema chiaro `#3052CB` svolge entrambi i compiti.

**Da scegliere per:** identità energica e riconoscibile. **Da controllare nella prova visiva:** usare il giallo burro in un solo richiamo importante per schermata; riservare il cobalto pieno a poca area. Evitare un grande pannello blu dietro al grafico multicolore.

### Acqua e iris — direzione raccomandata per equilibrio

Sposta la coesione della versione calda verso **acqua e iris**. Il petrolio molto neutro delle superfici riduce il nero-blu che non convinceva. I turni uniscono agrume attenuato, petrolio, azzurro, iris, malva fredda e due neutri: il sistema ha varietà senza sembrare un assortimento indipendente dall'interfaccia.

La primaria scura `#86E1D3` rimane luminosa; nel chiaro diventa `#09675D`. L'iris è secondario e non occupa tutte le selezioni. Fra le tre è la proposta più bilanciata per chi alterna spesso dark e light: carattere presente, ma meno aggressivo del lime e meno dominante del blu elettrico.

**Da scegliere per:** uso quotidiano prolungato con un'identità fresca. **Da controllare nella prova visiva:** non usare contemporaneamente acqua e iris come colori attivi della stessa gerarchia; la primaria identifica la selezione.

## Regole di applicazione

| Elemento | Ruoli da usare | Scelta di gerarchia |
|---|---|---|
| Sfondo schermata | `bg` | Non compete con i dati |
| Card e sheet | `surface`, eventualmente `raised` | Nessun bordo decorativo pesante |
| Testo principale / secondario | `text` / `muted` | Il secondario resta leggibile, non diventa grigio quasi invisibile |
| Azione / navigazione attiva | `primary` + `onPrimary` | Una sola grammatica della selezione |
| Cobalto: azione più importante | `brandFill` + `onBrandFill` | Blu elettrico pieno, mai usato come testo sul dark |
| Selezione morbida | `primaryContainer` + `onPrimaryContainer`, contorno `primary` | Il contenitore è delicato, il contorno rende la selezione esplicita |
| Turno in calendario | `shift.fill` + `shift.ink` | La categoria è leggibile su entrambi i temi |
| Forma KPI scura | `shift.chart` + `shift.shapeInk` | Più brillante del calendario, con il solo conteggio al centro; nome e ore sulla superficie esterna |
| Forma KPI chiara | `shift.fill` + `shift.ink` | Colore luminoso, conteggio profondo |
| Settore del grafico / marker legenda | `shift.chart` | Tono più forte del fondo card nel chiaro |
| Festa | `holiday` sul bordo del numero + nome nel dettaglio | Numero neutro, nessuna cornice sull'intera cella |
| Reperibilità | `onCall` + campanella | Non si confonde con una categoria di turno |
| Straordinario / trasferta | `overtime` / `trip` + simbolo o testo | Il colore aggiunge informazione, non la sostituisce |

I colori dei turni vanno assegnati a un identificativo persistente, non ricavati dal nome. Gli otto nomi inclusi nel JSON servono solo per la dimostrazione. Un utente può chiamare un modello `POM`, `Servizio B` o `Laboratorio`: il nome non deve modificare la categoria cromatica, e un passaggio dark/light non deve cambiarne la famiglia.

Quando i modelli superano otto, riutilizzare la palette con nomi/sigle sempre visibili. Otto colori non possono identificare senza ambiguità un insieme illimitato di modelli. La forma e il testo restano essenziali, specialmente per chi distingue meno bene alcuni colori.

## Verifica numerica

Audit eseguito con Python sul JSON finale: conversione sRGB lineare, luminanza relativa e rapporto `(L più chiara + 0,05) / (L più scura + 0,05)`. I risultati non sono stime visive. Le soglie usate sono **4,5:1 per tutto il testo**, compresi i piccoli label, e **3:1 per contorni funzionali e colori dei grafici sulle superfici**. La soglia del testo grande sarebbe 3:1, ma qui non è necessaria alcuna deroga. Riferimenti: [WCAG Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html) e [Non-text Contrast](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html).

**386 coppie verificate, nessun fallimento.** I rapporti in tabella sono i minimi del gruppo, arrotondati a tre decimali. L'esito è calcolato prima dell'arrotondamento.

| Sistema | Tema | Testi e coppie on/container: minimo | Indicatori e grafici: minimo | Coppie |
|---|---|---:|---:|---:|
| Lime botanico | Scuro | 6,086:1 | 4,325:1 | 68 |
| Lime botanico | Chiaro | 5,513:1 | 3,569:1 | 60 |
| Cobalto e burro | Scuro | 5,656:1 | 4,510:1 | 69 |
| Cobalto e burro | Chiaro | 5,430:1 | 3,472:1 | 61 |
| Acqua e iris | Scuro | 6,157:1 | 4,599:1 | 68 |
| Acqua e iris | Chiaro | 5,395:1 | 3,365:1 | 60 |

### Leggibilità delle superfici

Il valore minimo viene calcolato su `bg`, `surface` e `raised`, non soltanto sullo sfondo più favorevole.

| Sistema / tema | Testo principale | Testo secondario | Primaria come testo/icona | Contorno funzionale |
|---|---:|---:|---:|---:|
| Lime / scuro | 12,187 | 7,308 | 10,429 | 4,325 |
| Lime / chiaro | 13,012 | 5,693 | 5,513 | 3,791 |
| Cobalto / scuro | 12,209 | 7,662 | 7,495 | 4,510 |
| Cobalto / chiaro | 12,995 | 5,430 | 5,599 | 3,498 |
| Acqua / scuro | 11,756 | 7,229 | 8,363 | 4,599 |
| Acqua / chiaro | 11,835 | 5,395 | 5,753 | 3,365 |

### Etichette dei turni sul loro fondo

| Slot dimostrativo | Lime scuro | Lime chiaro | Cobalto scuro | Cobalto chiaro | Acqua scuro | Acqua chiaro |
|---|---:|---:|---:|---:|---:|---:|
| Mattina | 7,159 | 7,353 | 6,914 | 6,746 | 6,505 | 6,378 |
| Pomeriggio | 7,068 | 7,142 | 6,734 | 6,843 | 6,157 | 6,246 |
| Sera | 6,746 | 7,115 | 6,299 | 6,455 | 6,276 | 6,285 |
| Notte | 7,095 | 7,152 | 6,950 | 6,809 | 6,630 | 6,642 |
| Smonto | 6,809 | 6,735 | 6,409 | 6,401 | 6,669 | 6,198 |
| Riposo | 6,919 | 6,915 | 6,773 | 5,972 | 6,419 | 5,804 |
| Ferie | 6,086 | 6,404 | 6,336 | 6,359 | 6,207 | 5,827 |
| Personale | 6,681 | 6,387 | 6,384 | 6,093 | 6,338 | 5,985 |

### Limiti e dettagli che cambiano l'esito

Le forme KPI dark possono essere più espressive usando `chart` come riempimento e l'inchiostro scuro comune `shapeInk: #152022`. Questa variante è verificata su tutti e 24 i colori dark: il minimo è **6,984:1 per Lime**, **7,185:1 per Cobalto** e **7,534:1 per Acqua**. Così le statistiche acquistano più energia senza rendere il calendario troppo luminoso. Nel tema chiaro restano `fill` e `ink`.

- I contrasti sono validi per i colori pieni indicati. Opacità sull'intera cella, filtri, blending o fotografie di sfondo richiedono una nuova verifica del colore effettivamente composto.
- I contorni delle card normali sono decorativi: non è richiesto né cercato 3:1 tra tutte le superfici. Quando il bordo comunica selezione o stato, usare `primary`, `outline` o il token semantico testato, senza attenuarlo con opacità.
- I settori adiacenti del grafico non sono necessariamente a 3:1 fra loro. Inserire separatori sottili del colore `surface`, mantenere legenda e numeri, e non affidare la lettura alla sola differenza di tinta. Il contrasto di ciascun `chart` con tutte le superfici previste è verificato.
- L'audit cromatico non è una certificazione di accessibilità dell'app completa: non misura dimensione dei caratteri, target touch, clipping, focus, daltonismo o qualità dell'animazione. Non misura neppure quanto un abbinamento piaccia: per questo servono le sei viste reali affiancate.

## Confronto consigliato

Mostrare gli stessi dati in calendario e statistiche, contemporaneamente in scuro e chiaro, senza cambiare densità o tipografia fra le direzioni. Nella prova includere almeno un giorno selezionato e reperibile, un festivo, due turni nello stesso giorno e modelli personalizzati. Il giudizio deve riguardare insieme atmosfera e leggibilità, non soltanto una fila di campioni colore.

Per una prima scelta: **Acqua e iris** offre l'equilibrio più convincente; **Lime botanico** conserva meglio il carattere pop cercato; **Cobalto e burro** è il riferimento se si vuole una firma blu più decisa. Tutte e tre hanno un tema chiaro progettato autonomamente, mantenendo la stessa identità delle categorie.
