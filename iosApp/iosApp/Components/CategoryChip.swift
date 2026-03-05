import SwiftUI

struct CategoryChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: AppSpacing.xs) {
                Circle()
                    .fill(AppColors.categoryColor(for: title))
                    .frame(width: 8, height: 8)
                Text(title)
                    .font(AppTypography.caption)
            }
        }
        .buttonStyle(.borderedProminent)
        .tint(isSelected ? .accentColor : .gray.opacity(0.4))
    }
}

struct CategoryChipRow: View {
    let categories: [String]
    @Binding var selected: String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: AppSpacing.sm) {
                ForEach(categories, id: \.self) { category in
                    CategoryChip(
                        title: category,
                        isSelected: selected == category
                    ) {
                        selected = category
                    }
                }
            }
        }
    }
}
