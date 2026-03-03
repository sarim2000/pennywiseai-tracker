package com.pennywiseai.tracker.data.statement

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTextExtractor {

    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    fun extractText(context: Context, uri: Uri): String {
        ensureInitialized(context)

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open PDF at $uri")

        return inputStream.use { stream ->
            PDDocument.load(stream).use { document ->
                PDFTextStripper().getText(document)
            }
        }
    }
}
