import Foundation
import PDFKit
import Shared

class OnboardingViewModel: ObservableObject {
    @Published var currentStep = 0
    @Published var importResult: String?
    @Published var isImporting = false

    private let facade = PennyWiseSharedFacade()

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
