package com.proyecto_final.triage.utils

import platform.UIKit.UIApplication
import platform.Foundation.NSURL

actual fun dialEmergency(numero: String) {
    val url = NSURL.URLWithString("tel:$numero")!!
    UIApplication.sharedApplication.openURL(url)
}
