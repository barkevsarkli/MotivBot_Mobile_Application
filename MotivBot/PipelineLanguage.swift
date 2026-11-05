import Foundation

/// The language of a text as the pipeline detects it. The Space picks the language of the motivational
/// messages from the text alone, so the app refuses text the pipeline would read as another language.
///
/// Mirrors `detect_language` in the Space's app.py. Keep the two in step.
enum PipelineLanguage: Equatable {
    case turkish
    case persian
    case english

    /// Arabic-script block, U+0600-U+06FF.
    private static let persianScalars: ClosedRange<UInt32> = 0x0600...0x06FF
    private static let turkishLetters = Set("ğüşıöçĞÜŞİÖÇ".unicodeScalars)
    private static let turkishWords = ["bir", "ve", "bu", "için", "çok", "ben", "sen", "değil", "var", "yok", "ama", "daha"]

    static func detect(_ text: String) -> PipelineLanguage {
        let scalars = text.unicodeScalars
        let persianCount = scalars.filter { persianScalars.contains($0.value) }.count
        if Double(persianCount) / Double(max(scalars.count, 1)) > 0.3 {
            return .persian
        }

        let hasTurkishLetter = scalars.contains { turkishLetters.contains($0) }
        let padded = " \(text.lowercased()) "
        let turkishWordCount = turkishWords.filter { padded.contains(" \($0) ") }.count
        if hasTurkishLetter || turkishWordCount >= 2 {
            return .turkish
        }
        return .english
    }
}

extension AppLanguage {
    /// The language the pipeline must detect in the text for its messages to match the app.
    var pipelineLanguage: PipelineLanguage {
        switch self {
        case .turkish: return .turkish
        case .persian: return .persian
        }
    }
}
