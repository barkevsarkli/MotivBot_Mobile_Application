import SwiftUI

struct ThemeToggleView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @State private var isRotating = false
    
    var body: some View {
        Button(action: {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                isRotating.toggle()
            }
            viewModel.toggleTheme()
        }) {
            ZStack {
                // Glassmorphism background
                Circle()
                    .fill(.ultraThinMaterial)
                    .frame(width: 44, height: 44)
                    .shadow(color: viewModel.isDarkMode
                        ? Color.purple.opacity(0.3)
                        : Color.orange.opacity(0.3),
                        radius: 8, x: 0, y: 4)
                
                Image(systemName: viewModel.isDarkMode ? "moon.stars.fill" : "sun.max.fill")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(
                        viewModel.isDarkMode
                            ? LinearGradient(colors: [.purple, .blue], startPoint: .topLeading, endPoint: .bottomTrailing)
                            : LinearGradient(colors: [.orange, .yellow], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .rotationEffect(.degrees(isRotating ? 360 : 0))
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(localization.localizedString("theme_toggle"))
    }
}
