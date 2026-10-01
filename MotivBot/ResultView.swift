import SwiftUI

struct ResultView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @State private var showContent = false
    @State private var showReflection = false
    @FocusState private var isTextFieldFocused: Bool

    private let brandPurple = Color(red: 0.55, green: 0.48, blue: 0.85)
    private let brandBlue = Color(red: 0.42, green: 0.68, blue: 0.84)
    
    var body: some View {
        @Bindable var viewModel = viewModel
        
        ScrollView {
            VStack(spacing: 24) {
                Spacer().frame(height: 70)
                
                if !viewModel.isCompleted {
                    header

                    if let prediction = viewModel.prediction {
                        // Support card first, so it is the first thing a user at risk sees.
                        if prediction.elevatedRisk {
                            supportCard
                        }
                        dimensionsCard(prediction)
                        disclaimer
                        shareButton
                    }

                    // MARK: - Reflection Section
                    reflectionSection

                    // Submit reflection button
                    submitButton
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
                showContent = true
            }
            withAnimation(.easeOut(duration: 0.7).delay(0.8)) {
                showReflection = true
            }
        }
        .sheet(isPresented: $viewModel.showShareSheet) {
            if let csvURL = viewModel.csvFileURL {
                ShareSheet(activityItems: [csvURL])
            }
        }
    }
    
    // MARK: - Header

    private var header: some View {
        VStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "leaf.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [brandPurple, brandBlue],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )

                Text(localization.localizedString("result_title"))
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
            }

            Text(localization.localizedString("result_subtitle"))
                .font(.system(size: 14, design: .rounded))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .opacity(showContent ? 1 : 0)
        .offset(y: showContent ? 0 : 20)
    }

    // MARK: - Support Card

    private var supportCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "heart.circle.fill")
                    .font(.system(size: 26))
                    .foregroundColor(brandPurple)

                Text(localization.localizedString("support_title"))
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundColor(.primary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Text(localization.localizedString("support_body"))
                .font(.system(size: 15, design: .rounded))
                .foregroundColor(.primary.opacity(0.85))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)

            Text(localization.localizedString("support_resources_body"))
                .font(.system(size: 14, design: .rounded))
                .foregroundColor(.secondary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 22)
                .fill(brandPurple.opacity(0.10))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(brandPurple.opacity(0.35), lineWidth: 1.5)
        )
        .accessibilityElement(children: .combine)
        .opacity(showContent ? 1 : 0)
        .offset(y: showContent ? 0 : 20)
    }

    // MARK: - Dimensions

    private func dimensionsCard(_ prediction: PredictionResponse) -> some View {
        let rows: [(WellBeingDimension, PredictionResponse.Level)] = prediction.dimensions.compactMap { result in
            WellBeingDimension(rawValue: result.key).map { ($0, result.level) }
        }

        return VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.0) { index, row in
                dimensionRow(row.0, level: row.1)
                if index < rows.count - 1 {
                    Divider()
                        .background(Color.gray.opacity(0.15))
                        .padding(.vertical, 14)
                }
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
        .opacity(showContent ? 1 : 0)
        .offset(y: showContent ? 0 : 30)
    }

    private func dimensionRow(_ dimension: WellBeingDimension, level: PredictionResponse.Level) -> some View {
        let color = toneColor(dimension.tone(for: level))

        return HStack(alignment: .top, spacing: 14) {
            Image(systemName: dimension.iconName)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(color)
                .frame(width: 40, height: 40)
                .background(Circle().fill(color.opacity(0.14)))

            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .center) {
                    Text(localization.localizedString(dimension.titleKey))
                        .font(.system(size: 15, weight: .semibold, design: .rounded))
                        .foregroundColor(.primary)
                    Spacer(minLength: 8)
                    levelIndicator(level, color: color)
                }

                Text(localization.localizedString(dimension.messageKey(for: level)))
                    .font(.system(size: 14, design: .rounded))
                    .foregroundColor(.primary.opacity(0.75))
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            localization.localizedString(dimension.titleKey) + ". "
                + localization.localizedString(dimension.messageKey(for: level))
        )
    }

    /// Three soft steps; one filled for No, two for Low, three for High.
    private func levelIndicator(_ level: PredictionResponse.Level, color: Color) -> some View {
        let filled = (PredictionResponse.Level.allCases.firstIndex(of: level) ?? 0) + 1
        return HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { step in
                Capsule()
                    .fill(step < filled ? color : Color.gray.opacity(0.2))
                    .frame(width: 16, height: 6)
            }
        }
    }

    private func toneColor(_ tone: WellBeingDimension.Tone) -> Color {
        switch tone {
        case .steady: return Color(red: 0.30, green: 0.70, blue: 0.60)
        case .gentle: return Color(red: 0.85, green: 0.65, blue: 0.30)
        case .care: return Color(red: 0.86, green: 0.50, blue: 0.45)
        }
    }

    // MARK: - Disclaimer and Share

    private var disclaimer: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle")
                .font(.system(size: 14))
                .foregroundColor(.secondary)
            Text(localization.localizedString("result_disclaimer"))
                .font(.system(size: 13, design: .rounded))
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
        .opacity(showContent ? 1 : 0)
    }

    private var shareButton: some View {
        Button(action: {
            viewModel.showShareSheet = true
        }) {
            HStack(spacing: 8) {
                Image(systemName: "square.and.arrow.up")
                    .font(.system(size: 15, weight: .semibold))
                Text(localization.localizedString("share_results_button"))
                    .font(.system(size: 16, weight: .semibold, design: .rounded))
            }
            .foregroundColor(brandPurple)
            .padding(.horizontal, 28)
            .padding(.vertical, 12)
            .background(
                Capsule()
                    .stroke(brandPurple, lineWidth: 2)
            )
        }
        .disabled(viewModel.csvFileURL == nil)
        .opacity(showContent ? 1 : 0)
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
