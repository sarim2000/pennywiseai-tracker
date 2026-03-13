import SwiftUI

struct CategoryPieChart: View {
    let categories: [CategoryBreakdownItem]
    @Environment(\.isAmoledActive) private var isAmoled

    private var topCategories: [CategoryBreakdownItem] {
        let top = Array(categories.prefix(7))
        let otherTotal = categories.dropFirst(7).reduce(Int64(0)) { $0 + $1.totalMinor }
        let otherCount = categories.dropFirst(7).reduce(0) { $0 + $1.count }
        if otherTotal > 0 {
            let totalAll = categories.reduce(Int64(0)) { $0 + $1.totalMinor }
            let pct = Double(otherTotal) / Double(max(totalAll, 1)) * 100.0
            return top + [CategoryBreakdownItem(
                name: "Others",
                totalMinor: otherTotal,
                count: otherCount,
                percentage: pct,
                color: "Others"
            )]
        }
        return top
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("Category Breakdown")
                .font(AppTypography.headline)

            if categories.isEmpty {
                emptyState
            } else {
                HStack(alignment: .top, spacing: AppSpacing.md) {
                    pieChart
                        .frame(width: 160, height: 160)

                    legend
                }
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.secondaryGroupedBackground(isAmoled: isAmoled))
        .clipShape(RoundedRectangle(cornerRadius: AppCornerRadius.medium))
    }

    private var emptyState: some View {
        Text("No category data available")
            .font(AppTypography.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 120)
            .multilineTextAlignment(.center)
    }

    private var pieChart: some View {
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2 - 4
            let innerRadius = radius * 0.55
            let total = topCategories.reduce(Int64(0)) { $0 + $1.totalMinor }
            guard total > 0 else { return }

            var startAngle = Angle.degrees(-90)

            for item in topCategories {
                let fraction = Double(item.totalMinor) / Double(total)
                let sweepAngle = Angle.degrees(fraction * 360)
                let endAngle = startAngle + sweepAngle

                var path = Path()
                path.addArc(center: center, radius: radius, startAngle: startAngle, endAngle: endAngle, clockwise: false)
                path.addArc(center: center, radius: innerRadius, startAngle: endAngle, endAngle: startAngle, clockwise: true)
                path.closeSubpath()

                context.fill(path, with: .color(AppColors.categoryColor(for: item.name)))
                startAngle = endAngle
            }
        }
    }

    private var legend: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            ForEach(topCategories) { item in
                HStack(spacing: AppSpacing.sm) {
                    Circle()
                        .fill(AppColors.categoryColor(for: item.name))
                        .frame(width: 10, height: 10)

                    VStack(alignment: .leading, spacing: 0) {
                        Text(item.name)
                            .font(AppTypography.caption)
                            .lineLimit(1)
                        Text("\(Int(item.percentage))%")
                            .font(AppTypography.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
}
