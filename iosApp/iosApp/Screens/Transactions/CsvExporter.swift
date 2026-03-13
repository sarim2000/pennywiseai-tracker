import Foundation
import Shared
import SwiftUI

struct CsvExporter {

    static func generateCSV(from transactions: [SharedRecentTransactionItem]) -> URL? {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm:ss"

        let fileFormatter = DateFormatter()
        fileFormatter.dateFormat = "yyyyMMdd_HHmmss"

        let header = ["Date", "Time", "Merchant", "Category", "Type", "Amount", "Currency", "Bank", "Account", "Description"]

        var lines: [String] = [header.map { escapeCSV($0) }.joined(separator: ",")]

        for tx in transactions {
            let date = Date(epochMillis: tx.occurredAtEpochMillis)
            let amountDecimal = Decimal(tx.amountMinor) / 100

            let row: [String] = [
                dateFormatter.string(from: date),
                timeFormatter.string(from: date),
                tx.merchantName,
                tx.category,
                displayType(tx.transactionType),
                "\(amountDecimal)",
                tx.currency,
                tx.bankName ?? "",
                tx.accountLast4 ?? "",
                tx.note ?? ""
            ]

            lines.append(row.map { escapeCSV($0) }.joined(separator: ","))
        }

        let csvContent = lines.joined(separator: "\n")
        let fileName = "transactions_\(fileFormatter.string(from: Date())).csv"
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)

        do {
            try csvContent.write(to: tempURL, atomically: true, encoding: .utf8)
            return tempURL
        } catch {
            return nil
        }
    }

    private static func escapeCSV(_ field: String) -> String {
        if field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r") {
            let escaped = field.replacingOccurrences(of: "\"", with: "\"\"")
            return "\"\(escaped)\""
        }
        return field
    }

    private static func displayType(_ type: String) -> String {
        switch type {
        case "INCOME": return "Income"
        case "EXPENSE": return "Expense"
        case "CREDIT": return "Credit"
        case "TRANSFER": return "Transfer"
        case "INVESTMENT": return "Investment"
        default: return type
        }
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
