import Shared
import SwiftUI

extension SharedSubscriptionItem: @retroactive Identifiable {}

struct SubscriptionsScreen: View {
    @ObservedObject private var currencyManager = CurrencyManager.shared
    @StateObject private var viewModel = SubscriptionsViewModel()
    @State private var showAddSubscription = false
    @State private var editingSubscription: SharedSubscriptionItem?

    var body: some View {
        List {
            if viewModel.subscriptions.isEmpty {
                emptyState
            } else {
                subscriptionsList
            }
        }
        .refreshable {
            viewModel.loadSubscriptions()
        }
        .navigationTitle("Subscriptions")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAddSubscription = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .onAppear {
            viewModel.loadSubscriptions()
        }
        .sheet(isPresented: $showAddSubscription) {
            NavigationStack {
                AddEditSubscriptionScreen(
                    viewModel: viewModel,
                    onSave: { viewModel.loadSubscriptions() }
                )
            }
        }
        .sheet(item: $editingSubscription) { subscription in
            NavigationStack {
                AddEditSubscriptionScreen(
                    viewModel: viewModel,
                    editingSubscription: subscription,
                    onSave: { viewModel.loadSubscriptions() }
                )
            }
        }
    }

    // MARK: - Subscriptions List

    private var subscriptionsList: some View {
        Section {
            ForEach(viewModel.subscriptions) { subscription in
                SubscriptionRow(
                    subscription: subscription,
                    currencyCode: currencyManager.displayCurrency
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    editingSubscription = subscription
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                    Button(role: .destructive) {
                        _ = viewModel.deleteSubscription(id: subscription.id)
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
                .swipeActions(edge: .leading) {
                    Button {
                        editingSubscription = subscription
                    } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                    .tint(.orange)
                }
            }
        } header: {
            Text("\(viewModel.subscriptions.count) subscription\(viewModel.subscriptions.count == 1 ? "" : "s")")
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        Section {
            VStack(spacing: AppSpacing.md) {
                Image(systemName: "repeat.circle")
                    .font(.system(size: 48))
                    .foregroundStyle(.secondary)
                Text("No subscriptions yet")
                    .font(AppTypography.headline)
                Text("Tap + to add a subscription")
                    .font(AppTypography.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.xl)
            .listRowBackground(Color.clear)
        }
    }
}

// MARK: - Subscription Row

private struct SubscriptionRow: View {
    let subscription: SharedSubscriptionItem
    let currencyCode: String

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text(subscription.merchantName)
                    .font(AppTypography.body)
                    .lineLimit(1)

                HStack(spacing: AppSpacing.sm) {
                    if let category = subscription.category {
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

                    stateBadge
                }
            }

            Spacer()

            Text(CurrencyFormatter.format(
                amountMinor: subscription.amountMinor,
                currencyCode: currencyCode
            ))
            .font(AppTypography.amountMedium)
            .foregroundStyle(AppColors.expense)
        }
        .padding(.vertical, AppSpacing.xs)
    }

    @ViewBuilder
    private var stateBadge: some View {
        let state = subscription.state
        if state != "ACTIVE" {
            Text(state.capitalized)
                .font(AppTypography.caption2)
                .foregroundStyle(.secondary)
                .padding(.horizontal, AppSpacing.sm)
                .padding(.vertical, 2)
                .background(Color.secondary.opacity(0.1), in: Capsule())
        }
    }
}
