import SwiftUI

/// The first screen: what MotivBot is, what to expect, and the language choice (the only place it can change).
struct WelcomeView: View {

    @Environment(LocalizationManager.self) var localization
    let onStart: () -> Void

    var body: some View {
        ScrollView {
            ScreenColumn(spacing: DSSpacing.xl) {
                header
                    .dsAppear()

                expectations
                    .dsAppear(delay: 0.1)

                languageSection
                    .dsAppear(delay: 0.2)
            }
            .padding(.top, DSSpacing.l)
            .padding(.bottom, DSSpacing.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .top) { TopBar() }
        .safeAreaInset(edge: .bottom) {
            BottomActionBar {
                Button(action: onStart) {
                    DSButtonLabel(title: localization.localizedString("welcome_start"), systemImage: "arrow.forward")
                }
                .buttonStyle(.dsPrimary)
            }
            .dsAppear(delay: 0.3)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DSSpacing.s) {
            Image(systemName: "leaf.fill")
                .font(.system(.largeTitle))
                .foregroundStyle(Color.dsBrand)
                .frame(width: 64, height: 64)
                .background(Color.dsBrandSoft, in: RoundedRectangle(cornerRadius: DSRadius.medium + 4, style: .continuous))
                .accessibilityHidden(true)

            Text(localization.localizedString("app_title"))
                .font(DSFont.display)
                .foregroundStyle(Color.dsTextPrimary)
                .accessibilityAddTraits(.isHeader)

            Text(localization.localizedString("welcome_tagline"))
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextSecondary)
                .dsBodyText()
        }
    }

    private var expectations: some View {
        VStack(alignment: .leading, spacing: DSSpacing.m) {
            Text(localization.localizedString("welcome_expect_title"))
                .font(DSFont.heading)
                .foregroundStyle(Color.dsTextPrimary)
                .accessibilityAddTraits(.isHeader)

            expectationRow("clock", key: "welcome_expect_time")
            expectationRow("text.bubble", key: "welcome_expect_steps")
            expectationRow("1.circle", key: "welcome_expect_once")

            Divider().overlay(Color.dsStroke)

            Label {
                Text(localization.localizedString("welcome_not_diagnosis"))
                    .font(DSFont.callout)
                    .foregroundStyle(Color.dsTextSecondary)
                    .dsBodyText()
            } icon: {
                Image(systemName: "info.circle")
                    .foregroundStyle(Color.dsTextSecondary)
            }
        }
        .dsCard()
    }

    private func expectationRow(_ systemImage: String, key: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: DSSpacing.s) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.dsBrand)
                .frame(width: 24)
                .accessibilityHidden(true)
            Text(localization.localizedString(key))
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextPrimary)
                .dsBodyText()
        }
        .accessibilityElement(children: .combine)
    }

    private var languageSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text(localization.localizedString("welcome_language_label"))
                .font(DSFont.label)
                .foregroundStyle(Color.dsTextSecondary)
                .accessibilityAddTraits(.isHeader)
            LanguagePicker()
            Text(localization.localizedString("welcome_language_hint"))
                .font(DSFont.footnote)
                .foregroundStyle(Color.dsTextSecondary)
                .dsBodyText()
        }
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost { WelcomeView(onStart: {}) }
}

#Preview("Dark") {
    PreviewHost { WelcomeView(onStart: {}) }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL)") {
    PreviewHost(language: .persian) { WelcomeView(onStart: {}) }
}
#endif
