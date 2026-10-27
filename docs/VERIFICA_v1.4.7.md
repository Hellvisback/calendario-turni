# Verifica v1.4.7 — 26 ottobre 2026

Restyling dei quattro widget. Numeri dei giorni da 11 dp a 15 dp nei widget bassi e 18 dp nei widget più alti; data giornaliera 17 dp. Riga LUN/MAR/MER/GIO/VEN/SAB/DOM dedicata, una sola volta in alto. Intestazione data/lavoro compatta, frecce allineate. Riquadri dei turni adattati allo spazio, sigle fino a 14 dp e attacco fino a 12 dp; sul giornaliero attacco 15 dp e nome del turno. Rimangono reperibilità, festività, oggi e indicatori di ulteriori turni. Nessun indicatore di trasferta o straordinario.

Verificato sul Realme 8 Pro nelle dimensioni esistenti: settimanale basso con sigla e attacco entrambi visibili; due settimane; novembre mensile a sei righe; Oggi e domani senza taglio della parola Domani. Controllo scuro e chiaro, con ripristino finale del tema Scuro. Screenshot final-weeks.png e final-month.png e prove chiare nel workspace Codex work/v147-qa. Nessuna modifica al posizionamento o alle dimensioni dei widget sul launcher.

Build assembleDebug test riuscita: 350 test debug e 350 release, zero errori, failure e skipped. Runtime Java 11 locale e script build-native.ps1 usati perché il wrapper Android Studio restituisce Access denied nel profilo corrente. Firma debug originale conservata. APK v1.4.7 (28) installato. Database finale identico byte per byte al backup prima delle prove.

SHA-256 APK locale e installato: c5590bdd702423ac6c8569cdd530474649879d4cbac226564562bd3c508c1120.

Limiti: in host estremamente piccoli resta la rappresentazione compatta; verificati i formati effettivamente installati sul Realme, non tutti i launcher Android. Le animazioni interne all’app non sono state modificate.
