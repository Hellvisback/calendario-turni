package com.turni.calendario.ui.screens

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DriveAutomaticBackup
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal enum class GoogleFeature(val dataScopes:List<String>){
    PROFILE(emptyList()),
    DRIVE(listOf("https://www.googleapis.com/auth/drive.appdata")),
    CALENDAR(listOf("https://www.googleapis.com/auth/calendar.calendarlist.readonly","https://www.googleapis.com/auth/calendar.events"));
    val scopes get()=listOf("openid","https://www.googleapis.com/auth/userinfo.email","https://www.googleapis.com/auth/userinfo.profile")+dataScopes
}

/** Only a verified Google userinfo response can create a remembered account. Never stores tokens. */
internal object GoogleAccountSession {
    private const val PREFS="turnisync_google_identity_v1"
    fun initialize(context:Context){
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val email=p.getString("email",null);val subject=p.getString("subject",null)
        if(p.getBoolean("verified",false)&&!email.isNullOrBlank()&&!subject.isNullOrBlank())
            ShiftRepository.setVerifiedGoogleAccount(email,p.getString("name",null)?:email.substringBefore('@'))
    }
    fun accept(context:Context,json:JSONObject,expectedEmail:String){
        val email=json.optString("email");val sub=json.optString("sub")
        require(email.isNotBlank()&&sub.isNotBlank()&&json.optBoolean("email_verified",false)&&email.equals(expectedEmail,true)){"L’account restituito da Google non corrisponde a quello selezionato."}
        val name=json.optString("name").ifBlank{email.substringBefore('@')}
        val previous=ShiftRepository.googleAccountProfile?.email
        if(previous!=null&&!previous.equals(email,true))DriveAutomaticBackup.disable(context)
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("verified",true).putString("email",email).putString("subject",sub).putString("name",name).apply()
        ShiftRepository.setVerifiedGoogleAccount(email,name)
    }
    fun clear(context:Context){DriveAutomaticBackup.disable(context);context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();ShiftRepository.disconnectGoogleAccount()}
}

/** Account choice and Google consent are separate from the requested Drive/Calendar operation. */
internal class GoogleAccess(private val activity:ComponentActivity,private val scope:CoroutineScope,private val onError:(String)->Unit){
    var busy by mutableStateOf(false);private set
    var chooseAccount:((Intent)->Unit)?=null
    var resolveConsent:((IntentSenderRequest)->Unit)?=null
    private var feature=GoogleFeature.PROFILE
    private var email:String?=null
    private var completion:((String)->Unit)?=null
    private var cachedToken:String?=null
    private val client get()=Identity.getAuthorizationClient(activity)
    fun authorize(feature:GoogleFeature,chooseAccount:Boolean=false,onToken:(String)->Unit){
        if(busy)return
        if(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)!=ConnectionResult.SUCCESS){onError("Aggiorna o attiva Google Play Services per collegare il tuo account.");return}
        this.feature=feature;completion=onToken;busy=true
        val remembered=ShiftRepository.googleAccountProfile?.email
        if(chooseAccount||remembered.isNullOrBlank()){
            val intent=AccountPicker.newChooseAccountIntent(AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google")).setAlwaysShowAccountPicker(true).build())
            try{this.chooseAccount?.invoke(intent)?:cancel()}catch(_:Exception){fail("Non è possibile aprire la scelta dell’account Google.")}
        }else{email=remembered;requestAuthorization()}
    }
    fun accountResult(resultCode:Int,data:Intent?){
        if(resultCode!=Activity.RESULT_OK){cancel();return}
        val picked=data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if(picked.isNullOrBlank()||data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)!="com.google"){fail("Nessun account Google selezionato.");return}
        email=picked;requestAuthorization()
    }
    private fun requestAuthorization(){
        val selected=email?:return cancel()
        val request=AuthorizationRequest.builder().setAccount(Account(selected,"com.google"))
            .setRequestedScopes(feature.scopes.map{Scope(it)}).build()
        client.authorize(request).addOnSuccessListener{result->
            if(result.hasResolution()){
                val pending=result.pendingIntent
                if(pending==null)fail("Google non ha restituito una richiesta di autorizzazione valida.")
                else try{resolveConsent?.invoke(IntentSenderRequest.Builder(pending.intentSender).build())?:cancel()}catch(_:Exception){fail("Non è possibile aprire il consenso Google.")}
            }else acceptResult(result)
        }.addOnFailureListener{error->fail(googleAccessError(error))}
    }
    fun authorizationResult(resultCode:Int,data:Intent?){
        if(resultCode!=Activity.RESULT_OK){cancel();return}
        if(completion==null){fail("L’operazione è stata interrotta. Riprova il collegamento.");return}
        try{acceptResult(client.getAuthorizationResultFromIntent(data))}catch(e:Exception){fail(googleAccessError(e))}
    }
    private fun acceptResult(result:AuthorizationResult){
        val token=result.accessToken
        if(token.isNullOrBlank()||!result.grantedScopes.containsAll(feature.scopes)){fail("Non hai concesso tutti i permessi richiesti. Nessuna operazione eseguita.");return}
        val expected=email?:return cancel()
        scope.launch{
            try{
                val profile=withContext(Dispatchers.IO){googleUserInfo(token)}
                GoogleAccountSession.accept(activity,profile,expected)
                cachedToken=token
                val action=completion;completion=null;busy=false;action?.invoke(token)
            }catch(e:CancellationException){busy=false;throw e}catch(e:Exception){fail(e.message?:"Impossibile verificare l’account Google.")}
        }
    }
    fun disconnect(onDone:()->Unit){
        if(busy)return
        val remembered=ShiftRepository.googleAccountProfile?.email
        val token=cachedToken;cachedToken=null
        // Local logout must also work offline, after account removal or revoked Google consent.
        GoogleAccountSession.clear(activity);completion=null;email=null;onDone()
        if(token!=null){scope.launch{try{withContext(Dispatchers.IO){revokeGoogleAccess(token)}}catch(_:Exception){ /* Local state is already cleared; online grants can be revoked in Google settings. */ }}}
        else if(!remembered.isNullOrBlank()){
            val request=AuthorizationRequest.builder().setAccount(Account(remembered,"com.google"))
                .setRequestedScopes(GoogleFeature.PROFILE.scopes.map{Scope(it)}).build()
            client.authorize(request).addOnSuccessListener{result->
                val existing=result.accessToken
                if(!result.hasResolution()&&!existing.isNullOrBlank())scope.launch{try{withContext(Dispatchers.IO){revokeGoogleAccess(existing)}}catch(_:Exception){ /* No new consent is requested during logout. */ }}
            }.addOnFailureListener{ /* Signed-out state never depends on network success. */ }
        }
    }
    private fun cancel(){busy=false;completion=null;email=null}
    private fun fail(message:String){cancel();onError(message)}
}

