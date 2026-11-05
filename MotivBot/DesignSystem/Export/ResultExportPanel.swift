import SwiftUI

// MARK: - Page preview

/// A small, live picture of the summary page, so people see what they are about to save.
/// It is decorative: VoiceOver reads the export options instead.
struct SummaryPagePreview: View {
    let summary: ResultSummary
    let localization: LocalizationManager
    var width: CGFloat = 112

    private var scale: CGFloat { width / ResultSummaryPage.pageSize.width }
    private var height: CGFloat { ResultSummaryPage.pageSize.height * scale }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)

        ResultSummaryPage(summary: summary, localization: localization)
            .fixedSize(horizontal: false, vertical: true)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: width, height: height, alignment: .topLeading)
            .background(Color.white)
            .clipShape(shape)
            .overlay(shape.strokeBorder(Color.black.opacity(0.08), lineWidth: 0.5))
            .shadow(color: .black.opacity(0.14), radius: 14, y: 8)
            // The page sets its own direction; this only keeps the scaled page pinned to the top-left corner.
            .environment(\.layoutDirection, .leftToRight)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

// MARK: - Export header

/// The page preview in the export sheet, on a soft brand panel with a second sheet tilted behind it.
struct ExportHero: View {
    let summary: ResultSummary
    let localization: LocalizationManager
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
                .fill(Color.dsBrandSoft)

            ZStack {
                // A second sheet behind the page hints that it can be saved in two formats.
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .fill(Color.dsSurface.opacity(0.7))
                    .frame(width: 112, height: 112 * ResultSummaryPage.pageSize.height / ResultSummaryPage.pageSize.width)
                    .rotationEffect(.degrees(reduceMotion ? 0 : 7))
                    .offset(x: 14, y: 4)

                SummaryPagePreview(summary: summary, localization: localization)
                    .rotationEffect(.degrees(reduceMotion ? 0 : -4))
            }
            .padding(.vertical, DSSpacing.m)
        }
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}

// MARK: - Export option

/// One way to save the summary: a tinted format tile, a title, a short detail and a save symbol.
/// While the file is being made the symbol turns into a spinner and the row is disabled.
struct ExportOptionRow: View {
    let title: String
    let detail: String
    let systemImage: String
    let tint: Color
    let softTint: Color
    var isWorking: Bool = false
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)

        Button(action: action) {
            HStack(spacing: DSSpacing.s) {
                Image(systemName: systemImage)
                    .font(.system(.title3, weight: .semibold))
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .foregroundStyle(tint)
                    .frame(width: 48, height: 48)
                    .background(softTint, in: RoundedRectangle(cornerRadius: DSRadius.small, style: .continuous))
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(DSFont.bodyEmphasis)
                        .foregroundStyle(Color.dsTextPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(detail)
                        .font(DSFont.footnote)
                        .foregroundStyle(Color.dsTextSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                ZStack {
                    if isWorking {
                        ProgressView()
                            .tint(tint)
                    } else {
                        Image(systemName: "square.and.arrow.down")
                            .font(.system(.body, weight: .semibold))
                            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                            .foregroundStyle(tint)
                    }
                }
                .frame(width: 32, height: 32)
                .accessibilityHidden(true)
            }
            .padding(DSSpacing.s)
            .frame(minHeight: DSSize.answerOptionMinHeight)
            .background(Color.dsSurfaceMuted, in: shape)
            .overlay(shape.strokeBorder(Color.dsStroke, lineWidth: 1))
            .contentShape(shape)
        }
        .buttonStyle(.dsPressable)
        .disabled(isWorking)
    }
}

// MARK: - Export sheet

/// The sheet behind the download button on the results screen: a preview of the summary page and a choice
/// between PDF and PNG (and the CSV, when there is one). It renders the chosen file and hands it back; the
/// results screen closes this sheet and opens the share sheet, so only one sheet is on screen at a time.
struct ExportOptionsSheet: View {
    let summary: ResultSummary
    let localization: LocalizationManager
    let canShareCSV: Bool
    let onFile: (ResultExportFile) -> Void
    let onShareCSV: () -> Void

    /// The format being rendered right now; its row shows a spinner and both rows are disabled.
    @State private var working: ResultExportFormat?
    @State private var failed = false
    /// The sheet is as tall as its content, up to the full screen.
    @State private var contentHeight: CGFloat = 560

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DSSpacing.m) {
                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text(localization.localizedString("export_title"))
                        .font(DSFont.title)
                        .foregroundStyle(Color.dsTextPrimary)
                        .dsTitleText()
                        .accessibilityAddTraits(.isHeader)
                    Text(localization.localizedString("export_body"))
                        .font(DSFont.callout)
                        .foregroundStyle(Color.dsTextSecondary)
                        .dsBodyText()
                }

                ExportHero(summary: summary, localization: localization)

                VStack(spacing: DSSpacing.xs) {
                    ExportOptionRow(
                        title: localization.localizedString("export_pdf_title"),
                        detail: localization.localizedString("export_pdf_detail"),
                        systemImage: "doc.richtext",
                        tint: .dsBrand,
                        softTint: .dsBrandSoft,
                        isWorking: working == .pdf
                    ) { export(.pdf) }

                    ExportOptionRow(
                        title: localization.localizedString("export_image_title"),
                        detail: localization.localizedString("export_image_detail"),
                        systemImage: "photo",
                        tint: .dsSky,
                        softTint: .dsSkySoft,
                        isWorking: working == .image
                    ) { export(.image) }
                }
                .disabled(working != nil)

                if canShareCSV {
                    Button(action: onShareCSV) {
                        Label(localization.localizedString("share_results_button"), systemImage: "square.and.arrow.up")
                    }
                    .buttonStyle(.dsTertiary)
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.horizontal, DSSpacing.screen)
            .padding(.top, DSSpacing.xl)
            .padding(.bottom, DSSpacing.m)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { contentHeight = $0 }
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(contentHeight)])
        .presentationDragIndicator(.visible)
        .alert(localization.localizedString("export_failed"), isPresented: $failed) {
            Button(localization.localizedString("ok_button"), role: .cancel) {}
        }
    }

    private func export(_ format: ResultExportFormat) {
        guard working == nil else { return }
        working = format
        Task {
            // Rendering runs on the main actor; a short pause lets the row show its spinner first.
            try? await Task.sleep(for: .milliseconds(60))
            do {
                onFile(try ResultExporter.makeFile(format, summary: summary, localization: localization))
            } catch {
                failed = true
            }
            working = nil
        }
    }
}

#if DEBUG
#Preview("Export sheet") {
    let localization = LocalizationManager()
    let summary = ResultSummary(prediction: PreviewFixtures.prediction, date: Date(), language: .turkish, elevatedRisk: true)

    return Color.clear
        .background(AppBackground())
        .sheet(isPresented: .constant(true)) {
            ExportOptionsSheet(summary: summary, localization: localization, canShareCSV: true, onFile: { _ in }, onShareCSV: {})
        }
}
#endif
