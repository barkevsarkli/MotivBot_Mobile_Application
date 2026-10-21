import SwiftUI

/// The text and optional symbol of a button. Use `.forward` / `.backward` symbols so arrows mirror in Persian.
struct DSButtonLabel: View {
    let title: String
    var systemImage: String? = nil

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            Text(title)
                .multilineTextAlignment(.center)
            if let systemImage {
                Image(systemName: systemImage)
                    .accessibilityHidden(true)
            }
        }
    }
}

/// The one main action on a screen.
struct DSPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        configuration.label
            .font(DSFont.heading)
            .foregroundStyle(isEnabled ? Color.dsOnBrand : Color.dsTextTertiary)
            .padding(.horizontal, DSSpacing.l)
            .padding(.vertical, DSSpacing.s)
            .frame(maxWidth: .infinity, minHeight: DSSize.buttonMinHeight)
            .background(isEnabled ? Color.dsBrand : Color.dsSurfaceMuted, in: shape)
            .overlay(shape.strokeBorder(isEnabled ? Color.clear : Color.dsStroke, lineWidth: 1))
            .contentShape(shape)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(DSMotion.quick, value: configuration.isPressed)
            .animation(DSMotion.standard, value: isEnabled)
    }
}

/// A quieter action next to or below the primary one.
struct DSSecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: DSRadius.medium, style: .continuous)
        configuration.label
            .font(DSFont.heading)
            .foregroundStyle(isEnabled ? Color.dsBrand : Color.dsTextTertiary)
            .padding(.horizontal, DSSpacing.m)
            .padding(.vertical, DSSpacing.s)
            .frame(maxWidth: .infinity, minHeight: DSSize.buttonMinHeight)
            .background(Color.dsBrandSoft, in: shape)
            .contentShape(shape)
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(DSMotion.quick, value: configuration.isPressed)
    }
}

/// A text-only action, at least 44 pt tall.
struct DSTertiaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(DSFont.label)
            .foregroundStyle(Color.dsBrand)
            .padding(.horizontal, DSSpacing.s)
            .frame(minHeight: DSSize.minTap)
            .contentShape(Rectangle())
            .opacity(configuration.isPressed ? 0.6 : 1)
            .animation(DSMotion.quick, value: configuration.isPressed)
    }
}

/// Press feedback for custom tappable content such as answer options.
struct DSPressableStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(DSMotion.quick, value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == DSPrimaryButtonStyle {
    static var dsPrimary: DSPrimaryButtonStyle { DSPrimaryButtonStyle() }
}

extension ButtonStyle where Self == DSSecondaryButtonStyle {
    static var dsSecondary: DSSecondaryButtonStyle { DSSecondaryButtonStyle() }
}

extension ButtonStyle where Self == DSTertiaryButtonStyle {
    static var dsTertiary: DSTertiaryButtonStyle { DSTertiaryButtonStyle() }
}

extension ButtonStyle where Self == DSPressableStyle {
    static var dsPressable: DSPressableStyle { DSPressableStyle() }
}

// MARK: - Glass icon button

/// A round 44 pt control that floats over the content, e.g. back or the theme menu.
struct GlassIconButton: View {
    let systemImage: String
    let accessibilityLabel: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(.body, weight: .semibold))
                .foregroundStyle(Color.dsTextPrimary)
                .frame(width: DSSize.minTap, height: DSSize.minTap)
                .dsGlass(in: Circle(), interactive: true)
                .contentShape(Circle())
        }
        .buttonStyle(.dsPressable)
        .accessibilityLabel(accessibilityLabel)
    }
}

#if DEBUG
#Preview("Buttons") {
    VStack(spacing: DSSpacing.m) {
        Button {} label: { DSButtonLabel(title: "İleri", systemImage: "arrow.forward") }
            .buttonStyle(.dsPrimary)
        Button {} label: { DSButtonLabel(title: "İleri") }
            .buttonStyle(.dsPrimary)
            .disabled(true)
        Button {} label: { DSButtonLabel(title: "Baştan başla") }
            .buttonStyle(.dsSecondary)
        Button("Vazgeç") {}
            .buttonStyle(.dsTertiary)
        GlassIconButton(systemImage: "chevron.backward", accessibilityLabel: "Geri") {}
    }
    .padding()
    .background(AppBackground())
}
#endif
