import SwiftUI

// MARK: - Tone colours

extension WellBeingDimension.Tone {
    /// Sage for steady, honey for gentle, dusk plum for care. No reds: nothing here is an alarm.
    var color: Color {
        switch self {
        case .steady: return .dsSteady
        case .gentle: return .dsGentle
        case .care: return .dsCare
        }
    }

    var softColor: Color {
        switch self {
        case .steady: return .dsSteadySoft
        case .gentle: return .dsGentleSoft
        case .care: return .dsCareSoft
        }
    }
}

extension PredictionResponse.Level {
    /// How many of the three steps are filled: how strongly the dimension is present.
    var filledSteps: Int {
        switch self {
        case .no: return 1
        case .low: return 2
        case .high: return 3
        }
    }
}

// MARK: - Message categories

/// How a pipeline message category looks. Categories arrive as English strings from the pipeline.
enum MessageCategoryStyle: Int, CaseIterable {
    case validation
    case reframing
    case action
    case other

    init(category: String) {
        switch category {
        case "Emotional Validation": self = .validation
        case "Cognitive Reframing": self = .reframing
        case "Action-Oriented Encouragement": self = .action
        default: self = .other
        }
    }

    var titleKey: String {
        switch self {
        case .validation: return "message_category_validation"
        case .reframing: return "message_category_reframing"
        case .action: return "message_category_action"
        case .other: return "message_category_other"
        }
    }

    var systemImage: String {
        switch self {
        case .validation: return "heart"
        case .reframing: return "arrow.triangle.2.circlepath"
        case .action: return "leaf"
        case .other: return "sparkles"
        }
    }

    var color: Color {
        switch self {
        case .validation: return .dsPeach
        case .reframing: return .dsSky
        case .action: return .dsMint
        case .other: return .dsBrand
        }
    }

    var softColor: Color {
        switch self {
        case .validation: return .dsPeachSoft
        case .reframing: return .dsSkySoft
        case .action: return .dsMintSoft
        case .other: return .dsBrandSoft
        }
    }

    /// Validation, then reframing, then action, keeping the pipeline's order within a category.
    static func ordered(_ messages: [PredictionResponse.Message]) -> [PredictionResponse.Message] {
        messages.enumerated()
            .sorted { lhs, rhs in
                let left = MessageCategoryStyle(category: lhs.element.category).rawValue
                let right = MessageCategoryStyle(category: rhs.element.category).rawValue
                return left == right ? lhs.offset < rhs.offset : left < right
            }
            .map(\.element)
    }
}

// MARK: - Dimension row

/// One result dimension: icon, soft title, three-step level and one supportive sentence.
/// VoiceOver reads the title and the sentence only, never a level number.
struct DimensionRow: View {
    let dimension: WellBeingDimension
    let level: PredictionResponse.Level
    let title: String
    let message: String
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var tone: WellBeingDimension.Tone { dimension.tone(for: level) }

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: DSSpacing.s) {
                    icon
                    titleAndLevel
                    sentence
                }
            } else {
                HStack(alignment: .top, spacing: DSSpacing.s) {
                    icon
                    VStack(alignment: .leading, spacing: DSSpacing.xs) {
                        titleAndLevel
                        sentence
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(message)
    }

    private var icon: some View {
        Image(systemName: dimension.iconName)
            .font(.system(.body, weight: .semibold))
            .foregroundStyle(tone.color)
            .frame(width: 40, height: 40)
            .background(tone.softColor, in: Circle())
    }

    private var titleAndLevel: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .firstTextBaseline, spacing: DSSpacing.xs) {
                titleText
                Spacer(minLength: DSSpacing.xs)
                LevelIndicator(filledSteps: level.filledSteps, color: tone.color)
            }
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                titleText
                LevelIndicator(filledSteps: level.filledSteps, color: tone.color)
            }
        }
    }

    private var titleText: some View {
        Text(title)
            .font(DSFont.heading)
            .foregroundStyle(Color.dsTextPrimary)
    }

    private var sentence: some View {
        Text(message)
            .font(DSFont.callout)
            .foregroundStyle(Color.dsTextSecondary)
            .dsBodyText()
    }
}

// MARK: - Message card

/// A motivational message from the pipeline, tinted by its category.
struct MessageCard: View {
    let style: MessageCategoryStyle
    let categoryTitle: String
    let text: String
    let author: String?

    var body: some View {
        VStack(alignment: .leading, spacing: DSSpacing.s) {
            Label {
                Text(categoryTitle)
                    .font(DSFont.label)
            } icon: {
                Image(systemName: style.systemImage)
                    .accessibilityHidden(true)
            }
            .foregroundStyle(style.color)

            Text(text)
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextPrimary)
                .dsBodyText()

            if let author, !author.isEmpty {
                Text(author)
                    .font(DSFont.footnote)
                    .foregroundStyle(Color.dsTextSecondary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .dsCard(fill: style.softColor, stroke: style.color.opacity(0.18))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Support card

/// Shown at the top of the results when the risk is elevated. Warm and visible, never alarming.
struct SupportCard: View {
    let title: String
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: DSSpacing.s) {
            Image(systemName: "heart.circle.fill")
                .font(.system(.title2))
                .foregroundStyle(Color.dsCare)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text(title)
                    .font(DSFont.heading)
                    .foregroundStyle(Color.dsTextPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(message)
                    .font(DSFont.body)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsBodyText()
            }
        }
        .dsCard(fill: .dsCareSoft, stroke: Color.dsCare.opacity(0.35))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Banner

/// A short inline notice. There is no error style on purpose: problems are explained calmly.
struct Banner: View {
    enum Kind {
        /// Neutral information, e.g. "the service is waking up".
        case info
        /// Something the user can fix, e.g. text in another language.
        case notice
    }

    let kind: Kind
    let text: String

    private var color: Color { kind == .info ? .dsSky : .dsGentle }
    private var softColor: Color { kind == .info ? .dsSkySoft : .dsGentleSoft }
    private var systemImage: String { kind == .info ? "info.circle" : "lightbulb" }

    var body: some View {
        HStack(alignment: .top, spacing: DSSpacing.xs) {
            Image(systemName: systemImage)
                .foregroundStyle(color)
                .accessibilityHidden(true)
            Text(text)
                .font(DSFont.callout)
                .foregroundStyle(Color.dsTextPrimary)
                .dsBodyText()
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(DSSpacing.s)
        .background(softColor, in: RoundedRectangle(cornerRadius: DSRadius.small, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

#if DEBUG
#Preview("Result components") {
    ScrollView {
        VStack(spacing: DSSpacing.m) {
            SupportCard(title: "Bununla tek başınıza baş etmek zorunda değilsiniz", message: "Güvendiğiniz biriyle konuşmak iyi gelebilir.")
            MessageCard(style: .validation, categoryTitle: "Duygularınız geçerli", text: "Hissettikleriniz anlaşılır.", author: nil)
            DimensionRow(dimension: .positiveAffect, level: .high, title: "Neşe ve enerji", message: "Paylaştıklarınızda gerçek bir neşe var gibi görünüyor.")
                .dsCard()
            Banner(kind: .info, text: "Analiz servisi uyanıyor.")
            Banner(kind: .notice, text: "Lütfen Türkçe yazın.")
        }
        .padding()
    }
    .background(AppBackground())
}
#endif
