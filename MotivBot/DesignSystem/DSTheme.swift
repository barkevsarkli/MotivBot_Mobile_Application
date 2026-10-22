import SwiftUI

/// The appearance the user picked. `system` follows the device setting and is the default;
/// `light` or `dark` is set by `ThemeToggle` as the opposite of the system appearance.
enum ThemePreference: String {
    case system
    case light
    case dark

    /// `@AppStorage` key. The value is a display preference, not personal data.
    static let storageKey = "themePreference"

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

// MARK: - Language formatting

extension AppLanguage {
    /// Persian uses Persian digits and the Solar Hijri calendar; Turkish the Gregorian calendar.
    var locale: Locale {
        switch self {
        case .turkish: return Locale(identifier: "tr_TR")
        case .persian: return Locale(identifier: "fa_IR")
        }
    }

    var calendar: Calendar {
        var calendar = Calendar(identifier: self == .persian ? .persian : .gregorian)
        calendar.locale = locale
        return calendar
    }

    /// The language's own name, shown in the language picker.
    var nameKey: String { "language_name_\(rawValue)" }
}

extension LocalizationManager {
    var locale: Locale { currentLanguage.locale }

    /// A localized string with `String(format:)` arguments, formatted in the current language
    /// (so Persian gets Persian digits).
    func localizedString(_ key: String, _ arguments: CVarArg...) -> String {
        String(format: localizedString(key), locale: locale, arguments: arguments)
    }

    /// A whole number in the current language's digits.
    func number(_ value: Int) -> String {
        value.formatted(.number.locale(locale))
    }

    /// A long date in the current language and calendar.
    func longDate(_ date: Date) -> String {
        date.formatted(Date.FormatStyle(date: .long, time: .omitted, locale: locale, calendar: currentLanguage.calendar))
    }
}
