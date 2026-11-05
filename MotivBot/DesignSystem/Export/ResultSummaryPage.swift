import SwiftUI

/// The one-page results summary used for the PDF and image export.
/// It shows the date, the five dimensions (levels only), the messages and the disclaimer; never the
/// free text or individual answers. It is always light and keeps its meaning in black and white:
/// levels are filled versus outlined steps, and every colour is backed by text.
struct ResultSummaryPage: View {
    static let pageSize = CGSize(width: 595, height: 842) // A4 in points
    static let margin: CGFloat = 40

    let summary: ResultSummary
    let localization: LocalizationManager

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            header
            Rectangle().fill(Color.dsStroke).frame(height: 1)

            if summary.elevatedRisk {
                support
            }

            dimensions
            if !summary.messages.isEmpty {
                messages
            }
            disclaimer

            Text(localization.localizedString("export_footer"))
                .font(.system(size: 8))
                .foregroundStyle(Color.dsTextSecondary)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .padding(Self.margin)
        .frame(width: Self.pageSize.width, alignment: .topLeading)
        .background(Color.white)
        .environment(\.colorScheme, .light)
        .environment(\.layoutDirection, summary.language.layoutDirection)
        .environment(\.locale, summary.language.locale)
        .environment(\.dynamicTypeSize, .large)
    }

    // MARK: - Parts

    private var header: some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: "leaf.fill")
                .font(.system(size: 18))
                .foregroundStyle(Color.dsBrand)
            VStack(alignment: .leading, spacing: 2) {
                Text(localization.localizedString("app_title"))
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundStyle(Color.dsTextPrimary)
                Text(localization.localizedString("export_page_title"))
                    .font(.system(size: 11))
                    .foregroundStyle(Color.dsTextSecondary)
            }
            Spacer()
            Text(localization.longDate(summary.date))
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.dsTextSecondary)
        }
    }

    private var support: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(localization.localizedString("support_title"))
                .font(.system(size: 12, weight: .semibold))
            Text(localization.localizedString("support_body"))
                .font(.system(size: 10))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(Color.dsTextPrimary)
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.dsCare, lineWidth: 1))
    }

    private var dimensions: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle(localization.localizedString("export_dimensions_title"))
            ForEach(summary.dimensions) { row in
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: row.dimension.iconName)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(row.tone.color)
                        .frame(width: 18)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack {
                            Text(localization.localizedString(row.titleKey))
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Color.dsTextPrimary)
                            Spacer()
                            LevelIndicator(filledSteps: row.level.filledSteps, color: row.tone.color)
                        }
                        Text(localization.localizedString(row.messageKey))
                            .font(.system(size: 10))
                            .foregroundStyle(Color.dsTextSecondary)
                            .lineSpacing(summary.language.layoutDirection == .rightToLeft ? 3 : 1)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
    }

    private var messages: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(localization.localizedString("messages_title"))
            ForEach(Array(MessageCategoryStyle.ordered(summary.messages).enumerated()), id: \.offset) { _, message in
                let style = MessageCategoryStyle(category: message.category)
                HStack(alignment: .top, spacing: 10) {
                    Rectangle()
                        .fill(style.color)
                        .frame(width: 2)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(localization.localizedString(style.titleKey))
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundStyle(style.color)
                        Text(message.text)
                            .font(.system(size: 10))
                            .foregroundStyle(Color.dsTextPrimary)
                            .lineSpacing(summary.language.layoutDirection == .rightToLeft ? 3 : 1)
                            .fixedSize(horizontal: false, vertical: true)
                        if let author = message.author, !author.isEmpty {
                            Text(author)
                                .font(.system(size: 9))
                                .foregroundStyle(Color.dsTextSecondary)
                        }
                    }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var disclaimer: some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: "info.circle")
                .font(.system(size: 10))
            Text(localization.localizedString(ResultSummary.disclaimerKey))
                .font(.system(size: 10))
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(Color.dsTextPrimary)
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.dsTextSecondary, lineWidth: 0.75))
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold, design: .rounded))
            .foregroundStyle(Color.dsTextPrimary)
    }
}

#if DEBUG
private func previewSummary(_ language: AppLanguage) -> ResultSummary {
    ResultSummary(
        prediction: language == .persian ? PreviewFixtures.persianPrediction : PreviewFixtures.prediction,
        date: Date(),
        language: language,
        elevatedRisk: true
    )
}

private func previewLocalization(_ language: AppLanguage) -> LocalizationManager {
    let localization = LocalizationManager()
    localization.setLanguage(language)
    return localization
}

#Preview("Export page") {
    ScrollView {
        ResultSummaryPage(summary: previewSummary(.turkish), localization: previewLocalization(.turkish))
    }
}

#Preview("Export page, dark device") {
    ScrollView {
        ResultSummaryPage(summary: previewSummary(.turkish), localization: previewLocalization(.turkish))
    }
    .preferredColorScheme(.dark)
}

#Preview("Export page, Persian (RTL)") {
    ScrollView {
        ResultSummaryPage(summary: previewSummary(.persian), localization: previewLocalization(.persian))
    }
}
#endif
