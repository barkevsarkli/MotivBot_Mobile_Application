import SwiftUI

@main
struct MindWellApp: App {
    
    @Environment(\.scenePhase) private var scenePhase
    @State private var localization: LocalizationManager
    @State private var viewModel: WellBeingViewModel
    @AppStorage(ThemePreference.storageKey) private var themePreference: ThemePreference = .system

    init() {
        let localization = LocalizationManager()
        _localization = State(initialValue: localization)
        _viewModel = State(initialValue: WellBeingViewModel(localization: localization))
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(viewModel)
                .environment(localization)
                .preferredColorScheme(themePreference.colorScheme)
                .environment(\.layoutDirection, localization.layoutDirection)
                .task { viewModel.prewarmService() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { viewModel.flushPendingSave() }
        }
    }
}
