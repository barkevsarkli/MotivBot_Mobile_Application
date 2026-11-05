import SwiftUI

struct ResultView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var isNoteFocused: Bool
    @State private var exportFile: ResultExportFile?
    @State private var exportFailed = false
    @State private var hasAppeared = false

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
        .safeAreaInset(edge: .top) { TopBar() }
        .animation(DSMotion.animation(DSMotion.soft, reduceMotion: reduceMotion), value: viewModel.isCompleted)
        .sensoryFeedback(.success, trigger: hasAppeared) { _, appeared in appeared }
        .onAppear { hasAppeared = true }
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
        .alert(localization.localizedString("export_failed"), isPresented: $exportFailed) {
            Button(localization.localizedString("ok_button"), role: .cancel) {}
        }
    }

    // MARK: - Results

    @ViewBuilder
    private var results: some View {
        header
            .dsAppear()

        if let prediction = viewModel.prediction {
            // The support card comes first so a user at risk sees it before anything else.
            if viewModel.elevatedRisk {
                SupportCard(
                    title: localization.localizedString("support_title"),
                    message: localization.localizedString("support_body")
                )
                .dsAppear(delay: 0.1)
            }

            if !prediction.messages.isEmpty {
                messages(prediction.messages)
                    .dsAppear(delay: 0.2)
            }

            dimensions(prediction)
                .dsAppear(delay: 0.3)

            disclaimer
                .dsAppear(delay: 0.35)

            exportSection
                .dsAppear(delay: 0.4)
        }

        reflection
            .dsAppear(delay: 0.5)
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
        VStack(alignment: .leading, spacing: DSSpacing.s) {
            sectionTitle("messages_title")
            ForEach(Array(MessageCategoryStyle.ordered(messages).enumerated()), id: \.offset) { _, message in
                let style = MessageCategoryStyle(category: message.category)
                MessageCard(
                    style: style,
                    categoryTitle: localization.localizedString(style.titleKey),
                    text: message.text,
                    author: message.author
                )
            }
        }
    }

    private func dimensions(_ prediction: PredictionResponse) -> some View {
        let rows: [(WellBeingDimension, PredictionResponse.Level)] = WellBeingDimension.allCases.compactMap { dimension in
            prediction.level(for: dimension).map { (dimension, $0) }
        }

        return VStack(alignment: .leading, spacing: DSSpacing.s) {
            sectionTitle("dimensions_title")
            VStack(alignment: .leading, spacing: DSSpacing.m) {
                ForEach(Array(rows.enumerated()), id: \.element.0) { index, row in
                    DimensionRow(
                        dimension: row.0,
                        level: row.1,
                        title: localization.localizedString(row.0.titleKey),
                        message: localization.localizedString(row.0.messageKey(for: row.1))
                    )
                    if index < rows.count - 1 {
                        Divider().overlay(Color.dsStroke)
                    }
                }
            }
            .dsCard()
        }
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

    private var exportSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.s) {
            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text(localization.localizedString("export_title"))
                    .font(DSFont.heading)
                    .foregroundStyle(Color.dsTextPrimary)
                    .dsTitleText()
                    .accessibilityAddTraits(.isHeader)
                Text(localization.localizedString("export_body"))
                    .font(DSFont.callout)
                    .foregroundStyle(Color.dsTextSecondary)
                    .dsBodyText()
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: DSSpacing.xs) { exportButtons }
                VStack(spacing: DSSpacing.xs) { exportButtons }
            }

            if viewModel.csvFileURL != nil {
                Button {
                    viewModel.showShareSheet = true
                } label: {
                    Label(localization.localizedString("share_results_button"), systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.dsTertiary)
            }
        }
        .dsCard()
    }

    @ViewBuilder
    private var exportButtons: some View {
        Button { export(.pdf) } label: {
            DSButtonLabel(title: localization.localizedString("export_pdf"), systemImage: "doc.richtext")
        }
        .buttonStyle(.dsSecondary)

        Button { export(.image) } label: {
            DSButtonLabel(title: localization.localizedString("export_image"), systemImage: "photo")
        }
        .buttonStyle(.dsSecondary)
    }

    private func export(_ format: ResultExportFormat) {
        guard let summary = viewModel.resultSummary else { return }
        do {
            exportFile = try ResultExporter.makeFile(format, summary: summary, localization: localization)
        } catch {
            exportFailed = true
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

            ChipGroup {
                ForEach(Array(viewModel.feelingEmojis.enumerated()), id: \.offset) { index, feeling in
                    SelectableChip(
                        title: localization.localizedString(feeling.labelKey),
                        isSelected: viewModel.selectedFeelingIndex == index
                    ) {
                        withAnimation(DSMotion.quick) {
                            viewModel.selectedFeelingIndex = index
                        }
                    }
                }
            }
            .sensoryFeedback(.selection, trigger: viewModel.selectedFeelingIndex)

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
#Preview("Light, support card") {
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
