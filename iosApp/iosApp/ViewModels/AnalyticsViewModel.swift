import Shared
import SwiftUI

enum AnalyticsPeriod: String, CaseIterable {
    case thisMonth = "This Month"
    case lastMonth = "Last Month"
    case threeMonths = "3 Months"
    case sixMonths = "6 Months"
    case twelveMonths = "12 Months"
    case allTime = "All Time"

    /// Bar width for Spending Trends: daily bars across months are hairlines.
    var chartBucket: Calendar.Component {
        switch self {
        case .thisMonth, .lastMonth: return .day
        case .threeMonths: return .weekOfYear
        case .sixMonths, .twelveMonths, .allTime: return .month
        }
    }

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

/// What the charts below the summary total. There is no "everything" view:
/// adding salary or a transfer into a spending chart is meaningless.
enum AnalyticsTypeFilter: String, CaseIterable {
    case spending = "Spending"
    case expense = "Expense"
    case income = "Income"

    func includes(_ transactionType: String) -> Bool {
        switch self {
        // Expense + card spend, like Android's Spending view.
        case .spending: return MoneyTotals.shared.isSpendingType(transactionType: transactionType)
        case .expense: return transactionType == "EXPENSE"
        case .income: return transactionType == "INCOME"
        }
    }
}

struct CategoryBreakdownItem: Identifiable {
    let id = UUID()
    let name: String
    let totalMinor: Int64
    let count: Int
    let percentage: Double
    /// Distinct per slice so the donut never shows two near-identical greens.
    let color: Color
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

/// Totals are in the display currency; currencies with no stored rate are
/// carried as their own lines on each ConvertedTotal, never summed in.
struct AnalyticsSummaryData {
    /// EXPENSE transactions only — transfers/investments never count as spend.
    let totalSpending: ConvertedTotal
    /// INCOME transactions only.
    let income: ConvertedTotal
    let net: ConvertedTotal
    let transactionCount: Int
    let dailyAverageMinor: Int64
    let topCategoryName: String?
    let topCategoryIcon: String?
    /// Spending change vs the equal-length window immediately before this
    /// period (Android's "vs previous period" badge). Nil when there is no
    /// baseline (All Time, or an empty previous window).
    let spendingTrendPct: Int?
}

@MainActor
class AnalyticsViewModel: ObservableObject {
    private let facade = PennyWiseSharedFacade.companion.shared

    @Published var selectedPeriod: AnalyticsPeriod = .thisMonth
    @Published var selectedTypeFilter: AnalyticsTypeFilter = .spending
    @Published var isLoading = false

    @Published var summary: AnalyticsSummaryData?
    @Published var categoryBreakdown: [CategoryBreakdownItem] = []
    @Published var dailySpending: [DailySpendingItem] = []
    @Published var chartBucket: Calendar.Component = .day
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
        let filtered = all.filter { selectedTypeFilter.includes($0.transactionType) }
        periodTransactions = filtered

