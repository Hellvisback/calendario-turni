# Riproduzione sintetica della selezione calendario

Solo per un dispositivo/emulatore collegato ad ADB e per il package separato `com.turni.calendario.setupqa`. Non usa il database dell'app personale.

1. Compilare `assembleDebug -PsetupProbe` tramite lo script di verifica.
2. Installare solo l'APK con applicationId `com.turni.calendario.setupqa`.
3. Eseguire `python scripts/device_qa/calendar_probe.py seed`. Sostituisce soltanto i dati del package QA con la fixture sintetica inclusa.
4. Eseguire `python scripts/device_qa/check_anchor.py` per applicare quattro mattine e attivare l'intervallo gomma sull'ultima.

Gli screenshot e il dump UI vanno in `qa-artifacts/calendar/`, esclusa da Git. I giorni 18-21 si riferiscono al mese aperto dal calendario. Il dispositivo deve essere sbloccato e la prima configurazione QA viene marcata completa dal seed. Non è un test di FPS o di compatibilità di tutti i launcher.

## Primo avvio e guida interattiva

`onboarding_probe.py` lavora esclusivamente su `com.turni.calendario.setupqa`. Compilare e installare prima un APK debug con `-PsetupProbe`; impostare `ADB` oppure `ANDROID_HOME` e scegliere il dispositivo tramite `--serial`. I comandi di input e acquisizione richiedono che l'app QA sia in primo piano. Il package personale non è un parametro configurabile.

Per osservare un primo avvio nuovo, senza usare il seed del calendario:

```bash
# Cancella soltanto l'installazione di prova e avvia l'introduzione.
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 reset --launch
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 state --name before-practice
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 capture welcome
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 list
```

L'helper permette tap/pressioni prolungate su testo o descrizione esatti, oppure su coordinate ricavate da una cattura aggiornata:

```bash
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 tap "Prepara il mio calendario"
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 tap "Continua"
# Se ci sono più controlli con la stessa etichetta, specificare --index.
# long accetta gli stessi argomenti di tap e una durata --duration in millisecondi.
```

Dopo le interazioni di prova si può confrontare il calendario persistente:

```bash
python3 scripts/device_qa/onboarding_probe.py --serial emulator-5554 state \
  --name after-practice --compare qa-artifacts/onboarding/before-practice.json
```

Il confronto fallisce se cambiano assegnazioni o dati giornalieri. Gli artefatti JSON contengono conteggi, tema/formati, stato del primo avvio e hash, senza esportare il database o i testi delle note. Le modifiche volute a nome lavoro, tema e modelli non fanno parte dell'hash calendario: verificarle separatamente con il riepilogo/UI. La presenza di `calendarUnchanged` non attesta tutti i campi del database.

L'output predefinito è `qa-artifacts/onboarding/`; `--output` o `TURNISYNC_ONBOARDING_QA_DIR` possono indicare una cartella diversa. `shot` salva il PNG, `dump` l'XML e `capture` entrambi. Non usare `calendar_probe.py seed` prima della prova del primo avvio: quel comando marca intenzionalmente l'introduzione come già completata.

Percorso da verificare: nuovo avvio e ripristino separato; Aspetto nei due temi; nome lavoro/editor con tastiera e riapertura; modelli aggiunti solo su scelta esplicita; sei gesti della prova; opzioni espandibili e ritorno dagli editor; conclusione con calendario vuoto; successivo avvio senza ripetere la configurazione. Ripetere i punti di geometria su larghezza ridotta e caratteri grandi, poi ripristinare le impostazioni del dispositivo di prova.

Il verbale [VERIFICA_ONBOARDING_2026-11-17.md](../../docs/VERIFICA_ONBOARDING_2026-11-17.md) distingue gli esiti già registrati dai controlli ancora da eseguire.
