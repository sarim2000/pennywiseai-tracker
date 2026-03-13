import SwiftUI

// MARK: - Shimmer Modifier

struct ShimmerModifier: ViewModifier {
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay(
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .white.opacity(0.4), location: 0.5),
                        .init(color: .clear, location: 1)
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .offset(x: phase * 200)
                .mask(content)
            )
            .onAppear {
                withAnimation(
                    .linear(duration: 1.2)
                    .repeatForever(autoreverses: false)
                ) {
                    phase = 1
                }
            }
    }
}

extension View {
    func shimmer() -> some View {
        modifier(ShimmerModifier())
    }
}

// MARK: - Skeleton Row

struct TransactionRowSkeleton: View {
    @Environment(\.isAmoledActive) private var isAmoled

    private var placeholderColor: Color {
        isAmoled ? AppColors.amoledSurfaceContainerHigh : Color(.systemGray5)
    }

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Circle()
                .fill(placeholderColor)
                .frame(width: 42, height: 42)

            VStack(alignment: .leading, spacing: 2) {
                RoundedRectangle(cornerRadius: AppCornerRadius.small)
                    .fill(placeholderColor)
                    .frame(width: 120, height: 14)

                RoundedRectangle(cornerRadius: AppCornerRadius.small)
                    .fill(placeholderColor)
                    .frame(width: 80, height: 10)
            }

            Spacer()

            RoundedRectangle(cornerRadius: AppCornerRadius.small)
                .fill(placeholderColor)
                .frame(width: 60, height: 14)
        }
        .padding(.vertical, AppSpacing.xs)
        .shimmer()
    }
}

// MARK: - Skeleton List

struct TransactionSkeletonList: View {
    var body: some View {
        List {
            ForEach(0..<8, id: \.self) { _ in
                TransactionRowSkeleton()
                    .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .disabled(true)
    }
}
