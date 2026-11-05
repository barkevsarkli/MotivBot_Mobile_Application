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
                    .dsTitleText()
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

// MARK: - Message deck

/// The motivational messages as a stack of cards. Swiping right means the message was liked, left that it was not.
/// The directions are physical, also in Persian. The buttons and the VoiceOver actions do the same as a swipe.
struct MessageDeck: View {
    struct Strings {
        let hint: String
        let like: String
        let dislike: String
        let doneTitle: String
        let doneBody: String
        let replay: String
        let categoryTitle: (MessageCategoryStyle) -> String
    }

    let messages: [PredictionResponse.Message]
    let strings: Strings
    let onRate: (PredictionResponse.Message, Bool) -> Void

    @State private var index: Int
    @State private var offset: CGFloat = 0
    /// Locks a drag to horizontal or vertical on its first movement, so the page still scrolls over a card.
    @State private var isHorizontalDrag: Bool?
    @State private var isLeaving = false
    @Environment(\.layoutDirection) private var layoutDirection
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let threshold: CGFloat = 110
    private static let flyDistance: CGFloat = 600

    /// `startIndex` lets a deck that is shown again continue after the messages already rated.
    init(messages: [PredictionResponse.Message], strings: Strings, startIndex: Int = 0, onRate: @escaping (PredictionResponse.Message, Bool) -> Void) {
        self.messages = messages
        self.strings = strings
        self.onRate = onRate
        _index = State(initialValue: min(startIndex, messages.count))
    }

    var body: some View {
        Group {
            if index < messages.count {
                VStack(spacing: DSSpacing.m) {
                    Text(strings.hint)
                        .font(DSFont.footnote)
                        .foregroundStyle(Color.dsTextSecondary)
                        .multilineTextAlignment(.center)
                        .dsBodyText()
                        .frame(maxWidth: .infinity)
                        .accessibilityHidden(true)
                    cards
                    controls
                }
            } else {
                done
                    .transition(.opacity)
            }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: index)
    }

    // MARK: Cards

    private var cards: some View {
        ZStack {
            ForEach(Array(messages.indices.filter { $0 == index || $0 == index + 1 }.reversed()), id: \.self) { position in
                let isTop = position == index
                card(messages[position])
                    .overlay(alignment: .top) {
                        if isTop { stamp }
                    }
                    .scaleEffect(isTop ? 1 : 0.95, anchor: .bottom)
                    .offset(x: isTop ? offset : 0, y: isTop ? 0 : DSSpacing.s)
                    .rotationEffect(.degrees(isTop && !reduceMotion ? Double(offset / 20) : 0), anchor: .bottom)
                    .opacity(isTop ? (isLeaving ? 0 : 1) : 0.6)
                    .allowsHitTesting(isTop)
                    .accessibilityHidden(!isTop)
                    .accessibilityHint(isTop ? strings.hint : "")
                    .accessibilityAction(named: strings.like) { rate(liked: true) }
                    .accessibilityAction(named: strings.dislike) { rate(liked: false) }
                    .simultaneousGesture(drag, including: isTop ? .all : .none)
                    // The card that flew off is already off screen, so it leaves without a transition.
                    .transition(.asymmetric(insertion: .opacity, removal: .identity))
            }
        }
        .padding(.bottom, DSSpacing.s)
        // Left and right stay physical in Persian; the cards themselves keep the language's direction.
        .environment(\.layoutDirection, .leftToRight)
    }

    private func card(_ message: PredictionResponse.Message) -> some View {
        let style = MessageCategoryStyle(category: message.category)
        return MessageCard(style: style, categoryTitle: strings.categoryTitle(style), text: message.text, author: message.author)
            .environment(\.layoutDirection, layoutDirection)
    }

