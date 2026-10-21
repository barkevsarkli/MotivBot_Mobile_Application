import SwiftUI

/// Shown at launch when an unfinished assessment was saved. It appears in the saved session's language.
struct ResumePromptView: View {

    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    let onContinue: () -> Void
    @State private var isConfirmingStartOver = false

    var body: some View {
        ScrollView {
            ScreenColumn(spacing: DSSpacing.l) {
                if let session = viewModel.resumableSession {
                    card(session)
                        .dsAppear()
                }
            }
            .padding(.vertical, DSSpacing.l)
            .frame(maxHeight: .infinity)
        }
        .scrollBounceBehavior(.basedOnSize)
        .defaultScrollAnchor(.center)
        .safeAreaInset(edge: .top) { TopBar() }
        .safeAreaInset(edge: .bottom) {
            BottomActionBar {
                Button {
                    onContinue()
                    viewModel.continueSession()
                } label: {
                    DSButtonLabel(title: localization.localizedString("resume_continue"), systemImage: "arrow.forward")
                }
                .buttonStyle(.dsPrimary)

                Button {
                    isConfirmingStartOver = true
                } label: {
                    DSButtonLabel(title: localization.localizedString("resume_start_over"))
                }
                .buttonStyle(.dsSecondary)
            }
            .dsAppear(delay: 0.15)
        }
        .confirmationDialog(
            localization.localizedString("resume_confirm_title"),
            isPresented: $isConfirmingStartOver,
            titleVisibility: .visible
        ) {
            Button(localization.localizedString("resume_confirm_action"), role: .destructive) {
                viewModel.startOver()
            }
            Button(localization.localizedString("cancel_button"), role: .cancel) {}
        } message: {
            Text(localization.localizedString("resume_confirm_message"))
        }
    }

    private func card(_ session: ResumableSession) -> some View {
        let fraction = session.totalQuestions > 0
            ? Double(session.answeredQuestions) / Double(session.totalQuestions)
            : 0

        return VStack(alignment: .leading, spacing: DSSpacing.m) {
            HStack(alignment: .center, spacing: DSSpacing.m) {
                ZStack {
                    ProgressRing(fraction: fraction)
                    Image(systemName: "bookmark.fill")
                        .foregroundStyle(Color.dsBrand)
                        .accessibilityHidden(true)
                }
                .frame(width: 64, height: 64)

                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text(localization.localizedString("resume_title"))
                        .font(DSFont.title)
                        .foregroundStyle(Color.dsTextPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                    Text(localization.localizedString("resume_body"))
                        .font(DSFont.callout)
                        .foregroundStyle(Color.dsTextSecondary)
                        .dsBodyText()
                }
            }

            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text(localization.localizedString(
                    "resume_progress",
                    localization.number(session.answeredQuestions),
                    localization.number(session.totalQuestions)
                ))
                .font(DSFont.bodyEmphasis)
                .foregroundStyle(Color.dsTextPrimary)

                Text(localization.localizedString("resume_started", startedText(session.startedAt)))
                    .font(DSFont.footnote)
                    .foregroundStyle(Color.dsTextSecondary)
            }
            .accessibilityElement(children: .combine)
        }
        .dsCard()
    }

    private func startedText(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = localization.locale
        formatter.calendar = localization.currentLanguage.calendar
        formatter.unitsStyle = .full
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

#if DEBUG
#Preview("Light") {
    PreviewHost(.resumable(answered: 14)) { ResumePromptView(onContinue: {}) }
}

#Preview("Dark") {
    PreviewHost(.resumable(answered: 14)) { ResumePromptView(onContinue: {}) }
        .preferredColorScheme(.dark)
}

#Preview("Persian (RTL)") {
    PreviewHost(.resumable(answered: 14), language: .persian) { ResumePromptView(onContinue: {}) }
}
#endif
