# iOS state contract (integration → design)

This lists what the UI reads and calls on `WellBeingViewModel` (`@Environment(WellBeingViewModel.self)`) and
`LocalizationManager` (`@Environment(LocalizationManager.self)`). Both are created once in `MindWellApp`, and the
view model holds the same `LocalizationManager` instance as `viewModel.localization`.

The app supports **Turkish (`tr`) and Persian (`fa`) only**. English was removed.

## Screens

`viewModel.currentScreen: AppScreen`: `.emotionInput` (the first/welcome screen), `.questionnaire`, `.loading`, `.result`.
This is unchanged.

## Language

| Read / call | Meaning |
|---|---|
| `viewModel.canChangeLanguage: Bool` | **Show the language toggle only while this is true.** It is true only on `.emotionInput`, before the user types, and when no resumable session is waiting. |
| `localization.isLocked: Bool` | The lock itself. While locked, `toggleLanguage()` / `setLanguage(_:)` do nothing, so hiding the toggle is cosmetic. |
| `localization.currentLanguage: AppLanguage` | `.turkish` / `.persian`. This is now `private(set)`. |
| `localization.layoutDirection` | Already applied at the root in `MindWellApp` (fa means `.rightToLeft`). |

Lock rules: typing the first character of the emotion text locks the language. Clearing the text while still on the first
screen unlocks it again, so a user who started in the wrong language is not stuck. Proceeding to the questionnaire
locks it until `restartAssessment()`. A resumed session restores its saved language and stays locked.

## Emotion text (first screen)

| Read | Meaning |
|---|---|
| `viewModel.canProceedEmotion: Bool` | Enables Next. It requires 10–2000 characters **in the app's language**. |
| `viewModel.emotionTextStatus` | `.tooShort`, `.tooLong`, `.wrongLanguage` or `.valid`. |
| `viewModel.emotionTextWarningKey: String?` | The key to show under the text field, or nil. `emotion_wrong_language` means the text is not in the chosen language. `emotion_too_long` takes `%d` = `PredictionRequest.textLengthRange.upperBound` (2000). |

Why: the Space picks the language of the motivational messages from the text alone. The app copies the Space's
detection rule (`PipelineLanguage`), so Turkish text needs a Turkish letter (ç ğ ı ö ş ü) or two common Turkish words.
Persian text needs >30 % Arabic-script characters.

## Save & resume

| Read / call | Meaning |
|---|---|
| `viewModel.resumableSession: ResumableSession?` | Set at launch when an unfinished assessment exists (younger than 7 days). It has `language`, `startedAt`, `answeredQuestions` and `totalQuestions`. While it is non-nil, show a **Continue / Start over** prompt on the first screen. |
| `viewModel.continueSession()` | Restores the language (locked), text, answers and position. If all questions were answered, it goes straight to `.loading` and resubmits. |
| `viewModel.startOver()` | Deletes the saved session and stays on the first screen. |

Saving is automatic (debounced 500 ms, and flushed when the app leaves the foreground). The UI does nothing.

If a **last result** exists at launch (and no session), the app opens on `.result` with the result restored. That
result is kept until `restartAssessment()`. `submittedRequest` and `csvFileURL` are **not** restored after a relaunch.

## Loading / errors

`viewModel.analysisPhase`:

| Case | UI |
|---|---|
| `.idle` | — |
| `.connecting` | Generic loading text |
| `.modelWakingUp` | **Renamed from `.modelStarting`.** The Space is starting (up to about 1 min). `LoadingView` already shows `loading_model_starting`. |
| `.analysing` | The job is queued or running |
| `.failed(AnalysisError)` | Show `localization.localizedString(error.messageKey)` and a retry button calling `await viewModel.submitForAnalysis()`. |

`AnalysisError` replaces `MotivBotAPIError` in the UI:

| Case | `messageKey` |
|---|---|
| `.offline` | `error_server_unreachable` |
| `.serviceWakingUp` | `error_model_loading` |
| `.serviceBusy` | `error_service_busy` (new: rate limit or exhausted ZeroGPU quota) |
| `.unexpected` | `error_generic` |

`error_invalid_input` is no longer used by code. `LoadingView.errorKey(for:)` was changed to return `error.messageKey`,
and its `.modelStarting` case was renamed. Those were the only edits to view files (no visual change).

Retries (backoff 1 s → 2 s → 4 s … within about 90 s) happen inside the service. The UI never retries on its own except
through the retry button.

## Results

| Read | Meaning |
|---|---|
| `viewModel.prediction` / `viewModel.elevatedRisk` | As before. `elevatedRisk` is now stored (it survives relaunch). |
| `viewModel.resultSummary: ResultSummary?` | **Export model for PDF / image.** It has `date`, `language`, `dimensions: [DimensionResult]` (each with `dimension`, `level`, `titleKey`, `messageKey`, `tone`, and no percentages), `messages: [PredictionResponse.Message]` (`text`, `category`, `author?`), `elevatedRisk`, and `ResultSummary.disclaimerKey` (`result_disclaimer`). It contains no free text and no answers. |
| `PredictionResponse.Message.author: String?` | New. The quote's author, which can be shown as attribution. |

`restartAssessment()` cancels a running analysis, then deletes the saved session, the last result and the CSV, and unlocks the language.

## New localization keys (tr + fa, `/* MARK: integration */`)

`error_service_busy`, `emotion_wrong_language`, `emotion_too_long` (`%d`), `resume_title`, `resume_body`,
`resume_continue`, `resume_start_over`.

## Open questions

1. **Gender "prefer not to say" = 0.5.** The Space only accepts `Erkek, Kadın, Male, Female, مرد, زن` and inserts the
   string into the model prompt. The app still sends `Non-binary`, which the Space **rejects**. This needs a Space change first.
2. **CSV / research data.** The plan is to upload to a private HF Dataset through a small CPU Space. Consent, fields and
   ownership are pending.
3. **ZeroGPU quota.** Anonymous quota is per IP. No token will be embedded in the app.
