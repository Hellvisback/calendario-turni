# Riproduzione sintetica della selezione calendario

Solo per un dispositivo/emulatore collegato ad ADB e per il package separato `com.turni.calendario.setupqa`. Non usa il database dell'app personale.

1. Compilare `assembleDebug -PsetupProbe` tramite lo script di verifica.
2. Installare solo l'APK con applicationId `com.turni.calendario.setupqa`.
3. Eseguire `python scripts/device_qa/calendar_probe.py seed`. Sostituisce soltanto i dati del package QA con la fixture sintetica inclusa.
4. Eseguire `python scripts/device_qa/check_anchor.py` per applicare quattro mattine e attivare l'intervallo gomma sull'ultima.

Gli screenshot e il dump UI vanno in `qa-artifacts/calendar/`, esclusa da Git. I giorni 18-21 si riferiscono al mese aperto dal calendario. Il dispositivo deve essere sbloccato e la prima configurazione QA viene marcata completa dal seed. Non è un test di FPS o di compatibilità di tutti i launcher.
