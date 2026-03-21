import Shared
import SwiftUI

extension SharedRuleItem: @retroactive Identifiable {}

struct RulesScreen: View {
    @StateObject private var viewModel = RulesViewModel()
    @State private var showAddRule = false
    @State private var editingRule: SharedRuleItem?

    var body: some View {
        List {
            if viewModel.rules.isEmpty {
                emptyState
            } else {
                rulesList
            }
        }
        .refreshable {
            viewModel.loadRules()
        }
        .navigationTitle("Smart Rules")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAddRule = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .onAppear {
            viewModel.loadRules()
        }
        .sheet(isPresented: $showAddRule) {
            NavigationStack {
                AddEditRuleScreen(
                    viewModel: viewModel,
                    onSave: { viewModel.loadRules() }
                )
            }
        }
        .sheet(item: $editingRule) { rule in
            NavigationStack {
                AddEditRuleScreen(
                    viewModel: viewModel,
                    editingRule: rule,
                    onSave: { viewModel.loadRules() }
                )
            }
        }
    }

    // MARK: - Rules List

    private var rulesList: some View {
        Section {
            ForEach(viewModel.rules) { rule in
                RuleRow(rule: rule, onToggle: { enabled in
                    _ = viewModel.toggleRule(id: rule.id, isEnabled: enabled)
                })
                .contentShape(Rectangle())
                .onTapGesture {
                    editingRule = rule
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    Button(role: .destructive) {
                        _ = viewModel.deleteRule(id: rule.id)
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
                .swipeActions(edge: .leading) {
                    Button {
                        editingRule = rule
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    .tint(.orange)
                }
            }
        } header: {
            Text("\(viewModel.rules.count) rule\(viewModel.rules.count == 1 ? "" : "s")")
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        Section {
            VStack(spacing: AppSpacing.md) {
                Image(systemName: "wand.and.stars")
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
                Text("No smart rules yet")
                    .font(AppTypography.headline)
                Text("Tap + to create a rule that auto-categorizes transactions by keyword")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.xl)
            .listRowBackground(Color.clear)
        }
    }
}

// MARK: - Rule Row

private struct RuleRow: View {
    let rule: SharedRuleItem
    let onToggle: (Bool) -> Void

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text(rule.name)
                    .font(AppTypography.body)
                    .lineLimit(1)

                HStack(spacing: AppSpacing.sm) {
                    if let keyword = extractKeyword(from: rule.conditionsJson) {
                        Label(keyword, systemImage: "text.magnifyingglass")
                            .font(AppTypography.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    if let category = extractCategory(from: rule.actionsJson) {
                        Text(category)
                            .font(AppTypography.caption2)
                            .foregroundStyle(AppColors.categoryColor(for: category))
                            .padding(.horizontal, AppSpacing.sm)
                            .padding(.vertical, 2)
                            .background(
                                AppColors.categoryColor(for: category).opacity(0.1),
                                in: Capsule()
                            )
                    }
                }
            }

            Spacer()

            Toggle("", isOn: Binding(
                get: { rule.isEnabled },
                set: { onToggle($0) }
            ))
            .labelsHidden()
        }
        .padding(.vertical, AppSpacing.xs)
    }

    private func extractKeyword(from json: String) -> String? {
        guard let data = json.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let value = dict["value"] as? String else {
            return nil
        }
        return value
    }

    private func extractCategory(from json: String) -> String? {
        guard let data = json.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let category = dict["category"] as? String else {
            return nil
        }
        return category
    }
}
