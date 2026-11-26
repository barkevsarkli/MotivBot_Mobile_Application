import SwiftUI

struct ResultView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var isNoteFocused: Bool
    @State private var exportFile: ResultExportFile?
    @State private var showExportOptions = false
    /// What the export sheet chose; shared once that sheet has closed, so two sheets never overlap.
    @State private var pendingExportFile: ResultExportFile?
    @State private var pendingCSVShare = false
    @State private var hasAppeared = false
    /// The swipe tip pops up once by itself; the info button next to the messages title shows it again.
    @State private var showSwipeTip = false
    @State private var hasShownSwipeTip = false

    var body: some View {
        @Bindable var viewModel = viewModel

        ScrollView {
            ScreenColumn(spacing: DSSpacing.l) {
                if viewModel.isCompleted {
                    completion
                        .transition(.opacity.combined(with: .scale(scale: reduceMotion ? 1 : 0.96)))
                } else {
                    results
                        .transition(.opacity)
                }
            }
            .padding(.top, DSSpacing.xs)
            .padding(.bottom, DSSpacing.xl)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .top) {
            TopBar { EmptyView() } trailing: {
                if canExport {
                    GlassIconButton(
                        systemImage: "square.and.arrow.down",
                        accessibilityLabel: localization.localizedString("export_button")
                    ) {
                        showExportOptions = true
                    }
                    .transition(.opacity.combined(with: .scale(scale: reduceMotion ? 1 : 0.8)))
                }
            }
        }
        .animation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion), value: viewModel.isCompleted)
        .sensoryFeedback(.success, trigger: hasAppeared) { _, appeared in appeared }
        .onAppear { hasAppeared = true }
        .sheet(isPresented: $showExportOptions, onDismiss: presentPendingShare) {
            if let summary = viewModel.resultSummary {
                ExportOptionsSheet(
                    summary: summary,
                    localization: localization,
                    canShareCSV: viewModel.csvFileURL != nil,
                    onFile: { file in
                        pendingExportFile = file
                        showExportOptions = false
                    },
                    onShareCSV: {
                        pendingCSVShare = true
                        showExportOptions = false
                    }
                )
            }
        }
        .sheet(item: $exportFile) { file in
            ExportShareSheet(file: file)
                .presentationDetents([.medium, .large])
                .ignoresSafeArea()
        }
        .sheet(isPresented: $viewModel.showShareSheet) {
            if let csvURL = viewModel.csvFileURL {
                ShareSheet(activityItems: [csvURL])
            }
        }
    }

    /// The download button is only on the results, not on the thank-you screen after Submit.
    private var canExport: Bool {
        !viewModel.isCompleted && viewModel.resultSummary != nil
    }

    // MARK: - Results

    @ViewBuilder
    private var results: some View {
        header
            .dsAppear()

        if let prediction = viewModel.prediction {
            // The levels are only in the PDF / image summary, not on this screen.
            if !prediction.messages.isEmpty {
                messages(prediction.messages)
                    .dsAppear(delay: 0.2)
            }

            disclaimer
                .dsAppear(delay: 0.35)
        }

        reflection
            .dsAppear(delay: 0.45)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text(localization.localizedString("result_title"))
                .font(DSFont.display)
                .foregroundStyle(Color.dsTextPrimary)
                .dsTitleText()
                .accessibilityAddTraits(.isHeader)
            Text(localization.localizedString("result_subtitle"))
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextSecondary)
                .dsBodyText()
        }
    }

    private func messages(_ messages: [PredictionResponse.Message]) -> some View {
        let ordered = MessageCategoryStyle.ordered(messages)

        return VStack(alignment: .leading, spacing: DSSpacing.s) {
            HStack(spacing: DSSpacing.xs) {
                sectionTitle("messages_title")
                Spacer(minLength: 0)
                swipeTipButton
            }
            MessageDeck(
                messages: ordered,
                strings: MessageDeck.Strings(
                    hint: localization.localizedString("messages_swipe_hint"),
                    next: localization.localizedString("message_next"),
                    doneTitle: localization.localizedString("messages_done_title"),
                    doneBody: localization.localizedString("messages_done_body"),
                    replay: localization.localizedString("messages_replay"),
                    categoryTitle: { localization.localizedString($0.titleKey) }
                )
            )
        }
        .task { await showSwipeTipOnce() }
    }

    private var swipeTipButton: some View {
        Button {
            showSwipeTip = true
        } label: {
            Image(systemName: "info.circle")
                .font(.system(.body, weight: .semibold))
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .foregroundStyle(Color.dsBrand)
                .frame(width: DSSize.minTap, height: DSSize.minTap)
                .contentShape(Rectangle())
        }
        .buttonStyle(.dsPressable)
        .accessibilityLabel(localization.localizedString("messages_swipe_info"))
        .popover(isPresented: $showSwipeTip, arrowEdge: .top) {
            SwipeTip(text: localization.localizedString("messages_swipe_hint"))
                .presentationCompactAdaptation(.popover)
        }
    }

    /// Pops the swipe tip up shortly after the cards appear, and closes it again after a few seconds.
    private func showSwipeTipOnce() async {
        guard !hasShownSwipeTip else { return }
        guard (try? await Task.sleep(for: .seconds(1.2))) != nil else { return }
        hasShownSwipeTip = true
        showSwipeTip = true
        guard (try? await Task.sleep(for: .seconds(5))) != nil else { return }
        showSwipeTip = false
    }

    private var disclaimer: some View {
        Label {
            Text(localization.localizedString("result_disclaimer"))
                .font(DSFont.footnote)
                .foregroundStyle(Color.dsTextSecondary)
                .dsBodyText()
        } icon: {
            Image(systemName: "info.circle")
                .foregroundStyle(Color.dsTextSecondary)
        }
        .padding(.horizontal, DSSpacing.xxs)
    }

    // MARK: - Export

    private func presentPendingShare() {
        if let file = pendingExportFile {
            pendingExportFile = nil
            exportFile = file
        } else if pendingCSVShare {
            pendingCSVShare = false
            viewModel.showShareSheet = true
        }
    }

    // MARK: - Reflection

    private var reflection: some View {
        @Bindable var viewModel = viewModel

        return VStack(alignment: .leading, spacing: DSSpacing.m) {
            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text(localization.localizedString("reflection_title"))
                    .font(DSFont.heading)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsTitleText()
                    .accessibilityAddTraits(.isHeader)
                Text(localization.localizedString("reflection_question"))
                    .font(DSFont.callout)
                    .foregroundStyle(Color.dsTextSecondary)
                    .dsBodyText()
            }

            VStack(alignment: .leading, spacing: DSSpacing.m) {
                ForEach(ReflectionTopic.allCases) { topic in
                    ReflectionRatingRow(
                        title: localization.localizedString(topic.titleKey),
                        systemImage: topic.systemImage,
                        ratingTitle: { localization.localizedString($0.titleKey) },
                        selection: viewModel.reflectionRatings[topic]
                    ) { rating in
                        withAnimation(DSMotion.quick) {
                            viewModel.rateReflection(topic, rating)
                        }
                    }
                }
            }
            .sensoryFeedback(.selection, trigger: viewModel.reflectionRatings)

            if !viewModel.canSubmitReflection {
                Text(localization.localizedString("reflection_required_hint"))
                    .font(DSFont.caption)
                    .foregroundStyle(Color.dsTextSecondary)
                    .dsBodyText()
                    .transition(.opacity)
            }

            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text(localization.localizedString("reflection_optional"))
                    .font(DSFont.caption)
                    .foregroundStyle(Color.dsTextSecondary)
                    .accessibilityHidden(true)

                let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
                TextField(localization.localizedString("reflection_placeholder"), text: $viewModel.reflectionText, axis: .vertical)
                    .focused($isNoteFocused)
                    .lineLimit(3...6)
                    .font(DSFont.body)
                    .foregroundStyle(Color.dsTextPrimary)
                    .padding(DSSpacing.s)
                    .frame(minHeight: DSSize.minTap)
                    .background(Color.dsSurfaceMuted, in: shape)
                    .overlay(shape.strokeBorder(isNoteFocused ? Color.dsBrand : Color.dsStroke, lineWidth: isNoteFocused ? 1.5 : 1))
                    .animation(DSMotion.quick, value: isNoteFocused)
                    .accessibilityLabel(localization.localizedString("reflection_optional"))
            }

            Button {
                isNoteFocused = false
                viewModel.submitReflection()
            } label: {
                DSButtonLabel(title: localization.localizedString("submit_reflection"), systemImage: "checkmark")
            }
            .buttonStyle(.dsPrimary)
            .disabled(!viewModel.canSubmitReflection)
        }
        .dsCard()
    }

    // MARK: - Completion

    private var completion: some View {
        VStack(spacing: DSSpacing.l) {
            Spacer(minLength: DSSpacing.xxl)

            Image(systemName: "leaf.fill")
                .font(.system(size: 40))
                .foregroundStyle(Color.dsSteady)
                .frame(width: 96, height: 96)
                .background(Color.dsSteadySoft, in: Circle())
                .symbolEffect(.bounce, options: .nonRepeating, value: viewModel.isCompleted)
                .accessibilityHidden(true)

            VStack(spacing: DSSpacing.s) {
                Text(localization.localizedString("completion_title"))
                    .font(DSFont.display)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsTitleText()
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text(localization.localizedString("completion_message"))
                    .font(DSFont.body)
                    .foregroundStyle(Color.dsTextSecondary)
                    .multilineTextAlignment(.center)
                    .dsBodyText()
            }

            Button {
                ResultExporter.removeFiles()
                viewModel.restartAssessment()
            } label: {
                DSButtonLabel(title: localization.localizedString("restart_button"), systemImage: "arrow.counterclockwise")
            }
            .buttonStyle(.dsSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    private func sectionTitle(_ key: String) -> some View {
        Text(localization.localizedString(key))
            .font(DSFont.heading)
            .foregroundStyle(Color.dsTextPrimary)
            .dsTitleText()
            .accessibilityAddTraits(.isHeader)
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost(.result(elevatedRisk: true)) { ResultView() }
}

#Preview("Dark") {
    PreviewHost(.result(elevatedRisk: false)) { ResultView() }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL)") {
    PreviewHost(.result(elevatedRisk: true), language: .persian) { ResultView() }
}

#Preview("Completed") {
    PreviewHost(.completed) { ResultView() }
}
#endif
