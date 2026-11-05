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

/// A motivational message from the pipeline as a large slide, tinted by its category.
/// The category sits in a pill at the top, the message is set large, and the category symbol is repeated
/// as a faint watermark in the bottom corner.
struct MessageCard: View {
    /// The smallest height of a slide. Longer messages and larger text sizes make it taller.
    static let minHeight: CGFloat = 380

    let style: MessageCategoryStyle
    let categoryTitle: String
    let text: String
    let author: String?

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.slide, style: .continuous)

        VStack(alignment: .leading, spacing: DSSpacing.l) {
            categoryPill

            Spacer(minLength: 0)

            VStack(alignment: .leading, spacing: DSSpacing.s) {
                Image(systemName: "quote.opening")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(style.color.opacity(0.55))
                    .accessibilityHidden(true)

                Text(text)
                    .font(DSFont.quote)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsBodyText()

                if let author, !author.isEmpty {
                    Text(verbatim: "— \(author)")
                        .font(DSFont.callout.weight(.medium))
                        .foregroundStyle(Color.dsTextSecondary)
                        .padding(.top, DSSpacing.xxs)
                }
            }

            Spacer(minLength: DSSpacing.xl)
        }
        .padding(DSSpacing.l)
        .frame(maxWidth: .infinity, minHeight: Self.minHeight, maxHeight: .infinity, alignment: .topLeading)
        .background(alignment: .bottomTrailing) {
            Image(systemName: style.systemImage)
                .font(.system(size: 150, weight: .regular))
                .foregroundStyle(style.color.opacity(0.08))
                .offset(x: 28, y: 28)
                .accessibilityHidden(true)
        }
        .background(
            LinearGradient(colors: [style.softColor, Color.dsSurface], startPoint: .top, endPoint: .bottom),
            in: shape
        )
        .clipShape(shape)
        .overlay(shape.strokeBorder(style.color.opacity(0.2), lineWidth: 1))
        // One shadow for the whole slide, not one per layer (the watermark would cast its own).
        .compositingGroup()
        .shadow(color: .black.opacity(0.08), radius: 20, y: 10)
        .accessibilityElement(children: .combine)
    }

    private var categoryPill: some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: style.systemImage)
                .font(.system(.subheadline, weight: .semibold))
                .accessibilityHidden(true)
            Text(categoryTitle)
                .font(DSFont.label)
                .dsTitleText()
        }
        .foregroundStyle(style.color)
        .padding(.horizontal, DSSpacing.s)
        .padding(.vertical, DSSpacing.xs)
        .background(Color.dsSurface, in: Capsule())
        .overlay(Capsule().strokeBorder(style.color.opacity(0.2), lineWidth: 1))
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
        // Every slide gets the height of the tallest one, so the deck does not jump between messages
        // and the card behind always peeks out below the top one.
        ZStack {
            ForEach(messages.indices, id: \.self) { position in
                card(messages[position])
            }
        }
        .hidden()
        .accessibilityHidden(true)
        .overlay {
            ZStack {
                ForEach(Array(messages.indices.filter { $0 >= index && $0 <= index + 2 }.reversed()), id: \.self) { position in
                    let depth = position - index
                    let isTop = depth == 0
                    card(messages[position])
                        .overlay(alignment: .top) {
                            if isTop { stamp }
                        }
                        .scaleEffect(1 - CGFloat(depth) * 0.05, anchor: .bottom)
                        .offset(x: isTop ? offset : 0, y: CGFloat(depth) * DSSpacing.s)
                        .rotationEffect(.degrees(isTop && !reduceMotion ? Double(offset / 20) : 0), anchor: .bottom)
                        .opacity(isTop ? (isLeaving ? 0 : 1) : 1 - Double(depth) * 0.3)
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
        }
        .padding(.bottom, DSSpacing.l)
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
            RateButton(systemImage: "hand.thumbsdown.fill", isProminent: false, label: strings.dislike) { rate(liked: false) }
            Spacer()
            progressDots
            Spacer()
            RateButton(systemImage: "hand.thumbsup.fill", isProminent: true, label: strings.like) { rate(liked: true) }
        }
        .padding(.horizontal, DSSpacing.m)
        .environment(\.layoutDirection, .leftToRight)
    }

    /// One dot per message; the current one is a wider capsule. VoiceOver reads the card itself instead.
    private var progressDots: some View {
        HStack(spacing: 6) {
            ForEach(messages.indices, id: \.self) { position in
                Capsule()
                    .fill(position == index ? Color.dsBrand : position < index ? Color.dsBrand.opacity(0.35) : Color.dsStroke)
                    .frame(width: position == index ? 22 : 8, height: 8)
            }
        }
        .animation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion), value: index)
        .accessibilityHidden(true)
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

/// A large round like or dislike button under the message deck.
private struct RateButton: View {
    static let size: CGFloat = 64

    let systemImage: String
    /// The like button is filled with the brand colour; the dislike button stays quiet.
    let isProminent: Bool
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(.title2, weight: .semibold))
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .foregroundStyle(isProminent ? Color.dsOnBrand : Color.dsTextSecondary)
                .frame(width: Self.size, height: Self.size)
                .background(isProminent ? Color.dsBrand : Color.dsSurface, in: Circle())
                .overlay(Circle().strokeBorder(Color.dsStroke, lineWidth: isProminent ? 0 : 1))
                .shadow(color: .black.opacity(0.06), radius: 10, y: 4)
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
