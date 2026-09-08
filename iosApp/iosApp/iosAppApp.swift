import SwiftUI

@main
struct iosAppApp: App {
    // Result of a PDF handed to the app from outside (Files/Mail/WhatsApp
    // "Open in PennyWise") — the quickest way to import a bank statement.
    @State private var externalImportResult: String?

    // Set by the pennywise://add deep link. Back Tap can't reach an app
    // directly, so the user points Settings > Accessibility > Touch > Back Tap
    // at a Shortcut that opens this URL.
    @State private var showQuickAdd = false

    var body: some Scene {
        WindowGroup {
            MainTabView(showQuickAdd: $showQuickAdd)
                .onOpenURL { url in
                    if url.scheme == "pennywise" {
                        showQuickAdd = (url.host == "add")
                        return
                    }
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
