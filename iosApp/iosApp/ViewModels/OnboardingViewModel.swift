import Foundation
import PDFKit
import Shared

class OnboardingViewModel: ObservableObject {
    @Published var currentStep = 0
    @Published var importResult: String?
    @Published var isImporting = false

    private let facade = PennyWiseSharedFacade.companion.shared

    func importPDF(url: URL) {
        isImporting = true
        importResult = nil

        guard let pdfDocument = PDFDocument(url: url) else {
            importResult = "Could not open PDF file"
            isImporting = false
            return
        }

        var fullText = ""
        for i in 0..<pdfDocument.pageCount {
            if let page = pdfDocument.page(at: i), let pageText = page.string {
                fullText += pageText + "\n"
            }
        }

        guard !fullText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            importResult = "No text found in PDF"
            isImporting = false
            return
        }

        // PDFKit concatenates table columns without separators.
        // Split concatenated amounts: "395.0012,345.00" → "395.00\n12,345.00"
        // Also handles currency symbol junctions: "395.00₹12,345.00" → "395.00\n₹12,345.00"
        fullText = fullText
            .replacingOccurrences(of: #"(\.\d{2})(\d)"#, with: "$1\n$2", options: .regularExpression)
            .replacingOccurrences(of: #"(\.\d{2})([₹R])"#, with: "$1\n$2", options: .regularExpression)

        #if DEBUG
        print("[PDF Import] Extracted text length: \(fullText.count)")
        print("[PDF Import] First 1000 chars:\n\(String(fullText.prefix(1000)))")
        #endif

        let snapshot = facade.importStatementTextAndLoadHome(statementText: fullText)
        if snapshot.lastImportParsed > 0 {
            importResult = "\(snapshot.lastImportImported) transactions imported, \(snapshot.lastImportSkipped) skipped"
        } else if let error = snapshot.lastError {
            importResult = error
        } else {
            importResult = "No transactions found in this statement"
        }
        isImporting = false
    }

    func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
    }
}
