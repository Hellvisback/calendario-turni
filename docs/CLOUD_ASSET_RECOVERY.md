# Recupero delle immagini Android mancanti nel checkout

Il primo controllo Linux del commit `5771268b691a1976cfce00b24db30ad9432295dd` fallisce nel collegamento delle risorse: mancano `ic_launcher_foreground` e le quattro anteprime `preview_turnisync_v168_*`. Lo stesso errore compare nel [controllo GitHub](https://github.com/Hellvisback/calendario-turni/actions/runs/43602157681).

La regola generale `*.png` in `.gitignore` escludeva anche immagini necessarie all'app. È stata aggiunta un'eccezione circoscritta alle risorse Android. La successiva compilazione Kotlin ha individuato anche il logo `google_g`, recuperato con lo stesso metodo.

Le immagini sono state recuperate senza modificare i byte dall'[APK ufficiale beta.2](https://github.com/Hellvisback/calendario-turni/releases/download/v1.8.0-beta.2/app-release-v1.8.0-beta.2.apk), che già contiene le anteprime v1.6.8 e l'icona. SHA-256 dell'APK verificato: `b8ba3c3c4e73319937db8bbda2dc54fe95ca588605a6645347f4b517b60ec4c9`.

La tabella risorse letta con `aapt dump --values resources` associa i nomi ai seguenti file nell'APK:

| Risorsa | File nell'APK |
| --- | --- |
| app_logo_transparent | res/Sw.png |
| ic_launcher_foreground | res/83.png |
| google_g | res/qL.png |
| preview_turnisync_v168_month_grid | res/EP.png |
| preview_turnisync_v168_today_tomorrow | res/Xg.png |
| preview_turnisync_v168_week_row | res/XO.png |
| preview_turnisync_v168_weeks_panorama | res/a_.png |

Il recupero non cambia versione, logica dell'app o firma. La chiave originale del proprietario rimane esterna al repository. Le build del cloud servono alla verifica; non aggiornare il telefono personale con una firma differente.
