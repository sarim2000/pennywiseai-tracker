import SwiftUI

struct OnboardingScreen: View {
    let onComplete: () -> Void

    @StateObject private var viewModel = OnboardingViewModel()
    @ObservedObject private var themeManager = ThemeManager.shared
    @Environment(\.isAmoledActive) private var isAmoled

    private let totalSteps = 3

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $viewModel.currentStep) {
                WelcomeStep()
                    .tag(0)

                ProfileStep()
                    .tag(1)

                ImportStep(viewModel: viewModel)
                    .tag(2)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .animation(.easeInOut(duration: 0.3), value: viewModel.currentStep)

            bottomBar
                .padding(.horizontal, AppSpacing.lg)
                .padding(.bottom, AppSpacing.lg)
        }
        .background(AppColors.background(isAmoled: isAmoled))
    }

    @ViewBuilder
    private var bottomBar: some View {
        VStack(spacing: AppSpacing.md) {
            pageIndicator

            HStack {
                if viewModel.currentStep == totalSteps - 1 {
                    Button("Skip") {
                        finishOnboarding()
                    }
                    .font(AppTypography.body)
                    .foregroundColor(.secondary)

                    Spacer()

                    Button {
                        finishOnboarding()
                    } label: {
                        Text("Get Started")
                            .font(AppTypography.headline)
                            .foregroundColor(.white)
                            .padding(.horizontal, AppSpacing.xl)
                            .padding(.vertical, AppSpacing.sm)
                            .background(themeManager.accentColor)
                            .cornerRadius(AppCornerRadius.medium)
                    }
                } else if viewModel.currentStep == 0 {
                    Spacer()

                    Button {
                        withAnimation {
                            viewModel.currentStep = 1
                        }
                    } label: {
                        Text("Get Started")
                            .font(AppTypography.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AppSpacing.md)
                            .background(themeManager.accentColor)
                            .cornerRadius(AppCornerRadius.medium)
                    }

                    Spacer()
                } else {
                    Button("Back") {
                        withAnimation {
                            viewModel.currentStep -= 1
                        }
                    }
                    .font(AppTypography.body)
                    .foregroundColor(.secondary)

                    Spacer()

                    Button {
                        withAnimation {
                            viewModel.currentStep += 1
                        }
                    } label: {
                        Text("Next")
                            .font(AppTypography.headline)
                            .foregroundColor(.white)
                            .padding(.horizontal, AppSpacing.xl)
                            .padding(.vertical, AppSpacing.sm)
                            .background(themeManager.accentColor)
                            .cornerRadius(AppCornerRadius.medium)
                    }
                }
            }
        }
    }

    private var pageIndicator: some View {
        HStack(spacing: AppSpacing.sm) {
            ForEach(0..<totalSteps, id: \.self) { index in
                Circle()
                    .fill(index == viewModel.currentStep ? themeManager.accentColor : Color.secondary.opacity(0.3))
                    .frame(width: 8, height: 8)
                    .animation(.easeInOut(duration: 0.2), value: viewModel.currentStep)
            }
        }
    }

    private func finishOnboarding() {
        viewModel.completeOnboarding()
        onComplete()
    }
}

struct OnboardingScreen_Previews: PreviewProvider {
    static var previews: some View {
        OnboardingScreen(onComplete: {})
    }
}
