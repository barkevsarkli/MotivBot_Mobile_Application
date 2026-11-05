import SwiftUI

// MARK: - Theme toggle

/// Follows the system by default. A tap switches to the opposite of the system appearance, the next tap
/// back to the system. The choice is kept with `@AppStorage`; the root applies it.
struct ThemeToggle: View {
    @Environment(LocalizationManager.self) private var localization
    @Environment(\.colorScheme) private var colorScheme
    @AppStorage(ThemePreference.storageKey) private var preference: ThemePreference = .system

    /// While the preference is `system`, `colorScheme` is the system's, so its opposite is the override.
    private var next: ThemePreference {
        guard preference == .system else { return .system }
        return colorScheme == .dark ? .light : .dark
    }

    /// The appearance the tap leads to, shown on the button.
    private var target: ColorScheme {
        colorScheme == .dark ? .light : .dark
    }

    private var systemImage: String { target == .dark ? "moon" : "sun.max" }
    private var labelKey: String { target == .dark ? "theme_switch_to_dark" : "theme_switch_to_light" }

    var body: some View {
        Button {
            preference = next
        } label: {
            Image(systemName: systemImage)
                .font(.system(.body, weight: .semibold))
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .foregroundStyle(Color.dsTextPrimary)
                .frame(width: DSSize.minTap, height: DSSize.minTap)
                .dsGlass(in: Circle(), interactive: true)
                .contentShape(Circle())
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.dsPressable)
        .accessibilityLabel(localization.localizedString(labelKey))
        .accessibilityShowsLargeContentViewer {
            Label(localization.localizedString(labelKey), systemImage: systemImage)
        }
        .sensoryFeedback(.selection, trigger: preference)
    }
}

// MARK: - Language picker

/// The only place the language can be changed (the welcome screen). Each language is written in its own script.
struct LanguagePicker: View {
    @Environment(LocalizationManager.self) private var localization
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(spacing: DSSpacing.xxs))
            : AnyLayout(HStackLayout(spacing: DSSpacing.xxs))
        layout {
            ForEach(AppLanguage.allCases) { language in
                option(language)
            }
        }
        .padding(DSSpacing.xxs)
        .dsGlass(in: RoundedRectangle(cornerRadius: DSRadius.medium + 4, style: .continuous))
        .sensoryFeedback(.selection, trigger: localization.currentLanguage)
    }

    private func option(_ language: AppLanguage) -> some View {
        let isSelected = localization.currentLanguage == language
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        return Button {
            withAnimation(DSMotion.standard) {
                localization.setLanguage(language)
            }
        } label: {
            Text(localization.localizedString(language.nameKey))
                .font(DSFont.heading)
                .foregroundStyle(isSelected ? Color.dsOnBrand : Color.dsTextPrimary)
                .frame(maxWidth: .infinity, minHeight: DSSize.minTap)
                .background {
                    if isSelected {
                        shape.fill(Color.dsBrand)
                    }
                }
                .contentShape(shape)
        }
        .buttonStyle(.dsPressable)
        .disabled(localization.isLocked)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        // The language names are written in their own scripts; give VoiceOver the right voice.
        .environment(\.locale, language.locale)
    }
}

// MARK: - Bars

/// The row above a screen's content: an optional leading control and the theme toggle.
struct TopBar<Leading: View>: View {
    @ViewBuilder var leading: Leading

    var body: some View {
        HStack {
            leading
            Spacer()
            ThemeToggle()
        }
        .padding(.horizontal, DSSpacing.screen - 4)
        .padding(.top, DSSpacing.xxs)
        .padding(.bottom, DSSpacing.xs)
    }
}

extension TopBar where Leading == EmptyView {
    init() {
        self.leading = EmptyView()
    }
}

/// A floating glass bar at the bottom for the screen's actions. It rides above the keyboard.
struct BottomActionBar<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: DSSpacing.xs) {
            content
        }
        .padding(DSSpacing.xs)
        .dsGlass(in: RoundedRectangle(cornerRadius: DSRadius.large, style: .continuous))
        .frame(maxWidth: DSSize.contentMaxWidth)
        .padding(.horizontal, DSSpacing.screen - 8)
        .padding(.bottom, DSSpacing.xs)
    }
}

/// Centres a screen's content and limits its width on large screens.
struct ScreenColumn<Content: View>: View {
    var spacing: CGFloat = DSSpacing.l
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            content
        }
        .frame(maxWidth: DSSize.contentMaxWidth, alignment: .leading)
        .padding(.horizontal, DSSpacing.screen)
        .frame(maxWidth: .infinity)
    }
}
