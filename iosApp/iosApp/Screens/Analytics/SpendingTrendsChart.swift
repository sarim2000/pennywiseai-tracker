import SwiftUI
import Charts

struct SpendingTrendsChart: View {
    let data: [DailySpendingItem]
    /// .day, .weekOfYear or .month: one bar per bucket.
    var bucket: Calendar.Component = .day
    @Environment(\.isAmoledActive) private var isAmoled

    private var maxAmount: Int64 {
        data.map(\.totalMinor).max() ?? 0
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Spending Trends")
                .font(AppTypography.headline)

            if data.isEmpty || maxAmount == 0 {
                emptyState
            } else {
                chartView
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.surface(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    private var chartView: some View {
        Chart(data) { item in
            BarMark(
                x: .value("Date", item.date, unit: bucket),
                y: .value("Amount", Double(item.totalMinor) / 100.0)
            )
            .foregroundStyle(Color.accentColor.gradient)
            .cornerRadius(2)
        }
        .chartXAxis {
            AxisMarks(values: .stride(by: bucket, count: axisStride)) { _ in
                AxisGridLine()
                AxisValueLabel(format: axisDateFormat, centered: true)
            }
        }
        .chartYAxis {
            AxisMarks(position: .leading) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let doubleValue = value.as(Double.self) {
                        Text(formatCompact(doubleValue))
                            .font(.caption2)
                    }
                }
            }
        }
        .frame(height: 200)
    }

    private var emptyState: some View {
        Text("No spending data for this period")
            .font(AppTypography.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 120)
            .multilineTextAlignment(.center)
    }

    /// Aim for at most ~7 labels whatever the bucket.
    private var axisStride: Int {
        max(1, Int((Double(data.count) / 7).rounded(.up)))
    }

    private var axisDateFormat: Date.FormatStyle {
        switch bucket {
        case .day: return .dateTime.day()
        case .month: return data.count > 12 ? .dateTime.month(.abbreviated).year(.twoDigits) : .dateTime.month(.abbreviated)
        default: return .dateTime.month(.abbreviated).day()
        }
    }

    /// One unit for the whole axis, picked from the tallest bar, with up to one
    /// decimal: the 1.5L gridline reads "1.5L" (not a rounded "2L") and the
    /// 0.5L one reads "0.5L" (not "50K") next to it.
    private func formatCompact(_ value: Double) -> String {
        let top = Double(maxAmount) / 100.0
        let units: [(size: Double, suffix: String)] = CurrencyManager.shared.displayCurrency == "INR"
            ? [(1e7, "Cr"), (1e5, "L"), (1e3, "K")]
            : [(1e9, "B"), (1e6, "M"), (1e3, "K")]
        guard let unit = units.first(where: { top >= $0.size }) else {
            return value.formatted(.number.precision(.fractionLength(0)))
        }
        return (value / unit.size).formatted(.number.precision(.fractionLength(0...1))) + unit.suffix
    }
}
