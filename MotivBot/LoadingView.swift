import SwiftUI
import Combine

struct LoadingView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @State private var rotation: Double = 0
    @State private var pulse: Bool = false
    @State private var showText = false
    @State private var currentTipIndex = 0
    
    private let tips: [String] = [
        "loading_tip_1",
        "loading_tip_2",
        "loading_tip_3"
    ]
    
    let timer = Timer.publish(every: 3.0, on: .main, in: .common).autoconnect()
    
    var body: some View {
        VStack(spacing: 36) {
            Spacer()
            
            // Animated loading indicator
            ZStack {
                // Outer ring
                Circle()
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.3),
                                Color(red: 0.42, green: 0.68, blue: 0.84).opacity(0.1)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 4
                    )
                    .frame(width: 100, height: 100)
                
                // Spinning arc
                Circle()
                    .trim(from: 0, to: 0.3)
                    .stroke(
                        LinearGradient(
                            colors: [
                                Color(red: 0.55, green: 0.48, blue: 0.85),
                                Color(red: 0.42, green: 0.68, blue: 0.84)
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        ),
                        style: StrokeStyle(lineWidth: 4, lineCap: .round)
                    )
                    .frame(width: 100, height: 100)
                    .rotationEffect(.degrees(rotation))
                    .opacity(isFailed ? 0 : 1)
                
                // Center icon
                Image(systemName: "brain.head.profile")
                    .font(.system(size: 36))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .scaleEffect(pulse ? 1.1 : 0.95)
            }
            
            // Loading text
            VStack(spacing: 12) {
                Text(localization.localizedString("loading_title"))
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
                
                Text(localization.localizedString(subtitleKey))
                    .font(.system(size: 15, design: .rounded))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                    .id(subtitleKey)
                    .transition(.opacity)
                    .animation(.easeInOut(duration: 0.3), value: subtitleKey)
            }
            .opacity(showText ? 1 : 0)
            .offset(y: showText ? 0 : 20)
            
            // Rotating tips
            Text(localization.localizedString(tips[currentTipIndex]))
                .font(.system(size: 14, weight: .medium, design: .rounded))
                .foregroundColor(.secondary.opacity(0.7))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 50)
                .transition(.opacity)
                .id(currentTipIndex)
                .animation(.easeInOut(duration: 0.5), value: currentTipIndex)
            
            // Error display
            if case .failed(let error) = viewModel.analysisPhase {
                VStack(spacing: 12) {
                    Text(localization.localizedString(errorKey(for: error)))
                        .font(.system(size: 14, design: .rounded))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, 30)
                    
                    Button(localization.localizedString("retry_button")) {
                        Task {
                            await viewModel.submitForAnalysis()
                        }
                    }
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(
                        Capsule()
                            .fill(Color(red: 0.55, green: 0.48, blue: 0.85))
                    )
                }
            }
            
            Spacer()
        }
        .onAppear {
            // Start animations
            withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) {
                rotation = 360
            }
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                pulse = true
            }
            withAnimation(.easeOut(duration: 0.6).delay(0.3)) {
                showText = true
            }
        }
        .onReceive(timer) { _ in
            withAnimation {
                currentTipIndex = (currentTipIndex + 1) % tips.count
            }
        }
    }

    private var isFailed: Bool {
        if case .failed = viewModel.analysisPhase { return true }
        return false
    }

    private var subtitleKey: String {
        switch viewModel.analysisPhase {
        case .modelStarting: return "loading_model_starting"
        case .failed: return "loading_failed_subtitle"
        case .idle, .connecting, .analysing: return "loading_subtitle"
        }
    }

    private func errorKey(for error: MotivBotAPIError) -> String {
        switch error {
        case .serverUnreachable: return "error_server_unreachable"
        case .modelLoading: return "error_model_loading"
        case .invalidInput: return "error_invalid_input"
        case .decoding, .http: return "error_generic"
        }
    }
}

#Preview {
    LoadingView()
        .environment(WellBeingViewModel())
        .environment(LocalizationManager())
}
