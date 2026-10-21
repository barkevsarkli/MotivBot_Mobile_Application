import SwiftUI

struct LoadingView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isBreathingIn = false
    @State private var currentTipIndex = 0

    private let tips = ["loading_tip_1", "loading_tip_2", "loading_tip_3"]
    private let tipInterval: Duration = .seconds(6)

    var body: some View {
        ScrollView {
            ScreenColumn(spacing: DSSpacing.xl) {
                Spacer(minLength: DSSpacing.xl)

                breathingMark
                    .frame(maxWidth: .infinity)

                VStack(spacing: DSSpacing.s) {
                    Text(localization.localizedString(isFailed ? "loading_failed_title" : "loading_title"))
                        .font(DSFont.title)
                        .foregroundStyle(Color.dsTextPrimary)
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)

                    Text(localization.localizedString(subtitleKey))
                        .font(DSFont.body)
                        .foregroundStyle(Color.dsTextSecondary)
                        .multilineTextAlignment(.center)
                        .dsBodyText()
                        .id(subtitleKey)
                        .transition(.opacity)
                }
                .frame(maxWidth: .infinity)
                .animation(DSMotion.gentle, value: subtitleKey)

                statusContent
                    .frame(maxWidth: .infinity)
            }
            .padding(.bottom, DSSpacing.l)
            .dsAppear()
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .top) { TopBar() }
        .safeAreaInset(edge: .bottom) {
            if isFailed {
                BottomActionBar {
                    Button {
                        Task { await viewModel.submitForAnalysis() }
                    } label: {
                        DSButtonLabel(title: localization.localizedString("retry_button"), systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(.dsPrimary)
                }
                .transition(.opacity)
            }
        }
        .animation(DSMotion.standard, value: isFailed)
        .onAppear(perform: startBreathing)
        .onChange(of: reduceMotion) { startBreathing() }
        .task(id: isFailed) {
            guard !isFailed else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: tipInterval)
                guard !Task.isCancelled else { return }
                withAnimation(DSMotion.gentle) {
                    currentTipIndex = (currentTipIndex + 1) % tips.count
                }
            }
        }
        .onChange(of: viewModel.analysisPhase) { _, phase in
            announce(phase)
        }
    }

    // MARK: - Parts

    /// Two soft circles that grow and shrink over four seconds, like a slow breath.
    private var breathingMark: some View {
        ZStack {
            Circle()
                .fill(Color.dsBrandSoft)
                .frame(width: 148, height: 148)
                .scaleEffect(isBreathingIn ? 1 : 0.82)
            Circle()
                .fill(Color.dsBrand.opacity(0.18))
                .frame(width: 104, height: 104)
                .scaleEffect(isBreathingIn ? 1 : 0.88)
            Image(systemName: isFailed ? "cloud" : "leaf.fill")
                .font(.system(size: 40))
                .foregroundStyle(Color.dsBrand)
                .contentTransition(.symbolEffect(.replace))
        }
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var statusContent: some View {
        switch viewModel.analysisPhase {
        case .failed(let error):
            Banner(kind: .notice, text: localization.localizedString(error.messageKey))
                .transition(.opacity)
        case .modelWakingUp:
            VStack(spacing: DSSpacing.m) {
                Banner(kind: .info, text: localization.localizedString("loading_waking_banner"))
                tip
            }
            .transition(.opacity)
        case .idle, .connecting, .analysing:
            tip
        }
    }

    private var tip: some View {
        Text(localization.localizedString(tips[currentTipIndex]))
            .font(DSFont.callout)
            .foregroundStyle(Color.dsTextSecondary)
            .multilineTextAlignment(.center)
            .dsBodyText()
            .padding(.horizontal, DSSpacing.m)
            .id(currentTipIndex)
            .transition(.opacity)
            // Changing every few seconds would be noisy for VoiceOver; the status is announced instead.
            .accessibilityHidden(true)
    }

    // MARK: - Behaviour

    private var isFailed: Bool {
        if case .failed = viewModel.analysisPhase { return true }
        return false
    }

    private var subtitleKey: String {
        switch viewModel.analysisPhase {
        case .modelWakingUp: return "loading_model_starting"
        case .failed: return "loading_failed_subtitle"
        case .idle, .connecting, .analysing: return "loading_subtitle"
        }
    }

    private func startBreathing() {
        guard !reduceMotion else {
            isBreathingIn = true
            return
        }
        isBreathingIn = false
        withAnimation(.easeInOut(duration: 4).repeatForever(autoreverses: true)) {
            isBreathingIn = true
        }
    }

    private func announce(_ phase: WellBeingViewModel.AnalysisPhase) {
        let text: String
        switch phase {
        case .modelWakingUp: text = localization.localizedString("loading_model_starting")
        case .failed(let error): text = localization.localizedString(error.messageKey)
        case .idle, .connecting, .analysing: return
        }
        AccessibilityNotification.Announcement(text).post()
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost(.loading(.analysing)) { LoadingView() }
}

#Preview("Dark, waking up") {
    PreviewHost(.loading(.modelWakingUp)) { LoadingView() }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL), failed") {
    PreviewHost(.loading(.failed(.offline)), language: .persian) { LoadingView() }
}
#endif
