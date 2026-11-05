import Foundation

/// What a result is exported with (PDF, image). Holds no free text and no individual answers.
struct ResultSummary: Equatable {
    struct DimensionResult: Equatable, Identifiable {
        let dimension: WellBeingDimension
        let level: PredictionResponse.Level

        var id: String { dimension.id }
        var titleKey: String { dimension.titleKey }
        var messageKey: String { dimension.messageKey(for: level) }
        var tone: WellBeingDimension.Tone { dimension.tone(for: level) }
    }

    static let disclaimerKey = "result_disclaimer"

    let date: Date
    let language: AppLanguage
    /// Levels only, in model order; percentages are deliberately left out.
    let dimensions: [DimensionResult]
    let messages: [PredictionResponse.Message]
    /// Whether the support card was shown with the result.
    let elevatedRisk: Bool

    init(prediction: PredictionResponse, date: Date, language: AppLanguage, elevatedRisk: Bool) {
        self.date = date
        self.language = language
        self.dimensions = WellBeingDimension.allCases.compactMap { dimension in
            prediction.level(for: dimension).map { DimensionResult(dimension: dimension, level: $0) }
        }
        self.messages = prediction.messages
        self.elevatedRisk = elevatedRisk
    }
}
