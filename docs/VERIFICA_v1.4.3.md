# Verifica TurniSync v1.4.3 — 26 ottobre 2026

## Widget
Tutti e quattro i widget usano una testata di 32 dp su un’unica riga: freccia precedente, data, freccia successiva adiacente e nome del lavoro allineato a destra. La data ha una larghezza massima calcolata; il nome usa ellissi se lungo. Ridotto il padding verticale da 8 a 4 dp per lato, rimossa la sottoriga del lavoro. Le frecce conservano target da 32 dp; il titolo riporta al periodo corrente.

Il settimanale già installato sul Realme ora mostra anche l’attacco. La politica verticale evita che l’orario scompaia aumentando leggermente l’altezza della cella. Turno, attacco e reperibilità hanno priorità sugli extra. Straordinario: simbolo celeste e durata quando lo spazio lo consente; trasferta: aeroplanino verde; note: piccola icona discreta. Nel settimanale compatto sono usati i lati liberi della data, riservando la destra alla campanella quando presente. Nel due settimane e nei formati con spazio gli extra si dispongono in una riga compatta sotto il turno. Le assegnazioni aggiuntive non vengono sacrificate per mostrare gli extra.

## Festività
Nel calendario principale e nei quattro widget la festività attiva ha un sottile quadratino rosso arrotondato attorno al solo numero, senza riempire la giornata. Il bordo della cella selezionata resta distinto. Oggi conserva lo sfondo e il badge, la reperibilità conserva il numero ambra e la campanella. Il colore condiviso è rosso chiaro nel tema scuro e rosso profondo nel tema chiaro. Un piccolo distacco ottico mantiene leggibile il contorno sul badge di oggi. Il nome della festività è presente anche nella descrizione accessibile. Sono rispettate le festività abilitate nelle preferenze, non vengono aggiunte date festive arbitrariamente.

## Verifiche
- `assembleDebug test`: BUILD SUCCESSFUL. **337 test debug e 337 release**, zero failure/error/skipped.
- Verificata la navigazione avanti/indietro del settimanale con il nuovo posizionamento delle frecce; ripristinato il periodo iniziale.
- Screenshot reali Realme per settimanale, due settimane, Oggi/domani e mensile compatto. Provati temi chiaro e scuro e combinazione festività + selezione + reperibilità.
- Provati temporaneamente sul turno del 26 ottobre trasferta, +30 minuti e nota: aeroplanino nel settimanale, tutti i simboli nel due settimane, durata e simboli in Oggi/domani. Le modifiche sono state annullate mediante Annulla.
- Database dopo le prove esattamente uguale alla copia iniziale: 122 assegnazioni, modelli, lavori, rotazioni, note e configurazioni conservati. Ripristinato il tema Scuro.
- Rendering statico nativo, densità del dispositivo e limite di 1,5 milioni di pixel preservati. Nessun timer di animazione o filtro pesante aggiunto ai widget.
- APK v1.4.3, versionCode 24, firma debug esistente conservata. SHA-1 certificato AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01.
- SHA-256 APK: `26c7512c8fc9e195cbe4abc02d18c28616474a1bba251534d7f4adcfeb10fa06`. Installazione finale sul Realme eseguita; hash locale/installato identico e database finale uguale alla copia iniziale.
- Nessuna release GitHub e nessun accesso OneDrive.

## Limiti delle dimensioni
Il mensile già presente sul launcher conserva un’altezza molto inferiore alla dimensione consigliata per sei settimane: in quella configurazione estrema resta compatto, con data/sigla e campanella; l’orario completo e gli extra richiedono più altezza. Non è stato ridimensionato il widget dell’utente né cambiata la disposizione del launcher. I dettagli completi rimangono accessibili toccando il giorno. Nessuna garanzia di visualizzazione simultanea di ogni extra nelle celle minime.

APK: TurniSync-v1.4.3.apk. Screenshot e confronto dati nel workspace `work/v143-qa`.
