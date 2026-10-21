import SwiftUI

// MARK: - Background

/// The calm, static gradient behind every screen.
struct AppBackground: View {
    var body: some View {
        LinearGradient(
            colors: [.dsBackgroundTop, .dsBackgroundBottom],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}

// MARK: - Card

private struct DSCardModifier: ViewModifier {
    let fill: Color
    let stroke: Color
    let padding: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(fill, in: RoundedRectangle(cornerRadius: DSRadius.large, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: DSRadius.large, style: .continuous)
                    .strokeBorder(stroke, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 12, y: 4)
    }
}

extension View {
    /// A solid card. Cards are never translucent, so text on them always keeps its contrast.
    func dsCard(fill: Color = .dsSurface, stroke: Color = .dsStroke, padding: CGFloat = DSSpacing.l - 4) -> some View {
        modifier(DSCardModifier(fill: fill, stroke: stroke, padding: padding))
    }
}

// MARK: - Glass

private struct DSGlassModifier<S: Shape>: ViewModifier {
    let shape: S
    let interactive: Bool
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    func body(content: Content) -> some View {
        if reduceTransparency {
            content
                .background(Color.dsSurface, in: shape)
                .overlay(shape.stroke(Color.dsStroke, lineWidth: 1))
        } else {
            content.glassEffect(interactive ? .regular.interactive() : .regular, in: shape)
        }
    }
}

extension View {
    /// Liquid Glass for floating controls only. With Reduce Transparency it becomes an opaque surface.
    func dsGlass<S: Shape>(in shape: S, interactive: Bool = false) -> some View {
        modifier(DSGlassModifier(shape: shape, interactive: interactive))
    }
}

// MARK: - Appear animation

private struct DSAppearModifier: ViewModifier {
    let delay: Double
    @State private var isShown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(isShown ? 1 : 0)
            .offset(y: isShown || reduceMotion ? 0 : 14)
            .onAppear {
                withAnimation(DSMotion.gentle.delay(reduceMotion ? 0 : delay)) {
                    isShown = true
                }
            }
    }
}

extension View {
    /// Fades the view in and lifts it slightly. Pass increasing delays to stagger a screen's sections.
    func dsAppear(delay: Double = 0) -> some View {
        modifier(DSAppearModifier(delay: delay))
    }
}
