@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.turni.calendario.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.GoogleDriveBackupHelper
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.MagicalActionFeedbackHUD
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Local database backups plus provider-independent JSON export/import through SAF.
 * A selected account is a profile reference, not a Drive authentication session.
 */
@Composable
fun CloudBackupScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val autoBackupEnabled = ShiftRepository.appPreferences.autoBackupDaily
    var lastBackupTime by remember { mutableStateOf(GoogleDriveBackupHelper.getCloudBackupInfo(context) ?: "Nessun backup") }
    var actionFeedback by remember { mutableStateOf<ActionFeedback?>(null) }

    val totalShifts = ShiftRepository.calendarShifts.values.sumOf { it.size }
    val totalRotations = ShiftRepository.rotations.size
    val totalModels = ShiftRepository.shiftDefinitions.size

    // Launcher per Salvare Backup su Google Drive / File
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = GoogleDriveBackupHelper.writeBackupToUri(context, uri)
            if (ok) {
                actionFeedback = ActionFeedback(
                    message = "File backup esportato",
                    timestamp = System.currentTimeMillis(),
                    isDestructive = false
                )
            } else {
                actionFeedback = ActionFeedback(
                    message = "Impossibile esportare il backup",
                    timestamp = System.currentTimeMillis(),
                    isDestructive = true
                )
            }
        }
    }

    // Launcher per Ripristinare da Google Drive / File
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = GoogleDriveBackupHelper.restoreDatabaseFromUri(context, uri)
            if (result.success) {
                actionFeedback = ActionFeedback(
                    message = "Ripristino completato con successo",
                    timestamp = System.currentTimeMillis(),
                    isDestructive = false
                )
            } else {
                actionFeedback = ActionFeedback(
                    message = result.message,
                    timestamp = System.currentTimeMillis(),
                    isDestructive = true
                )
            }
        }
    }

    BackHandler(onBack = onDismiss)

    // Wrap in Box for overlay popup
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ObsidianSurfaceContainer)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = TextPrimary)
            }

            Text(
                text = "Backup e ripristino",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Box(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))

        var showAccountPicker by remember { mutableStateOf(false) }
        val currentAccount = ShiftRepository.googleAccountProfile

        // Card Profilo / Account Google Reale
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (currentAccount != null) ObsidianPrimary else ObsidianBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentAccount != null) Icons.Default.AccountCircle else Icons.Default.PersonOff,
                            contentDescription = null,
                            tint = catalogInk(if (currentAccount != null) ObsidianSurfaceHigh else ObsidianPrimary),
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentAccount != null) currentAccount.displayName else "Nessun Account Collegato",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentAccount?.email ?: "Profilo facoltativo per i tuoi dati",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (currentAccount != null) ObsidianTertiary else ColorNegative)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentAccount != null) "Profilo selezionato" else "Nessun profilo selezionato",
                                color = if (currentAccount != null) ObsidianTertiary else ColorNegative,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = ObsidianSurfaceContainer, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentAccount != null) {
                        TextButton(
                            onClick = {
                                ShiftRepository.disconnectGoogleAccount()
                                actionFeedback = ActionFeedback(
                                    message = "Profilo rimosso",
                                    timestamp = System.currentTimeMillis(),
                                    isDestructive = true
                                )
                            }
                        ) {
                            Text("Rimuovi profilo", color = ColorNegative, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = { showAccountPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentAccount != null) ObsidianSurfaceHigh else ObsidianPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwitchAccount,
                            contentDescription = null,
                            tint = catalogInk(if (currentAccount != null) ObsidianSurfaceHigh else ObsidianPrimary),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentAccount != null) "Cambia profilo" else "Profilo di riferimento",
                            color = catalogInk(if (currentAccount != null) ObsidianSurfaceHigh else ObsidianPrimary),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Dialog Selezione Account Google Reale
        if (showAccountPicker) {
            var customEmailInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAccountPicker = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = ObsidianPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Account Google", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Scegli il profilo di riferimento. Il backup automatico rimane sul dispositivo.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )

                        ShiftRepository.deviceGoogleAccounts.forEach { acc ->
                            val isSelected = currentAccount?.email == acc.email
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) ObsidianPrimary else ObsidianBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ShiftRepository.setGoogleAccount(acc.email, acc.displayName)
                                        actionFeedback = ActionFeedback(
                                            message = "Profilo selezionato: ${acc.email}",
                                            timestamp = System.currentTimeMillis(),
                                            isDestructive = false
                                        )
                                        showAccountPicker = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Mail,
                                        contentDescription = null,
                                        tint = if (isSelected) ObsidianPrimary else TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(acc.email, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                        Text(acc.displayName, color = TextSecondary, fontSize = 11.5.sp)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianTertiary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Divider(color = ObsidianSurfaceContainer, modifier = Modifier.padding(vertical = 4.dp))

                        Text("Oppure inserisci un altro account Google:", color = TextSecondary, fontSize = 12.sp)

                        OutlinedTextField(
                            value = customEmailInput,
                            onValueChange = { customEmailInput = it },
                            label = { Text("email@gmail.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (customEmailInput.isNotBlank() && customEmailInput.contains("@")) {
                                ShiftRepository.setGoogleAccount(customEmailInput.trim(), customEmailInput.substringBefore("@"))
                                actionFeedback = ActionFeedback(
                                    message = "Profilo selezionato: ${customEmailInput.trim()}",
                                    timestamp = System.currentTimeMillis(),
                                    isDestructive = false
                                )
                                showAccountPicker = false
                            }
                        },
                        enabled = customEmailInput.isNotBlank() && customEmailInput.contains("@"),
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                    ) {
                        Text("Seleziona", color = catalogInk(ObsidianPrimary), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccountPicker = false }) {
                        Text("Annulla", color = TextSecondary)
                    }
                },
                containerColor = ObsidianSurface,
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Statistiche Dati Archiviati
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Dati del tuo Profilo",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(title = "Turni Assegnati", value = "$totalShifts")
                    StatItem(title = "Modelli Turno", value = "$totalModels")
                    StatItem(title = "Cicli Rotazione", value = "$totalRotations")
                }

                Divider(color = ObsidianSurfaceContainer, modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ultimo backup locale:", color = TextSecondary, fontSize = 12.sp)
                    Text(lastBackupTime, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Opzione Backup Automatico
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup automatico locale", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Salva una copia all’avvio dell’app. Esporta un file per conservarlo anche altrove.", color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = { isChecked ->
                        ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(autoBackupDaily = isChecked)
                        if (isChecked) {
                            val ok = GoogleDriveBackupHelper.saveCloudBackupDirect(context)
                            if (ok) {
                                lastBackupTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                actionFeedback = ActionFeedback(
                                    message = "Backup locale automatico attivato",
                                    timestamp = System.currentTimeMillis(),
                                    isDestructive = false
                                )
                            }
                        } else {
                            actionFeedback = ActionFeedback(
                                message = "Backup automatico disattivato",
                                timestamp = System.currentTimeMillis(),
                                isDestructive = false
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))


        var showConfirmRestoreDialog by remember { mutableStateOf(false) }

        // Tasto Principale 1-Tap: Salva backup sul dispositivo Diretto
        Button(
            onClick = {
                try {
                    val ok = GoogleDriveBackupHelper.saveCloudBackupDirect(context)
                    if (ok) {
                        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        lastBackupTime = now
                        actionFeedback = ActionFeedback(
                            message = "Backup locale salvato",
                            timestamp = System.currentTimeMillis(),
                            isDestructive = false
                        )
                    } else {
                        actionFeedback = ActionFeedback(
                            message = "Impossibile salvare il backup locale",
                            timestamp = System.currentTimeMillis(),
                            isDestructive = true
                        )
                    }
                } catch (e: Throwable) {
                    actionFeedback = ActionFeedback(
                        message = "Errore: ${e.localizedMessage ?: "Impossibile salvare il backup"}",
                        timestamp = System.currentTimeMillis(),
                        isDestructive = true
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = ObsidianBackground)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Salva backup sul dispositivo", color = ObsidianBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tasto Rapido: Ripristina Ultimo Backup da Google Drive
        OutlinedButton(
            onClick = {
                if (GoogleDriveBackupHelper.hasCloudBackup(context)) {
                    showConfirmRestoreDialog = true
                } else {
                    Toast.makeText(context, "Nessun backup locale disponibile", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = ObsidianPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ripristina backup locale", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Sezione Avanzata Opzionale: Condivisione o Selezione Manuale File
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val filename = "turni_backup_${LocalDate.now()}.json"
                    createBackupLauncher.launch(filename)
                },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
            ) {
                Text("Esporta File...", color = TextSecondary, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    restoreBackupLauncher.launch(arrayOf("application/json", "text/*"))
                },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
            ) {
                Text("Sfoglia File...", color = TextSecondary, fontSize = 12.sp)
            }
        }

        // Dialog di Conferma Ripristino
        if (showConfirmRestoreDialog) {
            val backupDate = GoogleDriveBackupHelper.getCloudBackupInfo(context) ?: "recente"
            AlertDialog(
                onDismissRequest = { showConfirmRestoreDialog = false },
                title = { Text("Ripristina backup locale", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Vuoi ripristinare il backup del $backupDate?\nI turni, le rotazioni e i modelli verranno sostituiti con quelli della copia locale.",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmRestoreDialog = false
                            try {
                                val res = GoogleDriveBackupHelper.restoreCloudBackupDirect(context)
                                if (res.success) {
                                    actionFeedback = ActionFeedback(
                                        message = "Ripristino completato con successo (${res.shiftsCount} turni)",
                                        timestamp = System.currentTimeMillis(),
                                        isDestructive = false
                                    )
                                } else {
                                    actionFeedback = ActionFeedback(
                                        message = res.message,
                                        timestamp = System.currentTimeMillis(),
                                        isDestructive = true
                                    )
                                }
                            } catch (e: Throwable) {
                                actionFeedback = ActionFeedback(
                                    message = "Errore durante il ripristino: ${e.localizedMessage ?: "Errore"}",
                                    timestamp = System.currentTimeMillis(),
                                    isDestructive = true
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                    ) {
                        Text("Conferma Ripristino", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmRestoreDialog = false }) {
                        Text("Annulla", color = TextMuted)
                    }
                },
                containerColor = ObsidianSurfaceContainer,
                shape = RoundedCornerShape(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    MagicalActionFeedbackHUD(
        feedback = actionFeedback,
        onDismiss = { actionFeedback = null },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 16.dp)
    )
    }
}

@Composable
private fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ObsidianPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}