        let converter = facade.currencyConverter(baseCurrency: CurrencyManager.shared.displayCurrency)
        let previousSpendMinor = previousWindowSpend(range: range, converter: converter)
        computeAnalytics(
            converter: converter,
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
    private func previousWindowSpend(range: (start: Date, end: Date), converter: CurrencyConverter) -> Int64? {
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
        return converter.total(totals: MoneyTotals.shared.transactionTotals(transactions: previous)).amountMinor
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

    /// Distinct slice colours, by rank (category colours collide: three greens).
    /// One per pie slice; the rest share the grey of the pie's "Others" slice.
    private static let slicePalette: [Color] = [.blue, .orange, .green, .pink, .purple, .teal, .yellow]

    private func computeAnalytics(
        converter: CurrencyConverter,
        all: [SharedRecentTransactionItem],
        filtered transactions: [SharedRecentTransactionItem],
        startDate: Date,
        endDate: Date,
        previousSpendMinor: Int64?
    ) {
        // Summary is always honest regardless of the chip: spend = EXPENSE,
        // income = INCOME — transfers and investments are neither. Totals are
        // kept per currency, then converted with the stored rates.
        let spendTotals = MoneyTotals.shared.transactionTotals(transactions: all.filter { $0.transactionType == "EXPENSE" })
        let incomeTotals = MoneyTotals.shared.transactionTotals(transactions: all.filter { $0.transactionType == "INCOME" })
        let spend = converter.total(totals: spendTotals)
        let income = converter.total(totals: incomeTotals)
        let net = converter.total(totals: MoneyTotals.shared.netTotals(income: incomeTotals, expense: spendTotals))
        let count = transactions.count

        let calendar = Calendar.current
        let daysBetween = max(1, calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 1)
        let dailyAvg = spend.amountMinor > 0 ? spend.amountMinor / Int64(daysBetween) : 0

        let trendPct: Int? = previousSpendMinor.flatMap { prev in
            guard prev > 0 else { return nil }
            return Int((Double(spend.amountMinor - prev) / Double(prev) * 100.0).rounded())
        }

        // Charts plot one currency: each amount converted to the display
        // currency. Amounts with no stored rate are left out of the charts
        // (they still show as their own lines on the summary).
        let converted: [(txn: SharedRecentTransactionItem, minor: Int64)] = transactions.compactMap { txn in
            converter.convert(amountMinor: txn.amountMinor, currency: txn.currency).map { (txn, $0.int64Value) }
        }

        // Category breakdown (follows the chip selection)
        var categoryTotals: [String: (total: Int64, count: Int)] = [:]
        for (txn, minor) in converted {
            let cat = txn.category.isEmpty ? "Others" : txn.category
            let existing = categoryTotals[cat] ?? (total: 0, count: 0)
            categoryTotals[cat] = (total: existing.total + minor, count: existing.count + 1)
        }

        let filteredTotal = converted.reduce(Int64(0)) { $0 + $1.minor }
        let sortedCategories = categoryTotals.sorted { $0.value.total > $1.value.total }
        let totalForPercentage = max(filteredTotal, 1)
        categoryBreakdown = sortedCategories.enumerated().map { index, entry in
            CategoryBreakdownItem(
                name: entry.key,
                totalMinor: entry.value.total,
                count: entry.value.count,
                percentage: Double(entry.value.total) / Double(totalForPercentage) * 100.0,
                color: index < Self.slicePalette.count ? Self.slicePalette[index] : .gray
            )
        }

        let topCategory = sortedCategories.first
        summary = AnalyticsSummaryData(
            totalSpending: spend,
            income: income,
            net: net,
            transactionCount: count,
            dailyAverageMinor: dailyAvg,
            topCategoryName: topCategory?.key,
            topCategoryIcon: nil,
            spendingTrendPct: trendPct
        )

        // Spending trends: daily for a month, weekly for 3 months, monthly
        // beyond. Long ranges start at the first bucket with data, not at an
        // empty stretch months before it.
        let bucket = selectedPeriod.chartBucket
        chartBucket = bucket
        func bucketStart(_ date: Date) -> Date {
            calendar.dateInterval(of: bucket, for: date)?.start ?? calendar.startOfDay(for: date)
        }
        var bucketTotals: [Date: Int64] = [:]
        for (txn, minor) in converted {
            bucketTotals[bucketStart(Date(epochMillis: txn.occurredAtEpochMillis)), default: 0] += minor
        }

        let rangeStart = bucketStart(startDate)
        let effectiveStart = bucket == .day ? rangeStart : (bucketTotals.keys.min() ?? rangeStart)
        let effectiveEnd = bucketStart(min(endDate, Date()))
        var current = effectiveStart
        var dailyItems: [DailySpendingItem] = []
        while current <= effectiveEnd {
            dailyItems.append(DailySpendingItem(
                date: current,
                totalMinor: bucketTotals[current] ?? 0
            ))
            guard let next = calendar.date(byAdding: bucket, value: 1, to: current) else { break }
            current = next
        }
        dailySpending = dailyItems

        // Merchant ranking
        var merchantTotals: [String: (total: Int64, count: Int)] = [:]
        for (txn, minor) in converted {
            let merchant = txn.merchantName
            let existing = merchantTotals[merchant] ?? (total: 0, count: 0)
            merchantTotals[merchant] = (total: existing.total + minor, count: existing.count + 1)
        }

        merchantRanking = merchantTotals
            .sorted { $0.value.total > $1.value.total }
            .prefix(10)
            .map { MerchantRankingItem(name: $0.key, totalMinor: $0.value.total, count: $0.value.count) }
    }
}
