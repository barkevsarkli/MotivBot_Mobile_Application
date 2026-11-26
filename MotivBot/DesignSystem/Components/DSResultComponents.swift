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
/// as a faint watermark in the bottom corner. Only a message with an author is styled as a quote
/// (opening quote mark and "— Author"); without one it is plain text, so it never reads as an uncredited quote.
struct MessageCard: View {
    /// The smallest height of a slide. Longer messages and larger text sizes make it taller.
    static let minHeight: CGFloat = 380

    let style: MessageCategoryStyle
    let categoryTitle: String
    let text: String
    let author: String?

    /// The author to credit, or nil when the pipeline sent none (or only whitespace).
    private var attribution: String? {
        guard let name = author?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else { return nil }
        return name
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.slide, style: .continuous)

        VStack(alignment: .leading, spacing: DSSpacing.l) {
            categoryPill

            Spacer(minLength: 0)

            VStack(alignment: .leading, spacing: DSSpacing.s) {
                if attribution != nil {
                    Image(systemName: "quote.opening")
                        .font(.system(size: 30, weight: .bold))
                        .foregroundStyle(style.color.opacity(0.55))
                        .accessibilityHidden(true)
                }

                Text(text)
                    .font(DSFont.quote)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsBodyText()

                if let attribution {
                    Text(verbatim: "— \(attribution)")
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

/// The motivational messages as a stack of slides. Swiping the top card either way, or tapping the arrow,
/// moves on to the next message; nothing is rated here (the reflection below asks how the messages felt).
/// A swipe follows the finger; the arrow follows the reading direction, so it points left in Persian.
struct MessageDeck: View {
    struct Strings {
        /// How to move through the cards. Also the VoiceOver hint of the top card.
        let hint: String
        let next: String
        let doneTitle: String
        let doneBody: String
        let replay: String
        let categoryTitle: (MessageCategoryStyle) -> String
    }

    let messages: [PredictionResponse.Message]
    let strings: Strings

    @State private var index = 0
    @State private var offset: CGFloat = 0
    /// Locks a drag to horizontal or vertical on its first movement, so the page still scrolls over a card.
    @State private var isHorizontalDrag: Bool?
    @State private var isLeaving = false
    @Environment(\.layoutDirection) private var layoutDirection
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let threshold: CGFloat = 110
    private static let flyDistance: CGFloat = 600

    init(messages: [PredictionResponse.Message], strings: Strings) {
        self.messages = messages
        self.strings = strings
    }

    var body: some View {
        Group {
            if index < messages.count {
                VStack(spacing: DSSpacing.m) {
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
                        .scaleEffect(1 - CGFloat(depth) * 0.05, anchor: .bottom)
                        .offset(x: isTop ? offset : 0, y: CGFloat(depth) * DSSpacing.s)
                        .rotationEffect(.degrees(isTop && !reduceMotion ? Double(offset / 20) : 0), anchor: .bottom)
                        .opacity(isTop ? (isLeaving ? 0 : 1) : 1 - Double(depth) * 0.3)
                        .allowsHitTesting(isTop)
                        .accessibilityHidden(!isTop)
                        .accessibilityHint(isTop ? strings.hint : "")
                        .accessibilityAction(named: strings.next) { showNext() }
                        .simultaneousGesture(drag, including: isTop ? .all : .none)
                        // The card that flew off is already off screen, so it leaves without a transition.
                        .transition(.asymmetric(insertion: .opacity, removal: .identity))
                }
            }
        }
        .padding(.bottom, DSSpacing.l)
        // The drag offset is physical, also in Persian; the cards themselves keep the language's direction.
        .environment(\.layoutDirection, .leftToRight)
    }

    private func card(_ message: PredictionResponse.Message) -> some View {
        let style = MessageCategoryStyle(category: message.category)
        return MessageCard(style: style, categoryTitle: strings.categoryTitle(style), text: message.text, author: message.author)
            .environment(\.layoutDirection, layoutDirection)
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
                    advance(towards: distance > 0 ? 1 : -1)
                } else {
                    withAnimation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion)) { offset = 0 }
                }
            }
    }

    // MARK: Controls

