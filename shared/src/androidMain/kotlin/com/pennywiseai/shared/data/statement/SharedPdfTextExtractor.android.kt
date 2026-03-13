package com.pennywiseai.shared.data.statement

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

actual object SharedPdfTextExtractor {
    actual fun extractText(filePath: String): String {
        val file = File(filePath)
        PDDocument.load(file).use { doc ->
            return PDFTextStripper().getText(doc)
        }
    }
}
