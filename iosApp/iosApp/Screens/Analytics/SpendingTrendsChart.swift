import SwiftUI
import Charts

struct SpendingTrendsChart: View {
    let data: [DailySpendingItem]
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
        .background(AppColors.secondaryGroupedBackground(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    private var chartView: some View {
        Chart(data) { item in
            BarMark(
                x: .value("Date", item.date, unit: .day),
                y: .value("Amount", Double(item.totalMinor) / 100.0)
            )
            .foregroundStyle(Color.accentColor.gradient)
            .cornerRadius(2)
        }
        .chartXAxis {
            AxisMarks(values: .stride(by: .day, count: axisStride)) { _ in
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

    private var axisStride: Int {
        let count = data.count
        if count <= 7 { return 1 }
        if count <= 14 { return 2 }
        if count <= 31 { return 5 }
        if count <= 90 { return 10 }
        return 30
    }

    private var axisDateFormat: Date.FormatStyle {
        let count = data.count
        if count <= 31 {
            return .dateTime.day()
        }
        return .dateTime.month(.abbreviated).day()
    }

    private func formatCompact(_ value: Double) -> String {
        if value >= 10_000_000 {
            return String(format: "%.0fM", value / 1_000_000)
        } else if value >= 100_000 {
            return String(format: "%.0fL", value / 100_000)
        } else if value >= 1_000 {
            return String(format: "%.0fK", value / 1_000)
        }
        return String(format: "%.0f", value)
    }
}
