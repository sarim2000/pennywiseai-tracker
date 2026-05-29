package com.pennywiseai.tracker.utils

import android.content.Context
import com.pennywiseai.tracker.core.Constants
import java.net.URLEncoder

object SmsReportUrlBuilder {
    fun buildUrl(context: Context, smsBody: String?, smsSender: String?): String {
        val encodedMessage = URLEncoder.encode(smsBody ?: "", "UTF-8")
        val encodedSender = URLEncoder.encode(smsSender ?: "", "UTF-8")
        val encryptedDeviceData = DeviceEncryption.encryptDeviceData(context)
        val encodedDeviceData = if (encryptedDeviceData != null) {
            URLEncoder.encode(encryptedDeviceData, "UTF-8")
        } else ""
        return "${Constants.Links.WEB_PARSER_URL}/#message=$encodedMessage&sender=$encodedSender&device=$encodedDeviceData&autoparse=true"
    }
}
