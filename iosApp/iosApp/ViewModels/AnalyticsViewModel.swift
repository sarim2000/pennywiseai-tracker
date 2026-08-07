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
    /// EXPENSE transactions only — transfers/investments never count as spend.
    let totalSpendingMinor: Int64
    /// INCOME transactions only.
    let incomeMinor: Int64
    let netMinor: Int64
    let transactionCount: Int
    let dailyAverageMinor: Int64
    let topCategoryName: String?
    let topCategoryIcon: String?
    /// Spending change vs the equal-length window immediately before this
    /// period (Android's "vs previous period" badge). Nil when there is no
    /// baseline (All Time, or an empty previous window).
    let spendingTrendPct: Int?
}

class AnalyticsViewModel: ObservableObject {
    private let facade = PennyWiseSharedFacade.companion.shared

    @Published var selectedPeriod: AnalyticsPeriod = .thisMonth
    @Published var selectedTypeFilter: AnalyticsTypeFilter = .all
    @Published var isLoading = false

    @Published var summary = AnalyticsSummaryData(
        totalSpendingMinor: 0, incomeMinor: 0, netMinor: 0, transactionCount: 0,
        dailyAverageMinor: 0, topCategoryName: nil, topCategoryIcon: nil,
        spendingTrendPct: nil
    )
    @Published var categoryBreakdown: [CategoryBreakdownItem] = []
    @Published var dailySpending: [DailySpendingItem] = []
    @Published var merchantRanking: [MerchantRankingItem] = []
    /// The period's transactions after the type-filter chip — kept so category
    /// and merchant rows can drill through to their underlying transactions.
    @Published var periodTransactions: [SharedRecentTransactionItem] = []

    func loadAnalytics() {
        isLoading = true
        let range = selectedPeriod.dateRange
        let startMs = range.start.epochMillis
        let endMs = range.end.epochMillis

        // One unfiltered fetch: the summary always needs both sides
        // (income vs spend), the lists then apply the chip in memory.
        let all = facade.getTransactionsForPeriod(
            startDateMs: startMs, endDateMs: endMs, type: nil
        )
        let filtered: [SharedRecentTransactionItem]
        if let typeFilter = selectedTypeFilter.transactionTypeFilter {
            filtered = all.filter { $0.transactionType == typeFilter }
        } else {
            filtered = all
        }
        periodTransactions = filtered

        let previousSpendMinor = previousWindowSpend(range: range)
        computeAnalytics(
            all: all,
            filtered: filtered,
            startDate: range.start,
            endDate: range.end,
            previousSpendMinor: previousSpendMinor
        )
        isLoading = false
    }

    /// EXPENSE total of the equal-length window immediately before [range];
    /// nil when the period has no meaningful baseline.
    private func previousWindowSpend(range: (start: Date, end: Date)) -> Int64? {
        guard selectedPeriod != .allTime else { return nil }
        let length = range.end.timeIntervalSince(range.start)
        let prevStart = range.start.addingTimeInterval(-length)
        let prevEnd = range.start.addingTimeInterval(-1)
        let previous = facade.getTransactionsForPeriod(
            startDateMs: prevStart.epochMillis,
            endDateMs: prevEnd.epochMillis,
            type: "EXPENSE"
        )
        guard !previous.isEmpty else { return nil }
        return previous.reduce(Int64(0)) { $0 + $1.amountMinor }
    }

    func transactions(inCategory category: String) -> [SharedRecentTransactionItem] {
        periodTransactions.filter { ($0.category.isEmpty ? "Others" : $0.category) == category }
    }

    func transactions(forMerchant merchant: String) -> [SharedRecentTransactionItem] {
        periodTransactions.filter { $0.merchantName == merchant }
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
        all: [SharedRecentTransactionItem],
        filtered transactions: [SharedRecentTransactionItem],
        startDate: Date,
        endDate: Date,
        previousSpendMinor: Int64?
    ) {
        // Summary is always honest regardless of the chip: spend = EXPENSE,
        // income = INCOME — transfers and investments are neither.
        let spendMinor = all.filter { $0.transactionType == "EXPENSE" }
            .reduce(Int64(0)) { $0 + $1.amountMinor }
        let incomeMinor = all.filter { $0.transactionType == "INCOME" }
            .reduce(Int64(0)) { $0 + $1.amountMinor }
        let count = transactions.count

        let calendar = Calendar.current
        let daysBetween = max(1, calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 1)
        let dailyAvg = spendMinor > 0 ? spendMinor / Int64(daysBetween) : 0

        let trendPct: Int? = previousSpendMinor.flatMap { prev in
            guard prev > 0 else { return nil }
            return Int((Double(spendMinor - prev) / Double(prev) * 100.0).rounded())
        }

        // Category breakdown (follows the chip selection)
        var categoryTotals: [String: (total: Int64, count: Int)] = [:]
        for txn in transactions {
            let cat = txn.category.isEmpty ? "Others" : txn.category
            let existing = categoryTotals[cat] ?? (total: 0, count: 0)
            categoryTotals[cat] = (total: existing.total + txn.amountMinor, count: existing.count + 1)
        }

        let filteredTotal = transactions.reduce(Int64(0)) { $0 + $1.amountMinor }
        let sortedCategories = categoryTotals.sorted { $0.value.total > $1.value.total }
        let totalForPercentage = max(filteredTotal, 1)
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
            totalSpendingMinor: spendMinor,
            incomeMinor: incomeMinor,
            netMinor: incomeMinor - spendMinor,
            transactionCount: count,
            dailyAverageMinor: dailyAvg,
            topCategoryName: topCategory?.key,
            topCategoryIcon: nil,
            spendingTrendPct: trendPct
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
