import SwiftUI

struct QuestionnaireView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection
    @FocusState private var isInputFocused: Bool

    /// Moves on after a single-choice answer; cancelled when the user acts first.
    @State private var advanceTask: Task<Void, Never>?
    /// Sets which way the question card slides.
    @State private var isNavigatingBack = false
    @State private var showsSaved = false
    @State private var savedTask: Task<Void, Never>?

    var body: some View {
        ScrollView {
            ScreenColumn(spacing: DSSpacing.l) {
                if let section = viewModel.currentSection {
                    sectionIntro(section)

                    Group {
                        if section.displayMode == .allAtOnce {
                            gridSection(section)
                        } else if let question = viewModel.currentQuestion {
                            questionBlock(question)
                        }
                    }
                    .id(questionKey)
                    .transition(questionTransition)
                }
            }
            .padding(.top, DSSpacing.xs)
            .padding(.bottom, DSSpacing.l)
        }
        .scrollDismissesKeyboard(.interactively)
        .simultaneousGesture(swipeBack)
        .safeAreaInset(edge: .top) { header }
        .safeAreaInset(edge: .bottom) { actionBar }
        .sensoryFeedback(.selection, trigger: viewModel.selectedOptionId) { _, newValue in newValue != nil }
        .sensoryFeedback(.impact(weight: .light), trigger: viewModel.currentSectionIndex)
        .onChange(of: viewModel.answers.count) { oldCount, newCount in
            if newCount > oldCount { flashSaved() }
        }
        .onDisappear {
            advanceTask?.cancel()
            savedTask?.cancel()
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(spacing: 0) {
            TopBar {
                if canGoBack {
                    GlassIconButton(systemImage: "chevron.backward", accessibilityLabel: localization.localizedString("previous_button")) {
                        goBack()
                    }
                    .transition(.opacity)
                }
            }
            .animation(DSMotion.standard, value: canGoBack)

            ScreenColumn(spacing: DSSpacing.xs) {
                HStack(alignment: .firstTextBaseline) {
                    Text(localization.localizedString(
                        "section_progress",
                        localization.number(viewModel.currentSectionIndex + 1),
                        localization.number(viewModel.sections.count)
                    ))
                    .font(DSFont.label)
                    .foregroundStyle(Color.dsBrand)

                    Spacer(minLength: DSSpacing.xs)

                    if showsSaved {
                        Label(localization.localizedString("progress_saved"), systemImage: "checkmark.circle")
                            .font(DSFont.caption)
                            .foregroundStyle(Color.dsTextSecondary)
                            .transition(.opacity)
                    }
                }

                SegmentedProgressBar(
                    weights: viewModel.sections.map(\.questions.count),
                    fractions: sectionFractions,
                    accessibilityLabel: localization.localizedString("progress_label"),
                    accessibilityValue: localization.localizedString(
                        "overall_progress_value",
                        localization.number(viewModel.answeredQuestions),
                        localization.number(viewModel.totalQuestions)
                    )
                )
            }
            .padding(.bottom, DSSpacing.s)
        }
    }

    private var sectionFractions: [Double] {
        viewModel.sections.map { section in
            let answered = viewModel.answers.filter { $0.sectionId == section.id }.count
            return Double(answered) / Double(max(viewModel.shownQuestionCount(in: section), 1))
        }
    }

    // MARK: - Section intro

    /// The full title and description on the first question of a section, the title alone afterwards.
    @ViewBuilder
    private func sectionIntro(_ section: TestSection) -> some View {
        let isFirstQuestion = viewModel.currentQuestionIndex == 0 || section.displayMode == .allAtOnce
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text(localization.localizedString(section.titleKey))
                .font(isFirstQuestion ? DSFont.title : DSFont.label)
                .foregroundStyle(isFirstQuestion ? Color.dsTextPrimary : Color.dsTextSecondary)
                .dsTitleText()
                .accessibilityAddTraits(.isHeader)

            if isFirstQuestion {
                Text(localization.localizedString(section.descriptionKey))
                    .font(DSFont.callout)
                    .foregroundStyle(Color.dsTextSecondary)
                    .dsBodyText()
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .animation(motion, value: isFirstQuestion)
    }

    // MARK: - Question

    private func questionBlock(_ question: Question) -> some View {
        VStack(alignment: .leading, spacing: DSSpacing.m) {
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text(localization.localizedString(
                    "question_progress",
                    localization.number(viewModel.currentQuestionIndex + 1),
                    localization.number(viewModel.currentSection.map(viewModel.shownQuestionCount) ?? 0)
                ))
                .font(DSFont.caption)
                .foregroundStyle(Color.dsTextSecondary)

                Text(localization.localizedString(question.textKey))
                    .font(DSFont.question)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsBodyText()
                    .accessibilityAddTraits(.isHeader)
            }

            if let imageName = question.imageName {
                Image(imageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: DSRadius.small, style: .continuous))
                    .accessibilityHidden(true)
            }

            switch question.type {
            case .singleChoice:
                VStack(spacing: DSSpacing.xs) {
                    ForEach(question.options ?? []) { option in
                        AnswerOptionRow(
                            title: localization.localizedString(option.textKey),
                            isSelected: viewModel.selectedOptionId == option.id
                        ) {
                            choose(option)
                        }
                    }
                }
            case .scale(let range):
                scaleInput(range: range)
            case .freeText:
                freeTextInput(isOptional: question.isOptional)
            case .integer(let range):
                integerInput(range: range)
            }
        }
    }

    // MARK: - Inputs

    private func scaleInput(range: ClosedRange<Int>) -> some View {
        @Bindable var viewModel = viewModel
        return VStack(spacing: DSSpacing.m) {
            Text(localization.number(viewModel.scaleValue))
                .font(DSFont.display)
                .foregroundStyle(Color.dsBrand)
                .contentTransition(.numericText())
                .animation(DSMotion.quick, value: viewModel.scaleValue)
                .accessibilityHidden(true)

            HStack(spacing: DSSpacing.s) {
                stepButton("minus", enabled: viewModel.scaleValue > range.lowerBound) {
                    viewModel.scaleValue -= 1
                }
                Slider(
                    value: Binding(
                        get: { Double(viewModel.scaleValue) },
                        set: { viewModel.scaleValue = Int($0.rounded()) }
                    ),
                    in: Double(range.lowerBound)...Double(range.upperBound),
                    step: 1
                ) {
                    Text(localization.localizedString("question_label"))
                } minimumValueLabel: {
                    Text(localization.number(range.lowerBound))
                } maximumValueLabel: {
                    Text(localization.number(range.upperBound))
                }
                .tint(Color.dsBrand)
                .accessibilityValue(localization.number(viewModel.scaleValue))
                stepButton("plus", enabled: viewModel.scaleValue < range.upperBound) {
                    viewModel.scaleValue += 1
                }
            }
            .font(DSFont.footnote)
            .foregroundStyle(Color.dsTextSecondary)
        }
        .dsCard()
        .sensoryFeedback(.selection, trigger: viewModel.scaleValue)
    }

    private func stepButton(_ systemImage: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(.body, weight: .semibold))
                .frame(width: DSSize.minTap, height: DSSize.minTap)
                .background(Color.dsBrandSoft, in: Circle())
                .foregroundStyle(Color.dsBrand)
        }
        .buttonStyle(.dsPressable)
        .disabled(!enabled)
        .accessibilityHidden(true)
    }

    private func freeTextInput(isOptional: Bool) -> some View {
        @Bindable var viewModel = viewModel
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        return VStack(alignment: .leading, spacing: DSSpacing.xs) {
            if isOptional {
                Text(localization.localizedString("question_optional"))
                    .font(DSFont.caption)
                    .foregroundStyle(Color.dsTextSecondary)
            }
            TextEditor(text: $viewModel.freeTextAnswer)
                .focused($isInputFocused)
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextPrimary)
                .scrollContentBackground(.hidden)
                .frame(minHeight: 120)
                .padding(DSSpacing.xs)
                .background(Color.dsSurface, in: shape)
                .overlay(alignment: .topLeading) {
                    if viewModel.freeTextAnswer.isEmpty {
                        Text(localization.localizedString("free_text_placeholder"))
                            .font(DSFont.body)
                            .foregroundStyle(Color.dsTextTertiary)
                            .padding(.horizontal, DSSpacing.xs + 5)
                            .padding(.vertical, DSSpacing.xs + 8)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
                .overlay(shape.strokeBorder(isInputFocused ? Color.dsBrand : Color.dsStroke, lineWidth: isInputFocused ? 1.5 : 1))
                .animation(DSMotion.quick, value: isInputFocused)
        }
    }

    private func integerInput(range: ClosedRange<Int>) -> some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        let warning = integerWarning(range: range)

        return VStack(alignment: .leading, spacing: DSSpacing.s) {
            TextField(
                localization.localizedString("free_text_placeholder"),
                text: Binding(
                    get: { viewModel.freeTextAnswer },
                    set: { viewModel.freeTextAnswer = String($0.prefix(3)) }
                )
            )
            .keyboardType(.numberPad)
            .focused($isInputFocused)
            .font(DSFont.title)
            .foregroundStyle(Color.dsTextPrimary)
            .padding(.horizontal, DSSpacing.m)
            .frame(minHeight: DSSize.answerOptionMinHeight)
            .background(Color.dsSurface, in: shape)
            .overlay(shape.strokeBorder(borderColor(focused: isInputFocused, hasWarning: warning != nil), lineWidth: isInputFocused ? 1.5 : 1))
            .accessibilityLabel(localization.localizedString(viewModel.currentQuestion?.textKey ?? "question_label"))

            if let warning {
                Banner(kind: .notice, text: warning)
                    .transition(.opacity)
            }
        }
        .animation(DSMotion.quick, value: warning)
        .onAppear { isInputFocused = true }
    }

    private func borderColor(focused: Bool, hasWarning: Bool) -> Color {
        if hasWarning { return .dsGentle }
        return focused ? .dsBrand : .dsStroke
    }

    /// The message under a number answer, or nil when it is empty or valid.
    /// An age under 18 gets its own message; a single digit waits until the user stops typing.
    private func integerWarning(range: ClosedRange<Int>) -> String? {
        let text = viewModel.freeTextAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, viewModel.integerAnswer(in: range) == nil else { return nil }
        if range == PredictionRequestBuilder.ageRange,
           let value = Self.wholeNumber(text), value < range.lowerBound {
            return text.count >= 2 || !isInputFocused ? localization.localizedString("age_under_18_warning") : nil
        }
        return localization.localizedString("integer_invalid_hint", range.lowerBound, range.upperBound)
    }

    /// Reads any Unicode digits, e.g. Persian "۱۶". Display only; the view model validates the answer.
    private static func wholeNumber(_ text: String) -> Int? {
        var value = 0
        for character in text {
            guard let digit = character.wholeNumberValue, character.isNumber else { return nil }
            value = value * 10 + digit
        }
        return value
    }

    // MARK: - Grid section

    /// Each item gets its own card with wrapping answer chips; at large text sizes the chips stack.
    private func gridSection(_ section: TestSection) -> some View {
        VStack(spacing: DSSpacing.s) {
            ForEach(section.questions) { question in
                VStack(alignment: .leading, spacing: DSSpacing.s) {
                    Text(localization.localizedString(question.textKey))
                        .font(DSFont.heading)
                        .foregroundStyle(Color.dsTextPrimary)
                        .dsTitleText()
                    ChipGroup {
                        ForEach(question.options ?? []) { option in
                            SelectableChip(
                                title: localization.localizedString(option.textKey),
                                isSelected: viewModel.gridAnswers[question.id] == option.id
                            ) {
                                viewModel.selectGridOption(questionId: question.id, optionId: option.id)
                            }
                        }
                    }
                }
                .dsCard(padding: DSSpacing.m)
                .accessibilityElement(children: .contain)
            }
        }
        .sensoryFeedback(.selection, trigger: viewModel.gridAnswers)
    }

    // MARK: - Actions

    private var actionBar: some View {
        BottomActionBar {
            Button(action: advance) {
                DSButtonLabel(
                    title: localization.localizedString(viewModel.isLastQuestion ? "finish_button" : "next_button"),
                    systemImage: viewModel.isLastQuestion ? "checkmark" : "arrow.forward"
                )
            }
            .buttonStyle(.dsPrimary)
            .disabled(!viewModel.canProceedQuestion)
        }
    }

    private var canGoBack: Bool {
        viewModel.currentSectionIndex > 0 || viewModel.currentQuestionIndex > 0
    }

    private var questionKey: String {
        "\(viewModel.currentSectionIndex)-\(viewModel.currentQuestionIndex)"
    }

    private var motion: Animation {
        DSMotion.animation(DSMotion.standard, reduceMotion: reduceMotion)
    }

    private var questionTransition: AnyTransition {
        if reduceMotion { return .opacity }
        let forward = AnyTransition.asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .move(edge: .leading).combined(with: .opacity)
        )
        let backward = AnyTransition.asymmetric(
            insertion: .move(edge: .leading).combined(with: .opacity),
            removal: .move(edge: .trailing).combined(with: .opacity)
        )
        return isNavigatingBack ? backward : forward
    }

    /// Selects the answer and, unless it is the last question, moves on after a short pause.
    private func choose(_ option: AnswerOption) {
        viewModel.selectOption(option.id)
        advanceTask?.cancel()
        guard !viewModel.isLastQuestion else { return }
        let key = questionKey
        advanceTask = Task {
            try? await Task.sleep(for: DSMotion.autoAdvanceDelay)
            guard !Task.isCancelled, key == questionKey, viewModel.canProceedQuestion else { return }
            advance()
        }
    }

    private func advance() {
        advanceTask?.cancel()
        isInputFocused = false
        isNavigatingBack = false
        viewModel.nextQuestion()
    }

    private func goBack() {
        guard canGoBack else { return }
        advanceTask?.cancel()
        isInputFocused = false
        isNavigatingBack = true
        let earlierAnswers = viewModel.answers
        withAnimation(motion) {
            viewModel.previousQuestion()
        }
        restoreSelection(from: earlierAnswers)
    }

    /// `previousQuestion()` clears the selection; show the answer the user gave before so going back is cheap.
    private func restoreSelection(from answers: [UserAnswer]) {
        guard let section = viewModel.currentSection else { return }
        if section.displayMode == .allAtOnce {
            for question in section.questions {
                if let answer = answers.first(where: { $0.questionId == question.id }),
                   case .option(let option) = answer.answerValue {
                    viewModel.gridAnswers[question.id] = option.id
                }
            }
            return
        }
        guard let question = viewModel.currentQuestion,
              let answer = answers.last(where: { $0.sectionId == section.id && $0.questionId == question.id }) else { return }
        switch answer.answerValue {
        case .option(let option): viewModel.selectedOptionId = option.id
        case .scale(let value): viewModel.scaleValue = value
        case .text(let text): viewModel.freeTextAnswer = text
        }
    }

    /// Swiping towards the trailing edge goes back, like the system back gesture (mirrored in Persian).
    private var swipeBack: some Gesture {
        DragGesture(minimumDistance: 24, coordinateSpace: .global)
            .onEnded { value in
                let horizontal = value.translation.width
                guard abs(horizontal) > 80, abs(value.translation.height) < 60 else { return }
                let towardsTrailing = layoutDirection == .rightToLeft ? horizontal < 0 : horizontal > 0
                if towardsTrailing { goBack() }
            }
    }

    private func flashSaved() {
        savedTask?.cancel()
        savedTask = Task {
            // Saving is debounced in the view model; show the note once it has had time to run.
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled else { return }
            withAnimation(DSMotion.gentle) { showsSaved = true }
            try? await Task.sleep(for: .seconds(1.8))
            guard !Task.isCancelled else { return }
            withAnimation(DSMotion.gentle) { showsSaved = false }
        }
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost(.questionnaire(answered: 8)) { QuestionnaireView() }
}

#Preview("Dark") {
    PreviewHost(.questionnaire(answered: 6)) { QuestionnaireView() }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL)") {
    PreviewHost(.questionnaire(answered: 0), language: .persian) { QuestionnaireView() }
}
#endif
