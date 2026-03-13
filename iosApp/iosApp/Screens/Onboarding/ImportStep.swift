import SwiftUI
import UniformTypeIdentifiers

struct ImportStep: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @ObservedObject private var themeManager = ThemeManager.shared
    @State private var showingPicker = false

    var body: some View {
        VStack(spacing: AppSpacing.xl) {
            Spacer()

            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 60))
                .foregroundColor(themeManager.accentColor)

            VStack(spacing: AppSpacing.sm) {
                Text("Import your bank statement")
                    .font(AppTypography.title)
                    .multilineTextAlignment(.center)

                Text("Get started quickly by importing a GPay or PhonePe PDF statement")
                    .font(AppTypography.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.lg)
            }

            if viewModel.isImporting {
                ProgressView("Importing...")
                    .padding()
            } else if let result = viewModel.importResult {
                HStack(spacing: AppSpacing.sm) {
                    Image(systemName: result.contains("imported") ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                        .foregroundColor(result.contains("imported") ? .green : .orange)
                    Text(result)
                        .font(AppTypography.body)
                        .foregroundColor(.secondary)
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: AppCornerRadius.medium)
                        .fill(Color(.secondarySystemBackground))
                )
                .padding(.horizontal, AppSpacing.lg)
            }

            Button {
                showingPicker = true
            } label: {
                Label("Import PDF", systemImage: "doc.badge.plus")
                    .font(AppTypography.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, AppSpacing.md)
                    .background(themeManager.accentColor)
                    .cornerRadius(AppCornerRadius.medium)
            }
            .padding(.horizontal, AppSpacing.xl)
            .disabled(viewModel.isImporting)

            Spacer()
            Spacer()
        }
        .padding(AppSpacing.lg)
        .sheet(isPresented: $showingPicker) {
            PDFDocumentPicker { url in
                viewModel.importPDF(url: url)
            }
        }
    }
}

struct PDFDocumentPicker: UIViewControllerRepresentable {
    let onPick: (URL) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick)
    }

    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: [UTType.pdf],
            asCopy: true
        )
        picker.delegate = context.coordinator
        picker.allowsMultipleSelection = false
        return picker
    }

    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}

    final class Coordinator: NSObject, UIDocumentPickerDelegate {
        let onPick: (URL) -> Void

        init(onPick: @escaping (URL) -> Void) {
            self.onPick = onPick
        }

        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            onPick(url)
        }
    }
}

struct ImportStep_Previews: PreviewProvider {
    static var previews: some View {
        ImportStep(viewModel: OnboardingViewModel())
    }
}
