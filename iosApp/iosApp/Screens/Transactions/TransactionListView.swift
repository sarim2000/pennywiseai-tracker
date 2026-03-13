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

// MARK: - TransactionListView

struct TransactionListView: View {
    private let facade: PennyWiseSharedFacade

    @State private var transactions: [SharedRecentTransactionItem] = []
    @State private var searchText = ""
    @State private var selectedTypeFilter: TransactionTypeFilter = .all
    @State private var selectedCategory: String? = nil
    @State private var sortOrder: TransactionSortOrder = .newestFirst
    @State private var categories: [String] = []
    @State private var showDeleteConfirmation = false
    @State private var transactionToDelete: SharedRecentTransactionItem? = nil
    @State private var editingTransactionId: Int64? = nil

    init(facade: PennyWiseSharedFacade) {
        self.facade = facade
    }

    private var filteredTransactions: [SharedRecentTransactionItem] {
        var result = transactions

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

    var body: some View {
        VStack(spacing: 0) {
            filterBar
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
    }

    // MARK: - Filter Bar

    @ViewBuilder
    private var filterBar: some View {
        VStack(spacing: AppSpacing.sm) {
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

    // MARK: - Transaction List

    @ViewBuilder
    private var transactionList: some View {
        let groups = groupedByMonth
        if filteredTransactions.isEmpty {
            emptyState
        } else {
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
                Text(searchText.isEmpty ? "No Transactions Yet" : "No Results")
                    .font(AppTypography.title)
                    .foregroundStyle(.primary)

                Text(emptyStateMessage)
                    .font(AppTypography.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, AppSpacing.xl)
            }

            if searchText.isEmpty && selectedTypeFilter == .all && selectedCategory == nil {
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
