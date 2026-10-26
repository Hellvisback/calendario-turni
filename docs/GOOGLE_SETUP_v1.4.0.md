# TurniSync — configurazione Google per APK v1.4.0

Le chiamate Drive e Calendar nell’APK sono reali. La configurazione Google Cloud del proprietario del progetto non è stata fornita né verificata: il collaudo completo con consenso e trasferimento remoto resta da eseguire. Non sono incorporati account, token o password.

## Configurazione richiesta

1. Selezionare o creare il progetto Google Cloud destinato a TurniSync.
2. Abilitare Google Drive API e Google Calendar API.
3. Configurare Google Auth Platform (branding, pubblico e accesso ai dati). Per app in prova, aggiungere esplicitamente gli account di test consentiti. La distribuzione pubblica può richiedere la verifica Google dei permessi richiesti.
4. Creare un client OAuth **Android**, con package `com.turni.calendario` e impronta SHA-1 del certificato che firma l’APK:
   `AB:1B:01:83:6A:C9:AC:F1:32:F5:F1:4E:D4:7A:C8:66:DD:9C:B6:01`
   Questa è la chiave debug già usata dal progetto. Per una futura firma release/Play App Signing registrare anche l’impronta di quella firma. Non inserire client secret nell’app.
5. Dall’app aprire Impostazioni → Backup e ripristino oppure Google Calendar → Accedi con Google. L’utente sceglie il proprio account nel selettore Google e autorizza i dati richiesti.

## Permessi richiesti dall’app

- Profilo: `openid`, `https://www.googleapis.com/auth/userinfo.email`, `https://www.googleapis.com/auth/userinfo.profile`.
- Backup: `https://www.googleapis.com/auth/drive.appdata`, per il solo spazio privato dell’app su Drive.
- Calendario: `https://www.googleapis.com/auth/calendar.calendarlist.readonly`, `https://www.googleapis.com/auth/calendar.events`.

I permessi dati sono richiesti quando si usa la relativa funzione. L’identità viene memorizzata soltanto dopo la verifica HTTPS con Google; i token non vengono scritti nel database. La disconnessione locale funziona anche offline; la revoca online è tentata quando è disponibile un token, e può essere gestita anche dalle impostazioni di sicurezza dell’account Google.

## Prova da completare con account autorizzato

- Scegliere account A e concedere Drive; salvare un backup e verificare il ripristino tramite anteprima (annullare se non desiderato).
- Cambiare ad account B: non deve risultare collegato A e non devono comparire i backup privati di A.
- Concedere Calendar, scegliere esplicitamente un calendario scrivibile e sincronizzare un periodo di prova. Verificare orari notturni, straordinari, doppio turno e giorni interi.
- Ripetere la sincronizzazione: gli eventi TurniSync vengono aggiornati senza duplicati. Eventi personali e turni di altri lavori non vengono rimossi.
- Negare/annullare il consenso o togliere rete: nessun falso messaggio di operazione riuscita.

La copia automatica locale resta esplicitamente locale: viene aggiornata all’apertura dell’app e non costituisce un backup automatico su Drive. Il backup remoto viene eseguito dal pulsante dedicato.

## Documentazione ufficiale

- [Autorizzazione Google su Android](https://developer.android.com/identity/authorization)
- [Configurazione client Android e certificati](https://developers.google.com/android/guides/client-auth)
- [Spazio privato Drive](https://developers.google.com/workspace/drive/api/guides/appdata)
