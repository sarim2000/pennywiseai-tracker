import Shared
import SwiftUI

extension SharedCategoryItem: @retroactive Identifiable {}

struct CategoriesScreen: View {
    let facade: PennyWiseSharedFacade
    @StateObject private var viewModel: CategoriesViewModel
    @State private var showAddCategory = false
    @State private var editingCategory: SharedCategoryItem?

    init(facade: PennyWiseSharedFacade) {
        self.facade = facade
        _viewModel = StateObject(wrappedValue: CategoriesViewModel(facade: facade))
    }

    private var systemCategories: [SharedCategoryItem] {
        viewModel.categories.filter { $0.isSystem }
    }

    private var customCategories: [SharedCategoryItem] {
        viewModel.categories.filter { !$0.isSystem }
    }

    var body: some View {
        List {
            if !systemCategories.isEmpty {
                Section {
                    ForEach(systemCategories) { category in
                        CategoryRow(category: category)
                    }
                } header: {
                    Text("System Categories")
                }
            }

            Section {
                if customCategories.isEmpty {
                    Text("No custom categories yet")
                        .font(AppTypography.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .listRowBackground(Color.clear)
                } else {
                    ForEach(customCategories) { category in
                        CategoryRow(category: category)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    _ = viewModel.deleteCategory(id: category.id)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                            .swipeActions(edge: .leading) {
                                Button {
                                    editingCategory = category
                                } label: {
                                    Label("Edit", systemImage: "pencil")
                                }
                                .tint(.orange)
                            }
                    }
                }
            } header: {
                Text("Custom Categories")
            }
        }
        .navigationTitle("Categories")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAddCategory = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .onAppear {
            viewModel.loadCategories()
        }
        .sheet(isPresented: $showAddCategory) {
            NavigationStack {
                AddEditCategoryScreen(
                    facade: facade,
                    onSave: { viewModel.loadCategories() }
                )
            }
        }
        .sheet(item: $editingCategory) { category in
            NavigationStack {
                AddEditCategoryScreen(
                    facade: facade,
                    editingCategory: category,
                    onSave: { viewModel.loadCategories() }
                )
            }
        }
    }
}

// MARK: - Category Row

private struct CategoryRow: View {
    let category: SharedCategoryItem

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Circle()
                .fill(Color(hex: categoryColorHex))
                .frame(width: 12, height: 12)

            Text(category.name)
                .font(AppTypography.body)

            Spacer()

            if category.isIncome {
                Text("Income")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.green)
                    .padding(.horizontal, AppSpacing.sm)
                    .padding(.vertical, AppSpacing.xs)
                    .background(Color.green.opacity(0.1), in: Capsule())
            }

            if category.isSystem {
                Text("System")
                    .font(AppTypography.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, AppSpacing.sm)
                    .padding(.vertical, AppSpacing.xs)
                    .background(Color.secondary.opacity(0.1), in: Capsule())
            }
        }
        .padding(.vertical, AppSpacing.xs)
    }

    private var categoryColorHex: UInt {
        let hex = category.colorHex
            .replacingOccurrences(of: "#", with: "")
            .replacingOccurrences(of: "0x", with: "")
            .replacingOccurrences(of: "0X", with: "")
        let trimmed = hex.count > 6 ? String(hex.suffix(6)) : hex
        return UInt(trimmed, radix: 16) ?? 0x78909C
    }
}
