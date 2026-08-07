import Foundation

class OnboardingViewModel: ObservableObject {
    @Published var currentStep = 0
    @Published var importResult: String?
    @Published var isImporting = false

    func importPDF(url: URL) {
        isImporting = true
        importResult = nil
        importResult = StatementImportService.importPDF(at: url)
        isImporting = false
    }

    func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
    }
}
