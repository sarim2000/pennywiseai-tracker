import Foundation

extension Date {
    init(epochMillis: Int64) {
        self.init(timeIntervalSince1970: TimeInterval(epochMillis) / 1000.0)
    }

    var epochMillis: Int64 {
        Int64((timeIntervalSince1970 * 1000.0).rounded())
    }

    func formatted(as format: String = "dd MMM yyyy, hh:mm a") -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = format
        return formatter.string(from: self)
    }
}
