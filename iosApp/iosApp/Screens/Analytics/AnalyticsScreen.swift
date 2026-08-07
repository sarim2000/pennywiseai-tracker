import SwiftUI
import Shared

struct AnalyticsScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @StateObject private var viewModel = AnalyticsViewModel()
    @State private var drillDown: AnalyticsDrillDown?

    var body: some View {
        ScrollView {
            VStack(spacing: AppSpacing.md) {
                // Period filter chips
                periodFilterSection

                // Type filter chips
                typeFilterSection

                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 200)
                } else if viewModel.summary.transactionCount == 0 {
                    emptyState
                } else {
                    // Summary card
                    AnalyticsSummaryCard(summary: viewModel.summary)

                    // Spending trends bar chart
                    if !viewModel.dailySpending.isEmpty {
                        SpendingTrendsChart(data: viewModel.dailySpending)
                    }

                    // Category pie chart
                    if !viewModel.categoryBreakdown.isEmpty {
                        CategoryPieChart(categories: viewModel.categoryBreakdown)
                    }

                    // Category breakdown list — rows drill through to their
                    // transactions, like Android's breakdowns.
                    if !viewModel.categoryBreakdown.isEmpty {
                        CategoryBreakdownList(categories: viewModel.categoryBreakdown) { item in
                            drillDown = AnalyticsDrillDown(
                                title: item.name,
                                transactions: viewModel.transactions(inCategory: item.name)
                            )
                        }
                    }

                    // Top merchants
                    if !viewModel.merchantRanking.isEmpty {
                        TopMerchantsList(merchants: viewModel.merchantRanking) { item in
                            drillDown = AnalyticsDrillDown(
                                title: item.name,
                                transactions: viewModel.transactions(forMerchant: item.name)
                            )
                        }
                    }
                }
            }
            .padding(.horizontal, AppSpacing.md)
            .padding(.bottom, AppSpacing.xl)
        }
        .navigationTitle("Analytics")
        .onAppear {
            viewModel.loadAnalytics()
        }
        .sheet(item: $drillDown) { drill in
            AnalyticsDrillDownSheet(drill: drill)
        }
    }

    // MARK: - Period Filter

    private var periodFilterSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.sm) {
                ForEach(AnalyticsPeriod.allCases, id: \.self) { period in
                    periodChip(period)
                }
            }
            .padding(.horizontal, AppSpacing.xs)
        }
    }

    private func periodChip(_ period: AnalyticsPeriod) -> some View {
        let isSelected = viewModel.selectedPeriod == period
        return Text(period.rawValue)
            .font(AppTypography.caption)
            .fontWeight(isSelected ? .semibold : .regular)
            .padding(.horizontal, AppSpacing.sm + 4)
            .padding(.vertical, AppSpacing.sm)
            .background(isSelected ? Color.accentColor : Color(.tertiarySystemFill))
            .foregroundStyle(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .onTapGesture {
                viewModel.selectPeriod(period)
            }
    }

    // MARK: - Type Filter

    private var typeFilterSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.sm) {
                ForEach(AnalyticsTypeFilter.allCases, id: \.self) { filter in
                    typeChip(filter)
                }
            }
            .padding(.horizontal, AppSpacing.xs)
        }
    }

    private func typeChip(_ filter: AnalyticsTypeFilter) -> some View {
        let isSelected = viewModel.selectedTypeFilter == filter
        return Text(filter.rawValue)
            .font(AppTypography.caption)
            .fontWeight(isSelected ? .semibold : .regular)
            .padding(.horizontal, AppSpacing.sm + 4)
            .padding(.vertical, AppSpacing.sm)
            .background(isSelected ? Color.accentColor.opacity(0.8) : Color(.tertiarySystemFill))
            .foregroundStyle(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .onTapGesture {
                viewModel.selectTypeFilter(filter)
            }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: AppSpacing.md) {
            Image(systemName: "chart.bar.fill")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("No data yet")
                .font(AppTypography.title)
            Text("Your spending analytics will appear here once you have transactions.")
                .font(AppTypography.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 300)
        .padding(AppSpacing.lg)
    }
}

// MARK: - Drill-down (Android-parity: breakdown rows open their transactions)

struct AnalyticsDrillDown: Identifiable {
    let id = UUID()
    let title: String
    let transactions: [SharedRecentTransactionItem]
}

struct AnalyticsDrillDownSheet: View {
    let drill: AnalyticsDrillDown
    @State private var selectedDetail: SharedRecentTransactionItem?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(drill.transactions) { item in
                Button {
                    selectedDetail = item
                } label: {
                    HStack(spacing: AppSpacing.sm) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.merchantName)
                                .font(AppTypography.body)
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Text(Date(epochMillis: item.occurredAtEpochMillis), style: .date)
                                .font(AppTypography.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(AmountFormatter.format(minorUnits: item.amountMinor, currency: item.currency))
                            .font(AppTypography.amountSmall)
                            .foregroundStyle(item.transactionType == "INCOME" ? .green : .primary)
                    }
                }
                .buttonStyle(.plain)
            }
            .navigationTitle(drill.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $selectedDetail) { item in
                TransactionDetailSheet(item: item)
            }
        }
    }
}
