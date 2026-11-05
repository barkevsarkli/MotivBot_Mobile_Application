import SwiftUI

/// A full-width answer to a single-choice question. The whole row is the tap target (at least 56 pt).
struct AnswerOptionRow: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        Button(action: action) {
            HStack(spacing: DSSpacing.s) {
                SelectionIndicator(isSelected: isSelected)
                Text(title)
                    .font(DSFont.body)
                    .foregroundStyle(Color.dsTextPrimary)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .dsBodyText()
            }
            .padding(.horizontal, DSSpacing.m)
            .padding(.vertical, DSSpacing.s)
            .frame(minHeight: DSSize.answerOptionMinHeight)
            .background(isSelected ? Color.dsBrandSoft : Color.dsSurface, in: shape)
            .overlay(shape.strokeBorder(isSelected ? Color.dsBrand : Color.dsStroke, lineWidth: isSelected ? 1.5 : 1))
            .contentShape(shape)
        }
        .buttonStyle(.dsPressable)
        .animation(DSMotion.quick, value: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// A radio circle that turns into a check mark.
struct SelectionIndicator: View {
    let isSelected: Bool

    var body: some View {
        ZStack {
            Circle()
                .strokeBorder(isSelected ? Color.dsBrand : Color.dsTextTertiary, lineWidth: 2)
            if isSelected {
                Circle()
                    .fill(Color.dsBrand)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.dsOnBrand)
                    )
                    .transition(.scale(scale: 0.5).combined(with: .opacity))
            }
        }
        .frame(width: 26, height: 26)
        .accessibilityHidden(true)
    }
}

/// A compact choice used in wrapping rows, e.g. the reflection feelings or grid answers.
struct SelectableChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        let shape = Capsule()
        Button(action: action) {
            Text(title)
                .font(DSFont.label)
                .foregroundStyle(isSelected ? Color.dsOnBrand : Color.dsTextPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, DSSpacing.m)
                .padding(.vertical, DSSpacing.xs)
                .frame(maxWidth: .infinity, minHeight: DSSize.minTap)
                .background(isSelected ? Color.dsBrand : Color.dsSurface, in: shape)
                .overlay(shape.strokeBorder(isSelected ? Color.clear : Color.dsStroke, lineWidth: 1))
                .contentShape(shape)
        }
        .buttonStyle(.dsPressable)
        .animation(DSMotion.quick, value: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Lays chips out in one row when they fit and stacks them otherwise (e.g. at accessibility text sizes).
struct ChipGroup<Content: View>: View {
    @ViewBuilder let content: Content
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        if dynamicTypeSize.isAccessibilitySize {
            VStack(spacing: DSSpacing.xs) { content }
        } else {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: DSSpacing.xs) { content }
                VStack(spacing: DSSpacing.xs) { content }
            }
        }
    }
}

#if DEBUG
#Preview("Selection") {
    VStack(spacing: DSSpacing.s) {
        AnswerOptionRow(title: "Hiç", isSelected: false) {}
        AnswerOptionRow(title: "Günlerin yarısından fazla", isSelected: true) {}
        ChipGroup {
            SelectableChip(title: "Aynı", isSelected: false) {}
            SelectableChip(title: "Biraz iyi", isSelected: true) {}
            SelectableChip(title: "Daha iyi", isSelected: false) {}
        }
    }
    .padding()
    .background(AppBackground())
}
#endif