    /// The progress dots in the middle and the arrow on the trailing side (right in Turkish, left in Persian).
    private var controls: some View {
        ZStack {
            progressDots
            HStack {
                Spacer()
                NextButton(label: strings.next) { showNext() }
            }
        }
        .padding(.horizontal, DSSpacing.xs)
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

    // MARK: Moving on

    /// The arrow and the VoiceOver action send the card off against the reading direction, like turning a page.
    private func showNext() {
        advance(towards: layoutDirection == .rightToLeft ? 1 : -1)
    }

    /// Flies the top card off to the right (`1`) or left (`-1`) and shows the next one.
    private func advance(towards direction: CGFloat) {
        guard index < messages.count, !isLeaving else { return }
        // With Reduce Motion the card fades out instead of flying off.
        withAnimation(reduceMotion ? .easeInOut(duration: 0.2) : .easeIn(duration: 0.25)) {
            isLeaving = true
            if !reduceMotion {
                offset = direction * Self.flyDistance
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

/// The large round arrow under the message deck that shows the next message.
private struct NextButton: View {
    static let size: CGFloat = 64

    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "arrow.forward")
                .font(.system(.title2, weight: .semibold))
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .foregroundStyle(Color.dsOnBrand)
                .frame(width: Self.size, height: Self.size)
                .background(Color.dsBrand, in: Circle())
                .shadow(color: .black.opacity(0.08), radius: 10, y: 4)
                .contentShape(Circle())
        }
        .buttonStyle(.dsPressable)
        .accessibilityLabel(label)
        .accessibilityShowsLargeContentViewer {
            Label(label, systemImage: "arrow.forward")
        }
    }
}

// MARK: - Swipe tip

/// The small pop-up that tells people they can swipe the cards. Shown once on its own, and again from the info button.
struct SwipeTip: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: DSSpacing.s) {
            Image(systemName: "hand.draw")
                .font(.system(.title3, weight: .semibold))
                .foregroundStyle(Color.dsBrand)
                .frame(width: 36, height: 36)
                .background(Color.dsBrandSoft, in: Circle())
                .accessibilityHidden(true)
            Text(text)
                .font(DSFont.callout)
                .foregroundStyle(Color.dsTextPrimary)
                .dsBodyText()
                .frame(maxWidth: 240, alignment: .leading)
        }
        .padding(DSSpacing.m)
    }
}

// MARK: - Reflection rating

/// One reflection topic with its five steps (much worse, a bit worse, the same, a bit better, much better)
/// as a segmented row. Labels wrap onto up to three lines; at accessibility text sizes the steps wrap into two rows.
struct ReflectionRatingRow: View {
    let title: String
    let systemImage: String
    let ratingTitle: (ReflectionRating) -> String
    let selection: ReflectionRating?
    let onSelect: (ReflectionRating) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let container = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        let columns = Array(
            repeating: GridItem(.flexible(), spacing: DSSpacing.xxs),
            count: dynamicTypeSize.isAccessibilitySize ? 2 : ReflectionRating.allCases.count
        )

        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Label {
                Text(title)
                    .font(DSFont.label)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsTitleText()
            } icon: {
                Image(systemName: systemImage)
                    .foregroundStyle(Color.dsBrand)
                    .accessibilityHidden(true)
            }
            .accessibilityAddTraits(.isHeader)

            LazyVGrid(columns: columns, spacing: DSSpacing.xxs) {
                ForEach(ReflectionRating.allCases) { rating in
                    segment(rating)
                }
            }
            .padding(DSSpacing.xxs)
            .background(Color.dsSurfaceMuted, in: container)
            .overlay(container.strokeBorder(Color.dsStroke, lineWidth: 1))
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(title)
    }

    private func segment(_ rating: ReflectionRating) -> some View {
        let isSelected = selection == rating
        let shape = RoundedRectangle(cornerRadius: DSRadius.small, style: .continuous)

        return Button {
            onSelect(rating)
        } label: {
            // Five steps share one row, so the label has almost no side padding. Long single words
            // (e.g. "Değişmedi") carry a soft hyphen in the strings file so they break cleanly on 375 pt screens.
            Text(ratingTitle(rating))
                .font(.system(.footnote, weight: .semibold))
                .foregroundStyle(isSelected ? Color.dsOnBrand : Color.dsTextPrimary)
                .multilineTextAlignment(.center)
                .lineLimit(3)
                .allowsTightening(true)
                .minimumScaleFactor(0.8)
                .padding(.horizontal, 2)
                .padding(.vertical, DSSpacing.xxs)
                .frame(maxWidth: .infinity, minHeight: DSSize.minTap)
                .background(isSelected ? Color.dsBrand : Color.clear, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.dsPressable)
        .animation(DSMotion.quick, value: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
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
            MessageCard(style: .validation, categoryTitle: "Duygularınız geçerli", text: "Hiçbir şey sonsuza kadar sürmez.", author: "Matt Haig")
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
                hint: "Diğer sözleri görmek için kartı sağa ya da sola kaydırın veya oka dokunun.",
                next: "Sonraki söz",
                doneTitle: "Tüm sözleri okudunuz",
                doneBody: "Aşağıda bu sözlerin size nasıl hissettirdiğini paylaşabilirsiniz.",
                replay: "Sözleri yeniden gör",
                categoryTitle: { _ in "Duygularınız geçerli" }
            )
        )
        .padding()
    }
    .background(AppBackground())
}

#Preview("Reflection rating") {
    let titles = ["Çok daha kötü", "Biraz daha kötü", "Aynı / Değişmedi", "Biraz daha iyi", "Çok daha iyi"]
    VStack(spacing: DSSpacing.m) {
        ReflectionRatingRow(title: "Öz saygı", systemImage: "person.crop.circle.badge.checkmark", ratingTitle: { titles[$0.rawValue - 1] }, selection: .same) { _ in }
        ReflectionRatingRow(title: "Stres", systemImage: "bolt.heart", ratingTitle: { titles[$0.rawValue - 1] }, selection: nil) { _ in }
    }
    .dsCard()
    .padding()
    .background(AppBackground())
}
#endif
