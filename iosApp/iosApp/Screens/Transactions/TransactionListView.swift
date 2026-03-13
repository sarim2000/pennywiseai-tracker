import Shared
import SwiftUI

// MARK: - Enums

enum TransactionSortOrder: String, CaseIterable {
    case newestFirst = "Newest"
    case oldestFirst = "Oldest"
    case amountHigh = "Amount (High)"
    case amountLow = "Amount (Low)"
}

enum TransactionTypeFilter: String, CaseIterable {
    case all = "All"
    case income = "INCOME"
    case expense = "EXPENSE"
    case credit = "CREDIT"
    case transfer = "TRANSFER"
    case investment = "INVESTMENT"

    var displayName: String {
        switch self {
        case .all: return "All Types"
        case .income: return "Income"
        case .expense: return "Expense"
        case .credit: return "Credit"
        case .transfer: return "Transfer"
        case .investment: return "Investment"
        }
    }

    var icon: String {
        switch self {
        case .all: return "line.3.horizontal.decrease.circle"
        case .income: return "arrow.down.circle.fill"
        case .expense: return "arrow.up.circle.fill"
        case .credit: return "creditcard.fill"
        case .transfer: return "arrow.left.arrow.right.circle.fill"
        case .investment: return "chart.line.uptrend.xyaxis.circle.fill"
        }
    }
}

enum DatePeriodFilter: String, CaseIterable {
    case thisMonth = "This Month"
    case lastMonth = "Last Month"
    case currentFY = "Current FY"
    case allTime = "All Time"
    case custom = "Custom"

    var icon: String {
        switch self {
        case .thisMonth: return "calendar"
        case .lastMonth: return "calendar.badge.clock"
        case .currentFY: return "calendar.badge.checkmark"
        case .allTime: return "infinity"
        case .custom: return "calendar.badge.plus"
        }
    }

    func dateRange() -> (start: Date, end: Date)? {
        let calendar = Calendar.current
        let today = Date()

        switch self {
        case .thisMonth:
            guard let start = calendar.date(from: calendar.dateComponents([.year, .month], from: today)) else { return nil }
            return (start, today)
        case .lastMonth:
            guard let thisMonthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: today)),
                  let lastMonthStart = calendar.date(byAdding: .month, value: -1, to: thisMonthStart),
                  let lastMonthEnd = calendar.date(byAdding: .day, value: -1, to: thisMonthStart) else { return nil }
            return (lastMonthStart, lastMonthEnd)
        case .currentFY:
            let components = calendar.dateComponents([.year, .month], from: today)
            let year = components.year ?? 2026
            let month = components.month ?? 1
            // Indian Financial Year: April 1 to March 31
            let fyYear = month >= 4 ? year : year - 1
            var fyStartComponents = DateComponents()
            fyStartComponents.year = fyYear
            fyStartComponents.month = 4
            fyStartComponents.day = 1
            guard let fyStart = calendar.date(from: fyStartComponents) else { return nil }
            return (fyStart, today)
        case .allTime:
            return nil
        case .custom:
            return nil
        }
    }
}

// MARK: - TransactionListView

struct TransactionListView: View {
    private let facade: PennyWiseSharedFacade

    @State private var transactions: [SharedRecentTransactionItem] = []
    @State private var searchText = ""
    @State private var selectedDatePeriod: DatePeriodFilter = .allTime
    @State private var selectedTypeFilter: TransactionTypeFilter = .all
    @State private var selectedCategory: String? = nil
    @State private var sortOrder: TransactionSortOrder = .newestFirst
    @State private var categories: [String] = []
    @State private var showDeleteConfirmation = false
    @State private var transactionToDelete: SharedRecentTransactionItem? = nil
    @State private var editingTransactionId: Int64? = nil
    @State private var showCustomDateSheet = false
    @State private var customDateFrom: Date = Calendar.current.date(byAdding: .month, value: -1, to: Date()) ?? Date()
    @State private var customDateTo: Date = Date()
    @State private var isLoading = true
    @State private var csvFileURL: URL? = nil
    @State private var showShareSheet = false

    init(facade: PennyWiseSharedFacade) {
        self.facade = facade
    }

    private var hasActiveFilters: Bool {
        selectedDatePeriod != .allTime || selectedTypeFilter != .all || selectedCategory != nil
    }

    private func clearAllFilters() {
        selectedDatePeriod = .allTime
        selectedTypeFilter = .all
        selectedCategory = nil
    }

