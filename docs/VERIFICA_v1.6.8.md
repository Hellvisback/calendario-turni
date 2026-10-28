# Verifica v1.6.8 — widget adattivi e anteprime aggiornate

27 ottobre 2026. APK 1.6.8 / versionCode 40 aggiornato sul Realme 8 Pro con install -r. Nessuna disinstallazione dell’app principale o pubblicazione GitHub.

## Correzioni

- Selettore widget: quattro nuove PNG ricavate dal renderer Android reale, con risorse versionate per aggiornare le anteprime precedenti. Verificate visivamente nel selettore Realme.
- Settimana: target 4×1, altezza minima 56 dp, ridimensionamento orizzontale. Due settimane: target 4×2, altezza minima 112 dp, ridimensionamento orizzontale. Le vecchie altezze minime richiedevano una riga in più su alcuni launcher.
- Mese: target 4×4, larghezza minima 280 dp, ridimensionamento solo verticale per impedire la compressione a tre colonne. I launcher decidono la griglia fisica; non esiste minResizeCellWidth portabile. Sui launcher anteriori ad Android 12 restano i vincoli in dp.
- Oggi/domani: nella dimensione compatta sigla e orario completo sulla stessa riga, senza nome esteso ridondante. Nome esteso nei formati grandi. Larghezza del testo calcolata dallo spazio realmente disponibile, senza il margine fisso che tagliava gli orari. Titolo adattato anche con caratteri di sistema ingranditi; data e campanella in spazi distinti.
- Android 12+: RemoteViews per le dimensioni esatte comunicate dal launcher, fino a quattro varianti e budget condiviso dei bitmap. Fallback min/max per i launcher precedenti. Dati letti una sola volta per aggiornamento, non per ciascuna variante.

## Verifiche

- Build Debug e APK test riuscite; 401 test JVM Debug e 401 Release, nessun errore.
- Instrumentation Android nativa: 60 rendering RemoteViews, dieci combinazioni di dimensioni, temi chiaro/scuro e font scale 100/130/150%. Nessuna modifica del repository utente: fixture isolate passate al renderer.
- Verificata interamente la stringa orario alle larghezze compatte; misure native Paint per 06:00, 14:00, 22:00 e 23:59. Controllati screenshot giornaliero stretto, settimanale, panorama e mese a sei righe. Il caso 120×148 dp al 150% mostra titolo e orari completi.
- Screenshot del selettore Realme conferma le nuove anteprime. Widget già presenti nuovamente aggiornati con dati reali dopo l’instrumentation.
- Database finale identico alla baseline, esclusa la sola data di esportazione. Nessuna modifica di turni, lavori, rotazioni o account.

## Limiti e ingombri esistenti

L’aggiornamento dei metadati non cambia automaticamente i posti già occupati nel launcher: per acquisire 4×1 o 4×2 occorre rimuovere e riaggiungere il relativo widget. Nessuna modifica automatica della home personale è stata effettuata. Il secondo telefono dell’utente non era collegato: la correzione del taglio è verificata con dimensioni e caratteri differenti sul renderer Android, non con una prova fisica su quel dispositivo. I launcher OEM possono applicare spaziature proprie.

APK locale: outputs/TurniSync-v1.6.8.apk
SHA256: ed376af3bac1693e1a4ac42b166f0e4caddc1060e8f1eccef279f6721c258b1c
