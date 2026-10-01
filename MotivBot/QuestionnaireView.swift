import SwiftUI

struct QuestionnaireView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @State private var showContent = false
    @FocusState private var isInputFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            Spacer().frame(height: 70)
            
            // Progress bar
            progressSection
            
            ScrollView {
                VStack(spacing: 24) {
                    // Section header
                    sectionHeader
                    
                    if let section = viewModel.currentSection {
                        if section.displayMode == .allAtOnce {
                            // Grid mode: show all questions on one page
                            gridSection(section)
                        } else {
                            // Sequential mode: one question at a time
                            if let question = viewModel.currentQuestion {
                                questionCard(question)
                                    .id("\(viewModel.currentSectionIndex)-\(viewModel.currentQuestionIndex)")
                                    .transition(.asymmetric(
                                        insertion: .move(edge: .trailing).combined(with: .opacity),
                                        removal: .move(edge: .leading).combined(with: .opacity)
                                    ))
                            }
                        }
                    }
                    
                    // Navigation
                    navigationButtons
                    
                    Spacer().frame(height: 40)
                }
                .padding(.horizontal, 20)
            }
        }
        .animation(.easeInOut(duration: 0.35), value: viewModel.currentQuestionIndex)
        .animation(.easeInOut(duration: 0.35), value: viewModel.currentSectionIndex)
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                showContent = true
            }
        }
    }
    
    // MARK: - Progress Section
    
    private var progressSection: some View {
        VStack(spacing: 8) {
            HStack {
                Text(localization.localizedString("progress_label"))
                    .font(.system(size: 13, weight: .medium, design: .rounded))
                    .foregroundColor(.secondary)
                Spacer()
                Text("\(viewModel.answeredQuestions)/\(viewModel.totalQuestions)")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
            }
            
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.gray.opacity(0.15))
                        .frame(height: 8)
                    
                    RoundedRectangle(cornerRadius: 6)
                        .fill(
                            LinearGradient(
                                colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geometry.size.width * viewModel.overallProgress, height: 8)
                        .animation(.spring(response: 0.5, dampingFraction: 0.7), value: viewModel.overallProgress)
                }
            }
            .frame(height: 8)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .opacity(showContent ? 1 : 0)
    }
    
    // MARK: - Section Header
    
    private var sectionHeader: some View {
        VStack(spacing: 6) {
            if let section = viewModel.currentSection {
                HStack {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(
                            LinearGradient(
                                colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: 4, height: 28)
                    
                    Text(localization.localizedString(section.titleKey))
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                    
                    Spacer()
                }
                
                HStack {
                    Text(localization.localizedString(section.descriptionKey))
                        .font(.system(size: 14, design: .rounded))
                        .foregroundColor(.secondary)
                    Spacer()
                }
            }
        }
        .opacity(showContent ? 1 : 0)
    }
    
    // MARK: - Grid Section (allAtOnce)
    
    private func gridSection(_ section: TestSection) -> some View {
        VStack(spacing: 12) {
            // Column headers
            if let firstQ = section.questions.first, let opts = firstQ.options {
                HStack(spacing: 0) {
                    Text("")
                        .frame(width: 100, alignment: .leading)
                    ForEach(opts) { opt in
                        Text(localization.localizedString(opt.textKey))
                            .font(.system(size: 9, weight: .medium, design: .rounded))
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                            .minimumScaleFactor(0.7)
                    }
                }
                .padding(.horizontal, 12)
            }
            
            // Question rows
            ForEach(section.questions) { question in
                gridRow(question)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(.ultraThinMaterial)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.gray.opacity(0.1), lineWidth: 1)
        )
    }
    
    private func gridRow(_ question: Question) -> some View {
        HStack(spacing: 0) {
            Text(localization.localizedString(question.textKey))
                .font(.system(size: 13, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
                .frame(width: 100, alignment: .leading)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
            
            if let opts = question.options {
                ForEach(opts) { opt in
                    Button(action: {
                        viewModel.selectGridOption(questionId: question.id, optionId: opt.id)
                    }) {
                        ZStack {
                            Circle()
                                .stroke(
                                    viewModel.gridAnswers[question.id] == opt.id
                                        ? Color(red: 0.55, green: 0.48, blue: 0.85)
                                        : Color.gray.opacity(0.3),
                                    lineWidth: 2
                                )
                                .frame(width: 22, height: 22)
                            
                            if viewModel.gridAnswers[question.id] == opt.id {
                                Circle()
                                    .fill(
                                        LinearGradient(
                                            colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .frame(width: 12, height: 12)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 12)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(
                    viewModel.gridAnswers[question.id] != nil
                        ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.04)
                        : Color.clear
                )
        )
    }
    
    // MARK: - Question Card (sequential)
    
    private func questionCard(_ question: Question) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            // Question number badge
            HStack {
                Text("\(localization.localizedString("question_label")) \(viewModel.currentQuestionIndex + 1)")
                    .font(.system(size: 12, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(
                        Capsule()
                            .fill(
                                LinearGradient(
                                    colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                    )
                Spacer()
            }
            
            // Question text
            Text(localization.localizedString(question.textKey))
                .font(.system(size: 17, weight: .medium, design: .rounded))
                .foregroundColor(.primary)
                .fixedSize(horizontal: false, vertical: true)
            
            // SAM image if present
            if let imgName = question.imageName {
                Image(imgName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity)
                    .cornerRadius(12)
                    .padding(.vertical, 4)
            }
            
            // Options
            switch question.type {
            case .singleChoice:
                if let options = question.options {
                    VStack(spacing: 10) {
                        ForEach(options) { option in
                            optionRow(option)
                        }
                    }
                }
            case .scale(let range):
                scaleInput(range: range)
            case .freeText:
                freeTextInput
            case .integer(let range):
                integerInput(range: range)
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
    }
    
    // MARK: - Inputs
    
    private func scaleInput(range: ClosedRange<Int>) -> some View {
        VStack(spacing: 20) {
            Text("\(Int(viewModel.scaleValue))")
                .font(.system(size: 32, weight: .bold, design: .rounded))
                .foregroundColor(Color(red: 0.55, green: 0.48, blue: 0.85))
            
            Slider(value: Binding(
                get: { Double(viewModel.scaleValue) },
                set: { viewModel.scaleValue = Int($0) }
            ), in: Double(range.lowerBound)...Double(range.upperBound), step: 1)
            .accentColor(Color(red: 0.55, green: 0.48, blue: 0.85))
            
            HStack {
                Text("\(range.lowerBound)")
                Spacer()
                Text("\(range.upperBound)")
            }
            .font(.caption)
            .foregroundColor(.secondary)
        }
        .padding()
    }
    
    private var freeTextInput: some View {
        TextEditor(text: Binding(
            get: { viewModel.freeTextAnswer },
            set: { viewModel.freeTextAnswer = $0 }
        ))
        .focused($isInputFocused)
        .font(.system(size: 16, design: .rounded))
        .frame(minHeight: 120)
        .padding(12)
        .background(Color.gray.opacity(0.05))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.gray.opacity(0.2), lineWidth: 1)
        )
    }
    
    private func integerInput(range: ClosedRange<Int>) -> some View {
        let text = viewModel.freeTextAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        let showsHint = !text.isEmpty && viewModel.integerAnswer(in: range) == nil

        return VStack(alignment: .leading, spacing: 8) {
            TextField("", text: Binding(
                get: { viewModel.freeTextAnswer },
                set: { viewModel.freeTextAnswer = String($0.prefix(3)) }
            ))
            .keyboardType(.numberPad)
            .focused($isInputFocused)
            .font(.system(size: 22, weight: .semibold, design: .rounded))
            .padding(14)
            .background(Color.gray.opacity(0.05))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(
                        showsHint ? Color.orange.opacity(0.6) : Color.gray.opacity(0.2),
                        lineWidth: 1
                    )
            )

            if showsHint {
                Text(String(format: localization.localizedString("integer_invalid_hint"), range.lowerBound, range.upperBound))
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(.orange)
                    .fixedSize(horizontal: false, vertical: true)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showsHint)
    }

    // MARK: - Option Row
    
    private func optionRow(_ option: AnswerOption) -> some View {
        Button(action: {
            viewModel.selectOption(option.id)
        }) {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .stroke(
                            viewModel.selectedOptionId == option.id
                                ? Color(red: 0.55, green: 0.48, blue: 0.85)
                                : Color.gray.opacity(0.3),
                            lineWidth: 2
                        )
                        .frame(width: 24, height: 24)
                    
                    if viewModel.selectedOptionId == option.id {
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 14, height: 14)
                            .transition(.scale.combined(with: .opacity))
                    }
                }
                .animation(.spring(response: 0.3, dampingFraction: 0.7), value: viewModel.selectedOptionId)
                
                Text(localization.localizedString(option.textKey))
                    .font(.system(size: 15, design: .rounded))
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.leading)
                
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 14)
                    .fill(
                        viewModel.selectedOptionId == option.id
                            ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.08)
                            : Color.clear
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(
                        viewModel.selectedOptionId == option.id
                            ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.3)
                            : Color.gray.opacity(0.12),
                        lineWidth: 1
                    )
            )
            // Make the whole row tappable, not only the text and the radio outline.
            .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
    
    // MARK: - Navigation Buttons
    
    private var navigationButtons: some View {
        HStack(spacing: 12) {
            if viewModel.currentSectionIndex > 0 || viewModel.currentQuestionIndex > 0 {
                Button(action: {
                    isInputFocused = false
                    viewModel.previousQuestion()
                }) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(Color(red: 0.55, green: 0.48, blue: 0.85))
                        .frame(width: 54, height: 54)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.1))
                        )
                }
            }
            
            Button(action: {
                // Dismiss the keyboard before the text field leaves the screen.
                isInputFocused = false
                viewModel.nextQuestion()
            }) {
                HStack(spacing: 8) {
                    Text(viewModel.isLastQuestion
                         ? localization.localizedString("finish_button")
                         : localization.localizedString("next_button"))
                        .font(.system(size: 17, weight: .semibold, design: .rounded))
                    Image(systemName: viewModel.isLastQuestion ? "checkmark" : "arrow.right")
                        .font(.system(size: 15, weight: .semibold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(
                            viewModel.canProceedQuestion
                                ? LinearGradient(
                                    colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                  )
                                : LinearGradient(
                                    colors: [.gray.opacity(0.4), .gray.opacity(0.3)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                  )
                        )
                )
                .shadow(
                    color: viewModel.canProceedQuestion
                        ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.4)
                        : .clear,
                    radius: 12, y: 6
                )
            }
            .disabled(!viewModel.canProceedQuestion)
        }
        .opacity(showContent ? 1 : 0)
    }
}

#Preview {
    QuestionnaireView()
        .environment(WellBeingViewModel())
        .environment(LocalizationManager())
}