    private var filteredTransactions: [SharedRecentTransactionItem] {
        let calendar = Calendar.current
        var result = transactions

        // Date period filter
        if selectedDatePeriod == .custom {
            let startEpoch = Int64(calendar.startOfDay(for: customDateFrom).timeIntervalSince1970 * 1000)
            let endEpoch = Int64(calendar.startOfDay(for: customDateTo).addingTimeInterval(86399).timeIntervalSince1970 * 1000)
            result = result.filter { $0.occurredAtEpochMillis >= startEpoch && $0.occurredAtEpochMillis <= endEpoch }
        } else if let range = selectedDatePeriod.dateRange() {
            let startEpoch = Int64(calendar.startOfDay(for: range.start).timeIntervalSince1970 * 1000)
            let endEpoch = Int64(calendar.startOfDay(for: range.end).addingTimeInterval(86399).timeIntervalSince1970 * 1000)
            result = result.filter { $0.occurredAtEpochMillis >= startEpoch && $0.occurredAtEpochMillis <= endEpoch }
        }

        if !searchText.isEmpty {
            let query = searchText.lowercased()
            result = result.filter {
                $0.merchantName.lowercased().contains(query) ||
                    ($0.note?.lowercased().contains(query) ?? false) ||
                    $0.category.lowercased().contains(query)
            }
        }

        if selectedTypeFilter != .all {
            result = result.filter { $0.transactionType == selectedTypeFilter.rawValue }
        }

        if let category = selectedCategory {
            result = result.filter { $0.category == category }
        }

        switch sortOrder {
        case .newestFirst:
            result.sort { $0.occurredAtEpochMillis > $1.occurredAtEpochMillis }
        case .oldestFirst:
            result.sort { $0.occurredAtEpochMillis < $1.occurredAtEpochMillis }
        case .amountHigh:
            result.sort { $0.amountMinor > $1.amountMinor }
        case .amountLow:
            result.sort { $0.amountMinor < $1.amountMinor }
        }

        return result
    }

    private var groupedByMonth: [(key: String, items: [SharedRecentTransactionItem])] {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"

        var groups: [String: [SharedRecentTransactionItem]] = [:]
        var order: [String] = []

        for item in filteredTransactions {
            let date = Date(timeIntervalSince1970: Double(item.occurredAtEpochMillis) / 1000.0)
            let monthKey = formatter.string(from: date)
            if groups[monthKey] == nil {
                groups[monthKey] = []
                order.append(monthKey)
            }
            groups[monthKey]?.append(item)
        }

        // Sort items within each group by date (most recent first)
        for key in order {
            groups[key]?.sort { $0.occurredAtEpochMillis > $1.occurredAtEpochMillis }
        }

        return order.map { (key: $0, items: groups[$0] ?? []) }
    }

    private var totals: (income: Int64, expense: Int64, net: Int64) {
        var income: Int64 = 0
        var expense: Int64 = 0
        for item in filteredTransactions {
            if item.transactionType == "INCOME" {
                income += item.amountMinor
            } else {
                expense += item.amountMinor
            }
        }
        return (income, expense, income - expense)
    }

