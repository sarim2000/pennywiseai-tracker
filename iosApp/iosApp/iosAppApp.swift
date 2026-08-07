import SwiftUI

@main
struct iosAppApp: App {
    // Result of a PDF handed to the app from outside (Files/Mail/WhatsApp
    // "Open in PennyWise") — the quickest way to import a bank statement.
    @State private var externalImportResult: String?

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .onOpenURL { url in
                    guard url.isFileURL, url.pathExtension.lowercased() == "pdf" else { return }
                    externalImportResult = StatementImportService.importPDF(at: url)
                }
                .alert(
                    "Statement Import",
                    isPresented: Binding(
                        get: { externalImportResult != nil },
                        set: { if !$0 { externalImportResult = nil } }
                    )
                ) {
                    Button("OK", role: .cancel) { externalImportResult = nil }
                } message: {
                    Text(externalImportResult ?? "")
                }
        }
    }
}