@Composable
internal fun rememberGoogleAccess(onError:(String)->Unit):GoogleAccess{
    var context=LocalContext.current
    while(context is ContextWrapper&&context !is ComponentActivity)context=context.baseContext
    val activity=context as ComponentActivity
    val scope=rememberCoroutineScope();val latestError by rememberUpdatedState(onError)
    val access=remember(activity){GoogleAccess(activity,scope){latestError(it)}}
    val account=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){access.accountResult(it.resultCode,it.data)}
    val consent=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){access.authorizationResult(it.resultCode,it.data)}
    SideEffect{access.chooseAccount={account.launch(it)};access.resolveConsent={consent.launch(it)}}
    return access
}

private fun googleAccessError(error:Exception):String=when((error as? ApiException)?.statusCode){
    10->"Il collegamento Google non è configurato per questa versione dell’app. Occorre completare la configurazione Google di TurniSync."
    7->"Connessione assente. Controlla Internet e riprova."
    16,12501->"Accesso Google annullato. Nessuna operazione eseguita."
    else->"Google non ha autorizzato il collegamento. Verifica la connessione e la configurazione dell’app, poi riprova."
}
private fun googleUserInfo(token:String):JSONObject{
    val connection=URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
    return try{
        connection.connectTimeout=20000;connection.readTimeout=20000;connection.setRequestProperty("Authorization","Bearer $token")
        if(connection.responseCode !in 200..299)throw IllegalStateException("Google non ha confermato l’identità dell’account. Ricollegalo e riprova.")
        val text=connection.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}
        require(text.length<65536){"Risposta Google non valida."};JSONObject(text)
    }finally{connection.disconnect()}
}
private fun revokeGoogleAccess(token:String){
    val connection=URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection
    try{
        connection.requestMethod="POST";connection.connectTimeout=20000;connection.readTimeout=20000;connection.doOutput=true
        connection.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
        connection.outputStream.use{it.write(("token="+URLEncoder.encode(token,"UTF-8")).toByteArray(Charsets.UTF_8))}
        check(connection.responseCode in 200..299){"Revoca Google non riuscita."}
    }finally{connection.disconnect()}
}