    var body: some View {
        VStack(spacing: 0) {
            filterBar
            if !filteredTransactions.isEmpty {
                TransactionTotalsCard(
                    incomeMinor: totals.income,
                    expenseMinor: totals.expense,
                    netMinor: totals.net,
                    currency: CurrencyManager.shared.displayCurrency
                )
                .padding(.horizontal, AppSpacing.md)
                .padding(.bottom, AppSpacing.sm)
            }
            transactionList
        }
        .searchable(text: $searchText, prompt: "Search transactions")
        .navigationTitle("Transactions")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(destination: AddEditTransactionView(facade: facade, onSave: reloadTransactions)) {
                    Image(systemName: "plus")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                sortMenu
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    if let url = CsvExporter.generateCSV(from: filteredTransactions) {
                        csvFileURL = url
                        showShareSheet = true
                    }
                } label: {
                    Image(systemName: "square.and.arrow.up")
                }
                .disabled(filteredTransactions.isEmpty)
            }
        }
        .onAppear { reloadTransactions() }
        .navigationDestination(isPresented: Binding(
            get: { editingTransactionId != nil },
            set: { if !$0 { editingTransactionId = nil } }
        )) {
            if let editId = editingTransactionId {
                AddEditTransactionView(
                    facade: facade,
                    editingTransactionId: editId,
                    onSave: reloadTransactions
                )
            }
        }
        .alert("Delete Transaction", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {
                transactionToDelete = nil
            }
            Button("Delete", role: .destructive) {
                if let item = transactionToDelete {
                    let generator = UINotificationFeedbackGenerator()
                    generator.notificationOccurred(.success)
                    _ = facade.deleteTransaction(transactionId: item.transactionId)
                    reloadTransactions()
                    transactionToDelete = nil
                }
            }
        } message: {
            if let item = transactionToDelete {
                Text("Are you sure you want to delete the transaction \"\(item.merchantName)\"?")
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let url = csvFileURL {
                ShareSheet(activityItems: [url])
            }
        }
        .sheet(isPresented: $showCustomDateSheet) {
            customDateRangeSheet
        }
    }

    // MARK: - Filter Bar

    @ViewBuilder
    private var filterBar: some View {
        VStack(spacing: AppSpacing.sm) {
            // Date period filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AppSpacing.sm) {
                    // Clear All chip
                    if hasActiveFilters {
                        FilterChipView(
                            label: "Clear All",
                            icon: "xmark.circle",
                            isSelected: false
                        ) {
                            let generator = UIImpactFeedbackGenerator(style: .medium)
                            generator.impactOccurred()
                            clearAllFilters()
                        }
                    }

                    ForEach(DatePeriodFilter.allCases, id: \.self) { period in
                        FilterChipView(
                            label: period == .custom && selectedDatePeriod == .custom
                                ? customDateLabel
                                : period.rawValue,
                            icon: period.icon,
                            isSelected: selectedDatePeriod == period
                        ) {
                            let generator = UIImpactFeedbackGenerator(style: .light)
                            generator.impactOccurred()
                            if period == .custom {
                                showCustomDateSheet = true
                            } else {
                                selectedDatePeriod = period
                            }
                        }
                    }
                }
                .padding(.horizontal, AppSpacing.md)
            }

            // Type filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: AppSpacing.sm) {
                    ForEach(TransactionTypeFilter.allCases, id: \.self) { filter in
                        FilterChipView(
                            label: filter.displayName,
                            icon: filter == .all ? nil : filter.icon,
                            isSelected: selectedTypeFilter == filter
                        ) {
                            let generator = UIImpactFeedbackGenerator(style: .light)
                            generator.impactOccurred()
                            selectedTypeFilter = filter
                        }
                    }
                }
                .padding(.horizontal, AppSpacing.md)
            }

            // Category filter chips
            if !categories.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: AppSpacing.sm) {
                        FilterChipView(
                            label: "All Categories",
                            isSelected: selectedCategory == nil
                        ) {
                            let generator = UIImpactFeedbackGenerator(style: .light)
                            generator.impactOccurred()
                            selectedCategory = nil
                        }

                        ForEach(categories, id: \.self) { category in
                            FilterChipView(
                                label: category,
                                dotColor: AppColors.categoryColor(for: category),
                                isSelected: selectedCategory == category
                            ) {
                                let generator = UIImpactFeedbackGenerator(style: .light)
                                generator.impactOccurred()
                                selectedCategory = category
                            }
                        }
                    }
                    .padding(.horizontal, AppSpacing.md)
                }
            }
        }
        .padding(.vertical, AppSpacing.sm)
    }

    // MARK: - Custom Date Label

    private var customDateLabel: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM"
        return "\(formatter.string(from: customDateFrom)) - \(formatter.string(from: customDateTo))"
    }

    // MARK: - Custom Date Range Sheet

    private var customDateRangeSheet: some View {
        NavigationStack {
            Form {
                DatePicker("From", selection: $customDateFrom, displayedComponents: .date)
                DatePicker("To", selection: $customDateTo, in: customDateFrom..., displayedComponents: .date)
            }
            .navigationTitle("Custom Range")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        showCustomDateSheet = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        selectedDatePeriod = .custom
                        showCustomDateSheet = false
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Transaction List

    @ViewBuilder
    private var transactionList: some View {
        if isLoading {
            TransactionSkeletonList()
        } else if filteredTransactions.isEmpty {
            emptyState
        } else {
            let groups = groupedByMonth
            List {
                ForEach(groups, id: \.key) { group in
                    Section {
                        ForEach(group.items, id: \.transactionId) { item in
                            NavigationLink(destination: TransactionDetailView(transactionId: item.transactionId, facade: facade, onUpdate: reloadTransactions)) {
                                TransactionRowView(item: item)
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    transactionToDelete = item
                                    showDeleteConfirmation = true
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                            .swipeActions(edge: .leading, allowsFullSwipe: true) {
                                Button {
                                    editingTransactionId = item.transactionId
                                } label: {
                                    Label("Edit", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                        }
                    } header: {
                        SectionHeaderView(title: group.key)
                    }
                }
            }
            .listStyle(.plain)
            .refreshable {
                reloadTransactions()
            }
        }
    }

    // MARK: - Empty State

    @ViewBuilder
    private var emptyState: some View {
        VStack(spacing: AppSpacing.lg) {
            Spacer()

            Image(systemName: "tray")
                .font(.system(size: 64))
                .foregroundStyle(.tertiary)

            VStack(spacing: AppSpacing.sm) {
                Text(hasActiveFilters || !searchText.isEmpty ? "No Results" : "No Transactions Yet")
                    .font(AppTypography.title)
                    .foregroundStyle(.primary)

                Text(emptyStateMessage)
                    .font(AppTypography.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.xl)
            }

            if !hasActiveFilters && searchText.isEmpty {
                NavigationLink(destination: AddEditTransactionView(facade: facade, onSave: reloadTransactions)) {
                    Label("Add Transaction", systemImage: "plus.circle.fill")
                        .font(AppTypography.headline)
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.vertical, AppSpacing.md)
                        .background(Color.accentColor)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }
            }

            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var emptyStateMessage: String {
        if !searchText.isEmpty {
            return "No transactions match \"\(searchText)\". Try a different search."
        }
        if selectedDatePeriod != .allTime {
            return "No transactions found for the selected date range."
        }
        if selectedTypeFilter != .all {
            return "No \(selectedTypeFilter.displayName.lowercased()) transactions found."
        }
        if selectedCategory != nil {
            return "No transactions in this category."
        }
        return "Add your first transaction to start tracking your finances."
    }

    // MARK: - Sort Menu

    private var sortMenu: some View {
        Menu {
            ForEach(TransactionSortOrder.allCases, id: \.self) { order in
                Button {
                    sortOrder = order
                } label: {
                    HStack {
                        Text(order.rawValue)
                        if sortOrder == order {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            Image(systemName: "arrow.up.arrow.down")
        }
    }

    // MARK: - Data

    private func reloadTransactions() {
        transactions = facade.getAllTransactions()
        let snapshot = facade.initializeAndLoadHome()
        let snapshotCategories = snapshot.categories
        categories = snapshotCategories.isEmpty ? ["Others"] : snapshotCategories
        isLoading = false
    }
}

// MARK: - Section Header

private struct SectionHeaderView: View {
    let title: String

    var body: some View {
        Text(title)
            .font(AppTypography.caption)
            .fontWeight(.semibold)
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
    }
}

// MARK: - Filter Chip

private struct FilterChipView: View {
    let label: String
    var icon: String? = nil
    var dotColor: Color? = nil
    let isSelected: Bool
    let action: () -> Void
    @Environment(\.isAmoledActive) private var isAmoled

    var body: some View {
        Button(action: action) {
            HStack(spacing: AppSpacing.xs) {
                if let dotColor {
                    Circle()
                        .fill(dotColor)
                        .frame(width: 8, height: 8)
                }
                if let icon {
                    Image(systemName: icon)
                        .font(.caption2)
                }
                Text(label)
                    .font(AppTypography.caption)
            }
            .padding(.horizontal, AppSpacing.md)
            .padding(.vertical, 6)
            .background(isSelected ? Color.accentColor : AppColors.surface(isAmoled: isAmoled))
            .foregroundStyle(isSelected ? .white : .primary)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .strokeBorder(isSelected ? Color.accentColor : Color.clear, lineWidth: 1.5)
            )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
    }
}

// MARK: - Transaction Row (inline for list)

struct TransactionRowView: View {
    let item: SharedRecentTransactionItem

    private var iconInfo: CategoryIconInfo {
        AppColors.categoryIcon(for: item.category)
    }

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            ZStack {
                Circle()
                    .fill(iconInfo.color.opacity(0.15))
                    .frame(width: 42, height: 42)
                Image(systemName: iconInfo.systemName)
                    .font(.system(size: 18))
                    .foregroundColor(iconInfo.color)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(item.merchantName)
                    .font(AppTypography.body)
                    .lineLimit(1)
                HStack(spacing: AppSpacing.xs) {
                    Text(item.category)
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                    Text(Date(epochMillis: item.occurredAtEpochMillis).formatted(as: "dd MMM yyyy"))
                        .font(AppTypography.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            AmountText(
                amountMinor: item.amountMinor,
                currency: item.currency,
                transactionType: item.transactionType
            )
        }
        .padding(.vertical, AppSpacing.xs)
    }
}
