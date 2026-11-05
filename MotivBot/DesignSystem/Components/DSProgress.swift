import SwiftUI

/// Overall progress split into one segment per section. Segment widths follow the number of
/// questions in each section. It fills from the leading edge, so it runs right to left in Persian.
struct SegmentedProgressBar: View {
    /// Questions per section.
    let weights: [Int]
    /// 0...1 per section.
    let fractions: [Double]
    let accessibilityLabel: String
    let accessibilityValue: String

    private let spacing: CGFloat = 4

    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: spacing) {
                ForEach(weights.indices, id: \.self) { index in
                    segment(width: segmentWidth(index, in: geometry.size.width), fraction: fraction(index))
                }
            }
        }
        .frame(height: 6)
        .animation(DSMotion.soft, value: fractions)
        .accessibilityElement()
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue(accessibilityValue)
    }

    private func segmentWidth(_ index: Int, in totalWidth: CGFloat) -> CGFloat {
        let total = CGFloat(max(weights.reduce(0, +), 1))
        let gaps = spacing * CGFloat(max(weights.count - 1, 0))
        let available = max(totalWidth - gaps, 0)
        return available * CGFloat(weights[index]) / total
    }

    private func fraction(_ index: Int) -> CGFloat {
        guard index < fractions.count else { return 0 }
        return CGFloat(min(max(fractions[index], 0), 1))
    }

    private func segment(width: CGFloat, fraction: CGFloat) -> some View {
        ZStack(alignment: .leading) {
            Capsule().fill(Color.dsBrandSoft)
            Capsule()
                .fill(Color.dsBrand)
                .frame(width: width * fraction)
        }
        .frame(width: width)
    }
}

/// A small ring, e.g. for how far a saved session got.
struct ProgressRing: View {
    let fraction: Double
    var lineWidth: CGFloat = 6
    @Environment(\.layoutDirection) private var layoutDirection

    var body: some View {
        ZStack {
            Circle().stroke(Color.dsBrandSoft, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(Color.dsBrand, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                // Runs counter-clockwise in right-to-left languages.
                .scaleEffect(x: layoutDirection == .rightToLeft ? -1 : 1)
        }
        .accessibilityHidden(true)
    }
}

/// Three steps for a result level: filled steps show how strongly a dimension is present.
/// Filled and outlined steps stay distinguishable without colour, e.g. when printed in black and white.
struct LevelIndicator: View {
    /// 1...3
    let filledSteps: Int
    let color: Color

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3, id: \.self) { step in
                let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
                Group {
                    if step < filledSteps {
                        shape.fill(color)
                    } else {
                        shape.strokeBorder(color.opacity(0.6), lineWidth: 1.25)
                    }
                }
                .frame(width: 18, height: 8)
            }
        }
        .accessibilityHidden(true)
    }
}

#if DEBUG
#Preview("Progress") {
    VStack(spacing: DSSpacing.l) {
        SegmentedProgressBar(weights: [6, 10, 7, 10], fractions: [1, 0.4, 0, 0], accessibilityLabel: "Progress", accessibilityValue: "10 of 33")
        ProgressRing(fraction: 0.42).frame(width: 64, height: 64)
        LevelIndicator(filledSteps: 2, color: .dsGentle)
    }
    .padding()
}
#endif
