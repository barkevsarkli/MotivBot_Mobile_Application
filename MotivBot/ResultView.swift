import SwiftUI

struct ResultView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @State private var showQuote = false
    @State private var showReflection = false
    @State private var dragOffset: CGSize = .zero
    @State private var isFlyingAway = false
    @FocusState private var isTextFieldFocused: Bool

    private let swipeThreshold: CGFloat = 110
    private let cardHeight: CGFloat = 400

    /// Accent color per card position, so the deck reads as three distinct cards.
    private let cardAccents: [Color] = [
        Color(red: 0.55, green: 0.48, blue: 0.85),
        Color(red: 0.42, green: 0.68, blue: 0.84),
        Color(red: 0.85, green: 0.65, blue: 0.30)
    ]
    
    var body: some View {
        @Bindable var viewModel = viewModel
        
        ScrollView {
            VStack(spacing: 28) {
                Spacer().frame(height: 70)
                
                if !viewModel.isCompleted {
                    // MARK: - Motivational Card Deck
                    deckHeader

                    if viewModel.allCardsReviewed {
                        deckSummary

                        // MARK: - Reflection Section
                        reflectionSection

                        // Submit reflection button
                        submitButton
                    } else {
                        cardDeck
                        swipeControls
                    }
                } else {
                    // MARK: - Completion State
                    completionView
                }
                
                Spacer().frame(height: 40)
            }
            .padding(.horizontal, 20)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            withAnimation(.easeOut(duration: 0.7).delay(0.2)) {
                showQuote = true
            }
            withAnimation(.easeOut(duration: 0.7).delay(0.8)) {
                showReflection = true
            }
        }
        .onChange(of: viewModel.allCardsReviewed) { _, reviewed in
            // Share the CSV only once the user has been through the deck
            guard reviewed, viewModel.csvFileURL != nil else { return }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                viewModel.showShareSheet = true
            }
        }
        .sheet(isPresented: $viewModel.showShareSheet) {
            if let csvURL = viewModel.csvFileURL {
                ShareSheet(activityItems: [csvURL])
            }
        }
    }
    
    // MARK: - Deck Header

    private var deckHeader: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.system(size: 20))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color(red: 0.85, green: 0.65, blue: 0.30), Color(red: 0.90, green: 0.75, blue: 0.40)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Text(localization.localizedString("result_title"))
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
            }

            Text(localization.localizedString(viewModel.allCardsReviewed ? "cards_done_subtitle" : "cards_subtitle"))
                .font(.system(size: 14, design: .rounded))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            // Progress dots
            HStack(spacing: 6) {
                ForEach(viewModel.motivationCards.indices, id: \.self) { index in
                    Capsule()
                        .fill(
                            index < viewModel.currentCardIndex
                                ? accent(for: index)
                                : Color.gray.opacity(0.25)
                        )
                        .frame(width: index == viewModel.currentCardIndex ? 26 : 18, height: 5)
                        .animation(.spring(response: 0.35, dampingFraction: 0.8), value: viewModel.currentCardIndex)
                }
            }
            .padding(.top, 2)
        }
        .opacity(showQuote ? 1 : 0)
        .offset(y: showQuote ? 0 : 20)
    }

    // MARK: - Card Deck

    private var cardDeck: some View {
        ZStack {
            ForEach(Array(visibleCards.enumerated().reversed()), id: \.element.id) { depth, card in
                motivationCardView(card, depth: depth)
                    .zIndex(Double(visibleCards.count - depth))
            }
        }
        .frame(height: cardHeight)
        .opacity(showQuote ? 1 : 0)
        .offset(y: showQuote ? 0 : 30)
    }

    /// Top three remaining cards; the first one is interactive, the rest are stacked behind.
    private var visibleCards: [MotivationCard] {
        let cards = viewModel.motivationCards
        guard viewModel.currentCardIndex < cards.count else { return [] }
        let upperBound = min(cards.count, viewModel.currentCardIndex + 3)
        return Array(cards[viewModel.currentCardIndex..<upperBound])
    }

    private func accent(for index: Int) -> Color {
        cardAccents[index % cardAccents.count]
    }

    @ViewBuilder
    private func motivationCardView(_ card: MotivationCard, depth: Int) -> some View {
        let isTop = depth == 0
        let accentColor = accent(for: viewModel.currentCardIndex + depth)

        cardBody(card, accentColor: accentColor)
            .frame(height: cardHeight)
            .background(
                RoundedRectangle(cornerRadius: 28)
                    .fill(.ultraThinMaterial)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 28)
                    .stroke(
                        LinearGradient(
                            colors: [accentColor.opacity(0.45), accentColor.opacity(0.12), Color.clear],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1.5
                    )
            )
            .overlay(alignment: .top) {
                if isTop { swipeBadges }
            }
            .shadow(color: accentColor.opacity(isTop ? 0.20 : 0.06), radius: isTop ? 22 : 10, y: isTop ? 12 : 6)
            .scaleEffect(isTop ? 1.0 : 1.0 - CGFloat(depth) * 0.05)
            .offset(y: isTop ? 0 : CGFloat(depth) * 16)
            .offset(isTop ? dragOffset : .zero)
            .rotationEffect(.degrees(isTop ? Double(dragOffset.width / 22) : 0))
            .allowsHitTesting(isTop)
            .gesture(isTop ? dragGesture : nil)
    }

    private func cardBody(_ card: MotivationCard, accentColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            // Section badge — every card speaks to a different test section
            HStack(spacing: 6) {
                Image(systemName: "square.stack.3d.up.fill")
                    .font(.system(size: 11, weight: .semibold))
                Text(card.sectionTitle.isEmpty
                     ? localization.localizedString("card_section_generic")
                     : card.sectionTitle)
                    .font(.system(size: 12, weight: .semibold, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .foregroundColor(accentColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Capsule().fill(accentColor.opacity(0.12)))

            Image(systemName: "quote.opening")
                .font(.system(size: 22))
                .foregroundColor(accentColor.opacity(0.35))

            Text(card.quote)
                .font(.system(size: 20, weight: .medium, design: .serif))
                .foregroundColor(.primary)
                .lineSpacing(6)
                .fixedSize(horizontal: false, vertical: true)

            if let author = card.author, !author.isEmpty {
                Text("— \(author)")
                    .font(.system(size: 14, weight: .medium, design: .serif))
                    .foregroundColor(.secondary)
            }

            Divider().background(Color.gray.opacity(0.2))

            Text(card.message)
                .font(.system(size: 15, design: .rounded))
                .foregroundColor(.primary.opacity(0.8))
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)

            // Swipe hint
            HStack {
                Label(localization.localizedString("card_swipe_left_hint"), systemImage: "arrow.left")
                Spacer()
                Label(localization.localizedString("card_swipe_right_hint"), systemImage: "arrow.right")
                    .labelStyle(.titleAndIcon)
            }
            .font(.system(size: 11, weight: .medium, design: .rounded))
            .foregroundColor(.secondary.opacity(0.7))
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Swipe Badges

    private var swipeBadges: some View {
        HStack {
            badge(
                text: localization.localizedString("card_liked"),
                systemImage: "heart.fill",
                color: Color(red: 0.30, green: 0.78, blue: 0.55)
            )
            .opacity(dragOffset.width > 0 ? min(Double(dragOffset.width / swipeThreshold), 1) : 0)
            .rotationEffect(.degrees(-12))

            Spacer()

            badge(
                text: localization.localizedString("card_disliked"),
                systemImage: "xmark",
                color: Color(red: 0.90, green: 0.35, blue: 0.40)
            )
            .opacity(dragOffset.width < 0 ? min(Double(-dragOffset.width / swipeThreshold), 1) : 0)
            .rotationEffect(.degrees(12))
        }
        .padding(20)
    }

    private func badge(text: String, systemImage: String, color: Color) -> some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
                .font(.system(size: 13, weight: .bold))
            Text(text)
                .font(.system(size: 15, weight: .heavy, design: .rounded))
        }
        .foregroundColor(color)
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(color.opacity(0.12))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(color, lineWidth: 2.5)
        )
    }

    // MARK: - Drag Gesture

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                guard !isFlyingAway else { return }
                dragOffset = value.translation
            }
            .onEnded { value in
                guard !isFlyingAway else { return }
                if value.translation.width > swipeThreshold {
                    commitSwipe(.right)
                } else if value.translation.width < -swipeThreshold {
                    commitSwipe(.left)
                } else {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.7)) {
                        dragOffset = .zero
                    }
                }
            }
    }

    /// Flings the top card off screen, then records the rating and resets the offset.
    private func commitSwipe(_ direction: SwipeDirection) {
        isFlyingAway = true
        let endX: CGFloat = direction == .right ? 700 : -700

        withAnimation(.easeOut(duration: 0.28)) {
            dragOffset = CGSize(width: endX, height: dragOffset.height + 40)
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
            viewModel.rateCurrentCard(direction)
            dragOffset = .zero
            isFlyingAway = false
        }
    }

    // MARK: - Swipe Controls (tap alternative to swiping)

    private var swipeControls: some View {
        HStack(spacing: 40) {
            controlButton(
                systemImage: "xmark",
                color: Color(red: 0.90, green: 0.35, blue: 0.40),
                label: localization.localizedString("card_disliked")
            ) {
                commitSwipe(.left)
            }

            controlButton(
                systemImage: "heart.fill",
                color: Color(red: 0.30, green: 0.78, blue: 0.55),
                label: localization.localizedString("card_liked")
            ) {
                commitSwipe(.right)
            }
        }
        .opacity(showQuote ? 1 : 0)
    }

    private func controlButton(systemImage: String, color: Color, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(color)
                    .frame(width: 62, height: 62)
                    .background(
                        Circle()
                            .fill(.ultraThinMaterial)
                    )
                    .overlay(Circle().stroke(color.opacity(0.35), lineWidth: 1.5))
                    .shadow(color: color.opacity(0.25), radius: 10, y: 5)

                Text(label)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundColor(.secondary)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .disabled(isFlyingAway)
    }

    // MARK: - Deck Summary (after all three cards are rated)

    private var deckSummary: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(viewModel.motivationCards) { card in
                let liked = viewModel.rating(for: card) == true
                let accentColor = liked
                    ? Color(red: 0.30, green: 0.78, blue: 0.55)
                    : Color.gray

                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: liked ? "heart.fill" : "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(accentColor)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(accentColor.opacity(0.12)))

                    VStack(alignment: .leading, spacing: 4) {
                        if !card.sectionTitle.isEmpty {
                            Text(card.sectionTitle)
                                .font(.system(size: 11, weight: .semibold, design: .rounded))
                                .foregroundColor(.secondary)
                        }
                        Text(card.quote)
                            .font(.system(size: 15, design: .serif))
                            .foregroundColor(liked ? .primary : .secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Spacer(minLength: 0)
                }
                .opacity(liked ? 1 : 0.6)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 22)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(Color.gray.opacity(0.12), lineWidth: 1)
        )
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    // MARK: - Reflection Section
    
    private var reflectionSection: some View {
        @Bindable var viewModel = viewModel
        
        return VStack(alignment: .leading, spacing: 16) {
            Text(localization.localizedString("reflection_title"))
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundColor(.primary)
            
            Text(localization.localizedString("reflection_question"))
                .font(.system(size: 15, design: .rounded))
                .foregroundColor(.secondary)
            
            // Emoji feeling selector
            HStack(spacing: 12) {
                ForEach(Array(viewModel.feelingEmojis.enumerated()), id: \.offset) { index, feeling in
                    Button(action: {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            viewModel.selectedFeelingIndex = index
                        }
                    }) {
                        VStack(spacing: 6) {
                            Text(feeling.emoji)
                                .font(.system(size: viewModel.selectedFeelingIndex == index ? 36 : 28))
                                .scaleEffect(viewModel.selectedFeelingIndex == index ? 1.15 : 1.0)
                            
                            Text(localization.localizedString(feeling.labelKey))
                                .font(.system(size: 10, weight: .medium, design: .rounded))
                                .foregroundColor(viewModel.selectedFeelingIndex == index ? .primary : .secondary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(
                                    viewModel.selectedFeelingIndex == index
                                        ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.1)
                                        : Color.clear
                                )
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(
                                    viewModel.selectedFeelingIndex == index
                                        ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.4)
                                        : Color.gray.opacity(0.12),
                                    lineWidth: 1
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            
            // Optional text reflection
            VStack(alignment: .leading, spacing: 6) {
                Text(localization.localizedString("reflection_optional"))
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(.secondary)
                
                TextField(localization.localizedString("reflection_placeholder"), text: $viewModel.reflectionText, axis: .vertical)
                    .focused($isTextFieldFocused)
                    .lineLimit(3...6)
                    .padding(14)
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(.ultraThinMaterial)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(
                                isTextFieldFocused
                                    ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.5)
                                    : Color.gray.opacity(0.15),
                                lineWidth: 1
                            )
                    )
                    .font(.system(size: 15, design: .rounded))
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.gray.opacity(0.1), lineWidth: 1)
        )
        .opacity(showReflection ? 1 : 0)
        .offset(y: showReflection ? 0 : 30)
    }
    
    // MARK: - Submit Button
    
    private var submitButton: some View {
        Button(action: {
            isTextFieldFocused = false
            viewModel.submitReflection()
        }) {
            HStack(spacing: 8) {
                Text(localization.localizedString("submit_reflection"))
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                Image(systemName: "heart.fill")
                    .font(.system(size: 15))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        LinearGradient(
                            colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
            )
            .shadow(color: Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.4), radius: 12, y: 6)
        }
        .opacity(showReflection ? 1 : 0)
    }
    
    // MARK: - Completion View
    
    private var completionView: some View {
        VStack(spacing: 24) {
            Spacer().frame(height: 40)
            
            // Checkmark animation
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color(red: 0.30, green: 0.78, blue: 0.55), Color(red: 0.25, green: 0.65, blue: 0.50)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 90, height: 90)
                    .shadow(color: Color(red: 0.30, green: 0.78, blue: 0.55).opacity(0.4), radius: 16, y: 8)
                
                Image(systemName: "checkmark")
                    .font(.system(size: 40, weight: .bold))
                    .foregroundColor(.white)
            }
            
            Text(localization.localizedString("completion_title"))
                .font(.system(size: 26, weight: .bold, design: .rounded))
                .foregroundColor(.primary)
            
            Text(localization.localizedString("completion_message"))
                .font(.system(size: 16, design: .rounded))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
            
            // Restart button
            Button(action: {
                viewModel.restartAssessment()
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 15, weight: .semibold))
                    Text(localization.localizedString("restart_button"))
                        .font(.system(size: 16, weight: .semibold, design: .rounded))
                }
                .foregroundColor(Color(red: 0.55, green: 0.48, blue: 0.85))
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
                .background(
                    Capsule()
                        .stroke(Color(red: 0.55, green: 0.48, blue: 0.85), lineWidth: 2)
                )
            }
            .padding(.top, 8)
        }
        .transition(.scale.combined(with: .opacity))
    }
}

#Preview {
    ResultView()
        .environment(WellBeingViewModel())
        .environment(LocalizationManager())
}
