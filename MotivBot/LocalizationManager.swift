import SwiftUI
import Observation

// MARK: - Supported Languages

enum AppLanguage: String, CaseIterable, Identifiable, Codable {
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

    var layoutDirection: LayoutDirection {
        self == .persian ? .rightToLeft : .leftToRight
    }
}

// MARK: - Localization Manager

/// Handles runtime language switching by loading strings from the correct .lproj bundle.
///
/// Once an assessment starts the language is locked, so the text, the answers and the results
/// stay in one language. While locked, `toggleLanguage()` and `setLanguage(_:)` do nothing.
@Observable
class LocalizationManager {

    private(set) var currentLanguage: AppLanguage = .turkish

    /// True from the start of an assessment until restart.
    private(set) var isLocked = false

    /// Apply at the root view.
    var layoutDirection: LayoutDirection { currentLanguage.layoutDirection }

    private var bundle: Bundle = .main

    init() {
        loadBundle(for: .turkish)
    }

    func toggleLanguage() {
        setLanguage(currentLanguage.toggled)
    }

    func setLanguage(_ language: AppLanguage) {
        guard !isLocked else { return }
        currentLanguage = language
        loadBundle(for: language)
    }

    /// Locks the current language, or switches to `language` first (e.g. a resumed session's).
    func lock(to language: AppLanguage? = nil) {
        if let language, language != currentLanguage {
            currentLanguage = language
            loadBundle(for: language)
        }
        isLocked = true
    }

    func unlock() {
        isLocked = false
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
