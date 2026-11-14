# TurniSync · Schermate vettoriali

L’esportazione riproduce le schermate della visualizzazione Palette coordinate, versione 68. Contiene 27 viste per ciascuna combinazione di palette e tema, per un totale di 162 viste.

## Quale cartella aprire

- **01-tracciati** contiene le schermate pronte da trascinare sulla tela di Figma. Testi, icone e forme sono tracciati vettoriali separati e raggruppati. Per questo set non serve installare font.
- **02-testo-selezionabile** contiene le stesse viste con le scritte in elementi SVG `text`. Per mantenere l’aspetto originale usa Manrope e Consolas. La possibilità di modificare le parole dipende da come l’editor importa il testo SVG. [Manrope su Google Fonts](https://fonts.google.com/specimen/Manrope)

In Figma apri un file di progettazione e trascina uno o più SVG sulla tela. Puoi selezionare i gruppi o i singoli tracciati nel pannello dei livelli. I file sono elementi vettoriali statici. Layout automatici, collegamenti del prototipo e componenti Figma riutilizzabili richiedono la configurazione in Figma. [Guida Figma all’importazione di SVG](https://help.figma.com/hc/en-us/articles/360040028034-Add-images-and-videos-to-designs)

## Cosa include

Ogni set è suddiviso in **Lime botanico**, **Cobalto e burro** e **Acqua e iris**, ciascuno con cartelle `chiaro` e `scuro`.

- Calendario e dettagli di un turno, festività, note, doppio turno, trasferta e giornata vuota.
- Modifica dei turni con Cambia, Più turni, Rotazioni, Reperibilità e Gomma. Include anche le viste del selettore dopo lo scorrimento orizzontale.
- Statistiche mensili, annuali e personalizzate.
- Impostazioni, Aspetto, galleria delle 35 forme Material 3 e Turni e lavori.

Le schermate standard misurano **390 × 818 px**. I file con suffisso `-completa` mantengono la larghezza originale e estendono l’altezza per mostrare i contenuti normalmente visibili scorrendo. L’indice CSV riporta nomi e dimensioni.

## Fedeltà e verifica

L’HTML originale è stato renderizzato in Chrome. Colori, coordinate, testi, icone e curve delle forme sono stati estratti da quel rendering. Le ombre sfumate sono approssimate con contorni vettoriali trasparenti. Possono esserci piccole differenze nell’antialiasing del testo e dei bordi. I ritagli delle schermate sono espressi come clip vettoriali.

La verifica XML conferma che tutti gli SVG sono composti solo da elementi vettoriali e, nel secondo set, testo. Sono stati eseguiti 54 confronti di rendering in Chrome e controlli visivi mirati. La verifica è stata effettuata in Chrome, senza un’importazione di prova in Figma.

L’allegato HTML di origine ha SHA-256 `cf2e659cbc509368816d6e7b416988d028ff13910cbb4afdd7ba21fd096e0a60`. [Visualizzazione originale su Sites](https://calendario-turni-design.marcograziani72.chatgpt.site)
