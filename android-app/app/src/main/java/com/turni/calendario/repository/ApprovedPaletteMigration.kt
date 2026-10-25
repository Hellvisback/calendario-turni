package com.turni.calendario.repository

/** Adopt the approved design once; any color customized away from a legacy default is retained. */
fun adoptApprovedPalette(snapshot:DatabaseSnapshot):DatabaseSnapshot {
    val replacements=mapOf(
        "mattina" to (0xFF0284C7L to 0xFFF2C46DL),
        "pomeriggio" to (0xFFEF4444L to 0xFFEE9C9CL),
        "notte" to (0xFF22C55EL to 0xFFBAAEF6L),
        "smonto_notte" to (0xFF06B6D4L to 0xFF83CDB7L),
        "riposo" to (0xFFEC4899L to 0xFFAAB9CEL),
        "ferie" to (0xFF2563EBL to 0xFF94C4EFL),
        "giornaliero" to (0xFF10B981L to 0xFF83CDB7L),
        "mat_dom" to (0xFFF59E0BL to 0xFFF2C46DL),
        "uffici" to (0xFF64748BL to 0xFFAAB9CEL),
        "donazione" to (0xFFEAB308L to 0xFFEE9C9CL),
        "malattia" to (0xFF84CC16L to 0xFFB4D995L)
    )
    return snapshot.copy(definitions=snapshot.definitions.map{definition->
        val replacement=replacements[definition.id]
        if(replacement!=null&&definition.colorHex==replacement.first)definition.copy(colorHex=replacement.second)else definition
    },profiles=snapshot.profiles.map{profile->
        if(profile.id=="principale"&&profile.colorHex==0xFF0284C7L)profile.copy(colorHex=0xFFBAAEF6L)else profile
    })
}
