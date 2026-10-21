import SwiftUI

struct EmotionInputView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @FocusState private var isEditorFocused: Bool
    /// Back to the welcome screen. Offered only while the language can still change (nothing typed yet).
    var onBack: () -> Void = {}

    var body: some View {
        @Bindable var viewModel = viewModel

        ScrollView {
            ScreenColumn(spacing: DSSpacing.l) {
                header
                    .dsAppear()

                editor(text: $viewModel.emotionText)
                    .dsAppear(delay: 0.1)

                hint
                    .dsAppear(delay: 0.2)
            }
            .padding(.top, DSSpacing.s)
            .padding(.bottom, DSSpacing.l)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .top) {
            TopBar {
                if viewModel.canChangeLanguage {
                    GlassIconButton(systemImage: "chevron.backward", accessibilityLabel: localization.localizedString("back_button")) {
                        isEditorFocused = false
                        onBack()
                    }
                    .transition(.opacity)
                }
            }
            .animation(DSMotion.standard, value: viewModel.canChangeLanguage)
        }
        .safeAreaInset(edge: .bottom) {
            BottomActionBar {
                Button {
                    isEditorFocused = false
                    viewModel.submitEmotionAndProceed()
                } label: {
                    DSButtonLabel(title: localization.localizedString("next_button"), systemImage: "arrow.forward")
                }
                .buttonStyle(.dsPrimary)
                .disabled(!viewModel.canProceedEmotion)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text(localization.localizedString("emotion_title"))
                .font(DSFont.display)
                .foregroundStyle(Color.dsTextPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(localization.localizedString("emotion_prompt"))
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextSecondary)
                .dsBodyText()
        }
    }

    private func editor(text: Binding<String>) -> some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.large, style: .continuous)
        return VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Text(localization.localizedString("your_thoughts"))
                .font(DSFont.caption)
                .foregroundStyle(Color.dsTextSecondary)
                .accessibilityHidden(true)

            TextEditor(text: text)
                .focused($isEditorFocused)
                .font(DSFont.body)
                .foregroundStyle(Color.dsTextPrimary)
                .scrollContentBackground(.hidden)
                .frame(minHeight: 200)
                .padding(DSSpacing.s)
                .background(Color.dsSurface, in: shape)
                .overlay(alignment: .topLeading) {
                    if text.wrappedValue.isEmpty {
                        Text(localization.localizedString("emotion_placeholder"))
                            .font(DSFont.body)
                            .foregroundStyle(Color.dsTextTertiary)
                            .padding(.horizontal, DSSpacing.s + 5)
                            .padding(.vertical, DSSpacing.s + 8)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                }
                .overlay(
                    shape.strokeBorder(isEditorFocused ? Color.dsBrand : Color.dsStroke, lineWidth: isEditorFocused ? 1.5 : 1)
                )
                .animation(DSMotion.quick, value: isEditorFocused)
                .accessibilityLabel(localization.localizedString("your_thoughts"))
                .accessibilityHint(localization.localizedString("emotion_placeholder"))
        }
    }

    @ViewBuilder
    private var hint: some View {
        if let warningKey = viewModel.emotionTextWarningKey {
            Banner(kind: .notice, text: warningText(warningKey))
                .transition(.opacity)
        } else if viewModel.canProceedEmotion {
            Label {
                Text(localization.localizedString("emotion_ready"))
            } icon: {
                Image(systemName: "checkmark.circle")
            }
            .font(DSFont.footnote)
            .foregroundStyle(Color.dsSteady)
            .transition(.opacity)
        } else {
            Text(localization.localizedString("emotion_hint"))
                .font(DSFont.footnote)
                .foregroundStyle(Color.dsTextSecondary)
                .dsBodyText()
                .transition(.opacity)
        }
    }

    private func warningText(_ key: String) -> String {
        key == "emotion_too_long"
            ? localization.localizedString(key, PredictionRequest.textLengthRange.upperBound)
            : localization.localizedString(key)
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost(.emotionInput(text: nil)) { EmotionInputView() }
}

#Preview("Dark") {
    PreviewHost(.emotionInput(text: PreviewFixtures.sampleText)) { EmotionInputView() }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL)") {
    PreviewHost(.emotionInput(text: PreviewFixtures.samplePersianText), language: .persian) { EmotionInputView() }
}
#endif
