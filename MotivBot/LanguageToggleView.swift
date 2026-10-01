import SwiftUI

struct LanguageToggleView: View {
    
    @Environment(LocalizationManager.self) var localization
    @State private var isAnimating = false
    
    var body: some View {
        Button(action: {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                isAnimating.toggle()
            }
            localization.toggleLanguage()
        }) {
            ZStack {
                // Glassmorphism background
                Circle()
                    .fill(.ultraThinMaterial)
                    .frame(width: 44, height: 44)
                    .shadow(color: Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.25),
                            radius: 8, x: 0, y: 4)
                
                // Country label
                Text(localization.currentLanguage.displayLabel)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .scaleEffect(isAnimating ? 1.15 : 1.0)
                    .animation(.spring(response: 0.3, dampingFraction: 0.5), value: isAnimating)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(localization.localizedString("theme_toggle"))
    }
}
