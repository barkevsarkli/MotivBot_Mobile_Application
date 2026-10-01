import SwiftUI
import Observation

// MARK: - Supported Languages

enum AppLanguage: String, CaseIterable, Identifiable {
    case turkish = "tr"
    case persian = "fa"
    
    var id: String { rawValue }
    
    var displayLabel: String {
        switch self {
        case .turkish: return "TR"
        case .persian: return "FA"
        }
    }
    
    var toggled: AppLanguage {
        switch self {
        case .turkish: return .persian
        case .persian: return .turkish
        }
    }
}

// MARK: - Localization Manager

/// Handles runtime language switching by loading strings from the correct .lproj bundle.
@Observable
class LocalizationManager {
    
    var currentLanguage: AppLanguage = .turkish
    
    private var bundle: Bundle = .main
    
    init() {
        loadBundle(for: .turkish)
    }
    
    func toggleLanguage() {
        let newLanguage = currentLanguage.toggled
        currentLanguage = newLanguage
        loadBundle(for: newLanguage)
    }
    
    func setLanguage(_ language: AppLanguage) {
        currentLanguage = language
        loadBundle(for: language)
    }
    
    func localizedString(_ key: String) -> String {
        return bundle.localizedString(forKey: key, value: nil, table: nil)
    }
    
    private func loadBundle(for language: AppLanguage) {
        if let path = Bundle.main.path(forResource: language.rawValue, ofType: "lproj"),
           let langBundle = Bundle(path: path) {
            bundle = langBundle
        } else {
            bundle = .main
        }
    }
}
