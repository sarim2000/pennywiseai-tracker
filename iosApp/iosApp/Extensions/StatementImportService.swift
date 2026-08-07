import Foundation
import PDFKit
import Shared

/// One statement-import pipeline for every entry point — the Settings picker,
/// onboarding, and PDFs handed to the app from outside (Files/Mail share →
/// PennyWise). Previously Settings and Onboarding each carried their own copy
/// of the extraction + regex + facade sequence.
enum StatementImportService {

    /// Extracts text from the PDF at [url] and runs it through the shared
    /// import pipeline. Returns a user-facing result message.
    /// Handles security-scoped URLs (needed for files arriving via
    /// "Open in PennyWise"; harmless for the in-app picker's copies).
    static func importPDF(at url: URL) -> String {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        guard let pdfDocument = PDFDocument(url: url) else {
            return "Could not open PDF file"
        }

        var fullText = ""
        for i in 0..<pdfDocument.pageCount {
            if let page = pdfDocument.page(at: i), let pageText = page.string {
                fullText += pageText + "\n"
            }
        }

        guard !fullText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "No text found in PDF"
        }

        // PDFKit concatenates table columns without separators.
        // Split concatenated amounts: "395.0012,345.00" → "395.00\n12,345.00"
        // Also handles currency symbol junctions: "395.00₹12,345.00" → "395.00\n₹12,345.00"
        fullText = fullText
            .replacingOccurrences(of: #"(\.\d{2})(\d)"#, with: "$1\n$2", options: .regularExpression)
            .replacingOccurrences(of: #"(\.\d{2})([₹R])"#, with: "$1\n$2", options: .regularExpression)

        let snapshot = PennyWiseSharedFacade.companion.shared.importStatementTextAndLoadHome(statementText: fullText)
        if snapshot.lastImportParsed > 0 {
            return "\(snapshot.lastImportImported) transactions imported, \(snapshot.lastImportSkipped) skipped"
        } else if let error = snapshot.lastError {
            return error
        }
        return "No transactions found in this statement"
    }
}