    private var stamp: some View {
        let liked = offset > 0
        return Image(systemName: liked ? "hand.thumbsup.fill" : "hand.thumbsdown.fill")
            .font(.system(.title3, weight: .semibold))
            .foregroundStyle(liked ? Color.dsSteady : Color.dsTextSecondary)
            .frame(width: DSSize.minTap, height: DSSize.minTap)
            .background(Color.dsSurface, in: Circle())
            .overlay(Circle().strokeBorder(Color.dsStroke, lineWidth: 1))
            .offset(y: -DSSpacing.m)
            .opacity(min(abs(offset) / Self.threshold, 1))
            .accessibilityHidden(true)
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                guard !isLeaving else { return }
                if isHorizontalDrag == nil {
                    isHorizontalDrag = abs(value.translation.width) > abs(value.translation.height)
                }
                guard isHorizontalDrag == true else { return }
                offset = value.translation.width
            }
            .onEnded { value in
                defer { isHorizontalDrag = nil }
                guard isHorizontalDrag == true, !isLeaving else { return }
                let distance = abs(offset) > Self.threshold ? offset : value.predictedEndTranslation.width
                if abs(distance) > Self.threshold {
                    rate(liked: distance > 0)
                } else {
                    withAnimation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion)) { offset = 0 }
                }
            }
    }

    // MARK: Controls

    private var controls: some View {
        HStack {
            RateButton(systemImage: "hand.thumbsdown", tint: .dsTextSecondary, label: strings.dislike) { rate(liked: false) }
            Spacer()
            Text(verbatim: "\(index + 1) / \(messages.count)")
                .font(DSFont.footnote)
                .foregroundStyle(Color.dsTextSecondary)
                .accessibilityHidden(true)
            Spacer()
            RateButton(systemImage: "hand.thumbsup", tint: .dsBrand, label: strings.like) { rate(liked: true) }
        }
        .padding(.horizontal, DSSpacing.l)
        .environment(\.layoutDirection, .leftToRight)
    }

    private var done: some View {
        VStack(spacing: DSSpacing.s) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(.title2))
                .foregroundStyle(Color.dsSteady)
                .accessibilityHidden(true)
            Text(strings.doneTitle)
                .font(DSFont.heading)
                .foregroundStyle(Color.dsTextPrimary)
                .multilineTextAlignment(.center)
                .dsTitleText()
            Text(strings.doneBody)
                .font(DSFont.callout)
                .foregroundStyle(Color.dsTextSecondary)
                .multilineTextAlignment(.center)
                .dsBodyText()
            Button(strings.replay) {
                withAnimation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion)) { index = 0 }
            }
            .buttonStyle(.dsTertiary)
        }
        .frame(maxWidth: .infinity)
        .dsCard()
    }

    // MARK: Rating

    private func rate(liked: Bool) {
        guard index < messages.count, !isLeaving else { return }
        onRate(messages[index], liked)
        // With Reduce Motion the card fades out instead of flying off.
        withAnimation(reduceMotion ? .easeInOut(duration: 0.2) : .easeIn(duration: 0.25)) {
            isLeaving = true
            if !reduceMotion {
                offset = liked ? Self.flyDistance : -Self.flyDistance
            }
        } completion: {
            withAnimation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion)) {
                index += 1
                offset = 0
                isLeaving = false
            }
        }
    }
}

/// A round like or dislike button under the message deck.
private struct RateButton: View {
    let systemImage: String
    let tint: Color
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(.title3, weight: .semibold))
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .foregroundStyle(tint)
                .frame(width: DSSize.buttonMinHeight, height: DSSize.buttonMinHeight)
                .background(Color.dsSurface, in: Circle())
                .overlay(Circle().strokeBorder(Color.dsStroke, lineWidth: 1))
                .contentShape(Circle())
        }
        .buttonStyle(.dsPressable)
        .accessibilityLabel(label)
        .accessibilityShowsLargeContentViewer {
            Label(label, systemImage: systemImage)
        }
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
            MessageCard(style: .validation, categoryTitle: "Duygularınız geçerli", text: "Hissettikleriniz anlaşılır.", author: nil)
            Banner(kind: .info, text: "Analiz servisi uyanıyor.")
            Banner(kind: .notice, text: "Lütfen Türkçe yazın.")
        }
        .padding()
    }
    .background(AppBackground())
}

#Preview("Message deck") {
    ScrollView {
        MessageDeck(
            messages: PreviewFixtures.prediction.messages,
            strings: MessageDeck.Strings(
                hint: "Beğenmediğiniz sözü sola, beğendiğinizi sağa kaydırın.",
                like: "Beğendim",
                dislike: "Beğenmedim",
                doneTitle: "Tüm sözleri değerlendirdiniz",
                doneBody: "Geri bildiriminiz için teşekkürler.",
                replay: "Sözleri yeniden gör",
                categoryTitle: { _ in "Duygularınız geçerli" }
            )
        ) { _, _ in }
        .padding()
    }
    .background(AppBackground())
}
#endif
