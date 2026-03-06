import Foundation
import Shared

enum AnalyticsPeriod: String, CaseIterable {
    case thisMonth = "This Month"
    case lastMonth = "Last Month"
    case threeMonths = "3 Months"
    case sixMonths = "6 Months"
    case twelveMonths = "12 Months"
    case allTime = "All Time"

    var dateRange: (start: Date, end: Date) {
        let calendar = Calendar.current
        let now = Date()
        let endOfToday = calendar.startOfDay(for: now).addingTimeInterval(86399)

        switch self {
        case .thisMonth:
            let start = calendar.date(from: calendar.dateComponents([.year, .month], from: now))!
            return (start, endOfToday)
        case .lastMonth:
            let startOfThisMonth = calendar.date(from: calendar.dateComponents([.year, .month], from: now))!
            let start = calendar.date(byAdding: .month, value: -1, to: startOfThisMonth)!
            let end = calendar.date(byAdding: .second, value: -1, to: startOfThisMonth)!
            return (start, end)
        case .threeMonths:
            let start = calendar.date(byAdding: .month, value: -3, to: now)!
            return (start, endOfToday)
        case .sixMonths:
            let start = calendar.date(byAdding: .month, value: -6, to: now)!
            return (start, endOfToday)
        case .twelveMonths:
            let start = calendar.date(byAdding: .month, value: -12, to: now)!
            return (start, endOfToday)
        case .allTime:
            let start = Date(timeIntervalSince1970: 0)
            return (start, endOfToday)
        }
    }
}

enum AnalyticsTypeFilter: String, CaseIterable {
    case all = "All"
    case debit = "Debit"
    case credit = "Credit"

    var transactionTypeFilter: String? {
        switch self {
        case .all: return nil
        case .debit: return "EXPENSE"
        case .credit: return "INCOME"
        }
    }
}

struct CategoryBreakdownItem: Identifiable {
    let id = UUID()
    let name: String
    let totalMinor: Int64
    let count: Int
    let percentage: Double
    let color: String
}

struct DailySpendingItem: Identifiable {
    let id = UUID()
    let date: Date
    let totalMinor: Int64
}

struct MerchantRankingItem: Identifiable {
    let id = UUID()
    let name: String
    let totalMinor: Int64
    let count: Int
}

struct AnalyticsSummaryData {
    let totalSpendingMinor: Int64
    let transactionCount: Int
    let dailyAverageMinor: Int64
    let topCategoryName: String?
    let topCategoryIcon: String?
}

class AnalyticsViewModel: ObservableObject {
    private let facade = PennyWiseSharedFacade()

    @Published var selectedPeriod: AnalyticsPeriod = .thisMonth
    @Published var selectedTypeFilter: AnalyticsTypeFilter = .all
    @Published var isLoading = false

    @Published var summary = AnalyticsSummaryData(
        totalSpendingMinor: 0, transactionCount: 0,
        dailyAverageMinor: 0, topCategoryName: nil, topCategoryIcon: nil
    )
    @Published var categoryBreakdown: [CategoryBreakdownItem] = []
    @Published var dailySpending: [DailySpendingItem] = []
    @Published var merchantRanking: [MerchantRankingItem] = []

    func loadAnalytics() {
        isLoading = true
        let range = selectedPeriod.dateRange
        let startMs = range.start.epochMillis
        let endMs = range.end.epochMillis

        let transactions = facade.getTransactionsForPeriod(
            startDateMs: startMs,
            endDateMs: endMs,
            type: selectedTypeFilter.transactionTypeFilter
        )

        computeAnalytics(transactions: transactions, startDate: range.start, endDate: range.end)
        isLoading = false
    }

    func selectPeriod(_ period: AnalyticsPeriod) {
        selectedPeriod = period
        loadAnalytics()
    }

    func selectTypeFilter(_ filter: AnalyticsTypeFilter) {
        selectedTypeFilter = filter
        loadAnalytics()
    }

    private func computeAnalytics(
        transactions: [SharedRecentTransactionItem],
        startDate: Date,
        endDate: Date
    ) {
        let totalMinor = transactions.reduce(Int64(0)) { $0 + $1.amountMinor }
        let count = transactions.count

        let calendar = Calendar.current
        let daysBetween = max(1, calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 1)
        let dailyAvg = count > 0 ? totalMinor / Int64(daysBetween) : 0

        // Category breakdown
        var categoryTotals: [String: (total: Int64, count: Int)] = [:]
        for txn in transactions {
            let cat = txn.category.isEmpty ? "Others" : txn.category
            let existing = categoryTotals[cat] ?? (total: 0, count: 0)
            categoryTotals[cat] = (total: existing.total + txn.amountMinor, count: existing.count + 1)
        }

        let sortedCategories = categoryTotals.sorted { $0.value.total > $1.value.total }
        let totalForPercentage = max(totalMinor, 1)
        categoryBreakdown = sortedCategories.map { entry in
            CategoryBreakdownItem(
                name: entry.key,
                totalMinor: entry.value.total,
                count: entry.value.count,
                percentage: Double(entry.value.total) / Double(totalForPercentage) * 100.0,
                color: entry.key
            )
        }

        let topCategory = sortedCategories.first
        summary = AnalyticsSummaryData(
            totalSpendingMinor: totalMinor,
            transactionCount: count,
            dailyAverageMinor: dailyAvg,
            topCategoryName: topCategory?.key,
            topCategoryIcon: nil
        )

        // Daily spending
        var dailyTotals: [Date: Int64] = [:]
        for txn in transactions {
            let txnDate = Date(epochMillis: txn.occurredAtEpochMillis)
            let dayStart = calendar.startOfDay(for: txnDate)
            dailyTotals[dayStart, default: 0] += txn.amountMinor
        }

        let effectiveStart = calendar.startOfDay(for: startDate)
        let effectiveEnd = calendar.startOfDay(for: min(endDate, Date()))
        var current = effectiveStart
        var dailyItems: [DailySpendingItem] = []
        while current <= effectiveEnd {
            dailyItems.append(DailySpendingItem(
                date: current,
                totalMinor: dailyTotals[current] ?? 0
            ))
            guard let next = calendar.date(byAdding: .day, value: 1, to: current) else { break }
            current = next
        }
        dailySpending = dailyItems

        // Merchant ranking
        var merchantTotals: [String: (total: Int64, count: Int)] = [:]
        for txn in transactions {
            let merchant = txn.merchantName
            let existing = merchantTotals[merchant] ?? (total: 0, count: 0)
            merchantTotals[merchant] = (total: existing.total + txn.amountMinor, count: existing.count + 1)
        }

        merchantRanking = merchantTotals
            .sorted { $0.value.total > $1.value.total }
            .prefix(10)
            .map { MerchantRankingItem(name: $0.key, totalMinor: $0.value.total, count: $0.value.count) }
    }
}
