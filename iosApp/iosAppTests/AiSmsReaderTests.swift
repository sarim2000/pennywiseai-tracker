import Shared
import XCTest
@testable import iosApp

/// The on-device model on bank texts no parser knows. Needs Apple Intelligence:
/// skipped where the model is unavailable (most simulators, older phones).
/// Bank names and every number are made up.
final class AiSmsReaderTests: XCTestCase {
    private let samples: [(sms: String, amount: Double, currency: String, type: String)] = [
        ("INR 318.00 spent on your Northwind SFB Credit Card XX1234 at SWIGGY on 22-10-26. Avl limit: INR 45,682.00",
         318, "INR", "credit"),
        ("Your A/c XX5678 is debited by Rs.1,250.50 on 21-10-26 towards UPI/BIGBASKET. Avl Bal Rs.10,000.00 -Contoso Bank",
         1250.50, "INR", "expense"),
        ("Rs.50,000.00 credited to your A/c XX9012 on 01-10-26 by NEFT from ACME CORP. -Fabrikam Co-op Bank",
         50000, "INR", "income"),
        ("AED 75.25 was spent on your Tailspin Bank debit card ending 3456 at CARREFOUR on 20/10/2026.",
         75.25, "AED", "expense"),
        ("Rs 2,000.00 transferred from your A/c XX7890 to your own A/c XX1111 on 19-10-26. -Woodgrove Bank",
         2000, "INR", "transfer"),
    ]

    func testReadsAmountAndTypeOfUnknownBankSms() async throws {
        try XCTSkipUnless(OnDeviceAI.isAvailable, "Foundation Models unavailable here (needs Apple Intelligence on a supported device)")
        for sample in samples {
            let reading: AiSmsReading
            do {
                reading = try await AiSmsReader.read(sample.sms)
            } catch {
                #if targetEnvironment(simulator)
                // The simulator reports the model available from the Mac's eligibility, but
                // generation fails unless the Mac itself has Apple Intelligence turned on.
                throw XCTSkip("Foundation Models can't generate on this simulator: \(error)")
                #else
                throw error
                #endif
            }
            let amount = Double(reading.amount.filter { $0.isNumber || $0 == "." }.trimmingCharacters(in: CharacterSet(charactersIn: ".")))
            XCTAssertTrue(reading.isBankTransaction, sample.sms)
            XCTAssertEqual(amount, sample.amount, sample.sms)
            XCTAssertEqual(reading.currency.uppercased(), sample.currency, sample.sms)
            XCTAssertEqual(reading.type, sample.type, sample.sms)
        }
    }

    /// The intent's path: saved and marked when the model can read it, queued for the next app open when it can't.
    func testIntentPathReadsOrQueuesAnUnknownBankSms() async throws {
        try XCTSkipUnless(OnDeviceAI.isAvailable, "Foundation Models unavailable here")
        try? FileManager.default.removeItem(at: PendingAiSms.fileURL)
        defer { try? FileManager.default.removeItem(at: PendingAiSms.fileURL) }

        let outcome = await BankSmsImport.run(sender: nil, body: samples[0].sms)
        if let id = outcome.savedTransactionId {
            XCTAssertTrue(outcome.message.hasPrefix("Read by AI: ₹318"), outcome.message)
            _ = PennyWiseSharedFacade.companion.shared.deleteTransaction(transactionId: id)
        } else if outcome.message != "Already saved" { // a soft-deleted earlier run still dedupes
            XCTAssertEqual(outcome.message, "PennyWise AI will read this message when you next open the app")
            XCTAssertEqual(PendingAiSms.count, 1)
            let excluded = try PendingAiSms.fileURL.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup
            XCTAssertEqual(excluded, true)
        }
    }
}
