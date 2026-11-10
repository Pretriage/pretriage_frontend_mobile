package com.proyecto_final.triage.utils

import android.content.Intent
import android.net.Uri
import com.proyecto_final.triage.appContext

actual fun dialEmergency(numero: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero"))
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    appContext.startActivity(intent)
}
