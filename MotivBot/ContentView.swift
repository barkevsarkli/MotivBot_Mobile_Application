import SwiftUI

struct ContentView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// True once the user leaves the welcome screen. View state only: the view model starts on the
    /// emotion input screen, and the welcome screen is shown in front of it until the user taps Start.
    @State private var hasStarted = false

    private enum Route: Hashable {
        case resume
        case welcome
        case screen(AppScreen)
    }

    private var route: Route {
        if viewModel.resumableSession != nil { return .resume }
        if viewModel.currentScreen == .emotionInput && !hasStarted { return .welcome }
        return .screen(viewModel.currentScreen)
    }

    var body: some View {
        ZStack {
            AppBackground()

            Group {
                switch route {
                case .resume:
                    ResumePromptView(onContinue: { hasStarted = true })
                case .welcome:
                    WelcomeView(onStart: { hasStarted = true })
                case .screen(.emotionInput):
                    EmotionInputView(onBack: { hasStarted = false })
                case .screen(.questionnaire):
                    QuestionnaireView()
                case .screen(.loading):
                    LoadingView()
                case .screen(.result):
                    ResultView()
                }
            }
            .transition(screenTransition)
        }
        .animation(DSMotion.animation(DSMotion.standard, reduceMotion: reduceMotion), value: route)
        .onChange(of: viewModel.currentScreen) { oldScreen, newScreen in
            // A restart returns to the emotion input screen; show the welcome screen again.
            if newScreen == .emotionInput && oldScreen != .emotionInput {
                hasStarted = false
            }
        }
        .environment(\.locale, localization.locale)
        // An export left behind when the app was closed with the share sheet open.
        .task { ResultExporter.removeFiles() }
    }

    private var screenTransition: AnyTransition {
        if reduceMotion { return .opacity }
        return .asymmetric(
            insertion: .opacity.combined(with: .offset(y: 16)),
            removal: .opacity
        )
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost { ContentView() }
}

#Preview("Dark") {
    PreviewHost { ContentView() }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL)") {
    PreviewHost(language: .persian) { ContentView() }
}
#endif
