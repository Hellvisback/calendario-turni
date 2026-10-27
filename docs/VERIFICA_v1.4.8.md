# Verifica v1.4.8 — 26 ottobre 2026

Ridimensionamento intermedio dei widget richiesto dopo v1.4.7: date 13 dp nei widget bassi (prima 15), 14,5 dp negli altri (prima 18); riquadri turno con limite 44 dp anziché 56; sigle 12,5 dp anziché 14 e orario 11,25 dp anziché 12 nei formati alti. Nel giornaliero data 14,5 dp, label 12,5 dp, nome turno 11,25 dp e orario 13,5 dp; limite riquadro 50 dp. Conservata la riga unica dei giorni della settimana e la presenza dell’attacco nel settimanale basso.

Verifica sul Realme 8 Pro: settimanale, due settimane, giornaliero e novembre mensile a sei righe. Screenshot work/v148-qa/final-weeks.png e final-month.png nel workspace Codex. Tema Scuro mantenuto. Build assembleDebug test superata, 350 test per variante senza errori/failure/skipped. Database invariato byte per byte, firma originale verificata.

APK v1.4.8 (29) installato; SHA-256 locale e installato d5566f15ae2ce8cf87d94c2828525a2a5e0b72ff35dfedb45858acf95fa18c41. Non eseguite nuove prove su tutti i launcher o un benchmark dell’app: modifica limitata alle proporzioni statiche dei widget.
