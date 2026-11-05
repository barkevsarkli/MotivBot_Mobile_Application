import SwiftUI
import UIKit

/// The formats the results summary can be exported in.
enum ResultExportFormat: String, Identifiable {
    case pdf
    case image

    var id: String { rawValue }
    var fileExtension: String { self == .pdf ? "pdf" : "png" }
}

/// A rendered export, ready for the share sheet.
struct ResultExportFile: Identifiable {
    let url: URL
    var id: URL { url }
}

/// Renders `ResultSummaryPage` to a one-page A4 PDF or a PNG with `ImageRenderer`.
/// Files go to a private temporary folder and are deleted when the share sheet closes.
enum ResultExporter {

    enum ExportError: Error {
        case renderingFailed
    }

    nonisolated static var directory: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("ResultExport", isDirectory: true)
    }

    static func makeFile(_ format: ResultExportFormat, summary: ResultSummary, localization: LocalizationManager) throws -> ResultExportFile {
        removeFiles()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)

        let day = summary.date.formatted(.iso8601.year().month().day())
        let url = directory.appendingPathComponent("MotivBot-\(day).\(format.fileExtension)")
        let page = ResultSummaryPage(summary: summary, localization: localization)

        let data: Data
        switch format {
        case .pdf: data = try pdfData(page)
        case .image: data = try pngData(page)
        }
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        return ResultExportFile(url: url)
    }

    /// Deletes every exported file.
    nonisolated static func removeFiles() {
        try? FileManager.default.removeItem(at: directory)
    }

    private static func pdfData(_ page: ResultSummaryPage) throws -> Data {
        let renderer = ImageRenderer(content: page)
        renderer.proposedSize = ProposedViewSize(width: ResultSummaryPage.pageSize.width, height: nil)

        let data = NSMutableData()
        var rendered = false
        renderer.render { size, draw in
            var mediaBox = CGRect(origin: .zero, size: ResultSummaryPage.pageSize)
            guard let consumer = CGDataConsumer(data: data as CFMutableData),
                  let context = CGContext(consumer: consumer, mediaBox: &mediaBox, nil) else { return }
            // Keep it on one page: shrink long content to fit, and pin it to the top of the page.
            let scale = min(1, mediaBox.height / max(size.height, 1))
            context.beginPDFPage(nil)
            context.translateBy(
                x: (mediaBox.width - size.width * scale) / 2,
                y: mediaBox.height - size.height * scale
            )
            context.scaleBy(x: scale, y: scale)
            draw(context)
            context.endPDFPage()
            context.closePDF()
            rendered = true
        }
        guard rendered else { throw ExportError.renderingFailed }
        return data as Data
    }

    private static func pngData(_ page: ResultSummaryPage) throws -> Data {
        let renderer = ImageRenderer(content: page)
        renderer.proposedSize = ProposedViewSize(width: ResultSummaryPage.pageSize.width, height: nil)
        renderer.scale = 2
        renderer.isOpaque = true
        guard let data = renderer.uiImage?.pngData() else { throw ExportError.renderingFailed }
        return data
    }
}

/// The system share sheet for an exported file. Deletes the file once the sheet closes.
struct ExportShareSheet: UIViewControllerRepresentable {
    let file: ResultExportFile

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [file.url], applicationActivities: nil)
        controller.completionWithItemsHandler = { _, _, _, _ in
            ResultExporter.removeFiles()
        }
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
