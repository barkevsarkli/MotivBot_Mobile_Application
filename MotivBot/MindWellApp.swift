import SwiftUI

@main
struct MindWellApp: App {
    
    @State private var viewModel = WellBeingViewModel()
    @State private var localization = LocalizationManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(viewModel)
                .environment(localization)
                .preferredColorScheme(viewModel.isDarkMode ? .dark : .light)
                .environment(\.layoutDirection, localization.currentLanguage == .persian ? .rightToLeft : .leftToRight)
        }
    }
}
