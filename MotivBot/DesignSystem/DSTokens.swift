import SwiftUI

// MARK: - Typography

/// Text styles of the design system. All of them scale with Dynamic Type.
/// Titles use the rounded design; body text keeps the default design, which reads better in long
/// Turkish and Persian text. Persian falls back to the system Arabic-script font automatically.
enum DSFont {
    static let display = Font.system(.largeTitle, design: .rounded, weight: .bold)
    static let title = Font.system(.title2, design: .rounded, weight: .semibold)
    static let heading = Font.system(.headline, design: .rounded, weight: .semibold)
    static let question = Font.system(.title3, design: .default, weight: .medium)
    static let body = Font.system(.body)
    static let bodyEmphasis = Font.system(.body, weight: .semibold)
    static let callout = Font.system(.callout)
    static let footnote = Font.system(.footnote)
    static let caption = Font.system(.caption, weight: .medium)
    static let label = Font.system(.subheadline, design: .rounded, weight: .semibold)
}

// MARK: - Spacing, radii, sizes

/// A 4 pt spacing scale.
enum DSSpacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let s: CGFloat = 12
    static let m: CGFloat = 16
    static let l: CGFloat = 24
    static let xl: CGFloat = 32
    static let xxl: CGFloat = 48
    /// Horizontal margin of every screen.
    static let screen: CGFloat = 20
}

enum DSRadius {
    static let small: CGFloat = 12
    static let medium: CGFloat = 16
    static let large: CGFloat = 24
}

enum DSSize {
    /// The smallest tap target allowed (Apple HIG and WCAG).
    static let minTap: CGFloat = 44
    static let answerOptionMinHeight: CGFloat = 56
    static let buttonMinHeight: CGFloat = 54
    /// Readable line length on wide screens.
    static let contentMaxWidth: CGFloat = 560
}

// MARK: - Motion

/// Short, calm animations. Use `DSMotion.animation(_:reduceMotion:)` so Reduce Motion turns
/// movement into a plain cross-fade.
enum DSMotion {
    static let quick = Animation.easeOut(duration: 0.2)
    static let standard = Animation.easeInOut(duration: 0.35)
    static let gentle = Animation.easeOut(duration: 0.5)
    static let soft = Animation.spring(response: 0.45, dampingFraction: 0.85)
    /// Delay before a single-choice answer moves on to the next question.
    static let autoAdvanceDelay: Duration = .milliseconds(380)

    static func animation(_ animation: Animation, reduceMotion: Bool) -> Animation {
        reduceMotion ? .easeInOut(duration: 0.2) : animation
    }
}

// MARK: - Body text

private struct DSBodyTextModifier: ViewModifier {
    @Environment(\.layoutDirection) private var layoutDirection

    func body(content: Content) -> some View {
        content
            // Persian script needs more room between lines than Latin script.
            .lineSpacing(layoutDirection == .rightToLeft ? 5 : 3)
            .fixedSize(horizontal: false, vertical: true)
    }
}

extension View {
    /// Line spacing and wrapping for paragraphs.
    func dsBodyText() -> some View {
        modifier(DSBodyTextModifier())
    }

    /// Large titles. Growth stops at accessibility size 2, and long Turkish words shrink
    /// instead of breaking in the middle.
    func dsTitleText() -> some View {
        self
            .dynamicTypeSize(...DynamicTypeSize.accessibility2)
            .lineLimit(4)
            .minimumScaleFactor(0.5)
    }
}
