package com.pennywiseai.shared.data.statement

actual object SharedPdfTextExtractor {
    actual fun extractText(filePath: String): String {
        throw UnsupportedOperationException(
            "Direct iOS PDF extraction is not wired in Kotlin yet. " +
                "Extract text via Swift PDFKit and call importFromText."
        )
    }
}
