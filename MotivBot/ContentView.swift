import SwiftUI

struct ContentView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    
    var body: some View {
        @Bindable var viewModel = viewModel
        
        ZStack {
            // Dynamic gradient background
            backgroundGradient
                .ignoresSafeArea()
            
            // Main content (screen switching)
            VStack {
                switch viewModel.currentScreen {
                case .emotionInput:
                    EmotionInputView()
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                case .questionnaire:
                    QuestionnaireView()
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                case .loading:
                    LoadingView()
                        .transition(.opacity.combined(with: .scale))
                case .result:
                    ResultView()
                        .transition(.asymmetric(
                            insertion: .move(edge: .bottom).combined(with: .opacity),
                            removal: .opacity
                        ))
                }
            }
            .animation(.easeInOut(duration: 0.45), value: viewModel.currentScreen)
            
            // Top bar: Theme toggle (left) + Language toggle (right)
            VStack {
                HStack {
                    ThemeToggleView()
                    Spacer()
                    LanguageToggleView()
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                Spacer()
            }
        }
        .onAppear {
            viewModel.languageCode = localization.currentLanguage.rawValue
        }
        .onChange(of: localization.currentLanguage) { _, newLanguage in
            viewModel.languageCode = newLanguage.rawValue
        }
    }
    
    // MARK: - Background Gradient
    
    private var backgroundGradient: some View {
        Group {
            if viewModel.isDarkMode {
                LinearGradient(
                    colors: [
                        Color(red: 0.08, green: 0.08, blue: 0.15),
                        Color(red: 0.12, green: 0.10, blue: 0.22),
                        Color(red: 0.06, green: 0.06, blue: 0.12)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            } else {
                LinearGradient(
                    colors: [
                        Color(red: 0.93, green: 0.91, blue: 0.98),
                        Color(red: 0.87, green: 0.92, blue: 0.99),
                        Color(red: 0.95, green: 0.90, blue: 0.96)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
        }
    }
}

#Preview {
    ContentView()
        .environment(WellBeingViewModel())
        .environment(LocalizationManager())
}
