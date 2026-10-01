import SwiftUI

struct EmotionInputView: View {
    
    @Environment(WellBeingViewModel.self) var viewModel
    @Environment(LocalizationManager.self) var localization
    @FocusState private var isTextEditorFocused: Bool
    @State private var showContent = false
    
    var body: some View {
        @Bindable var viewModel = viewModel
        
        ScrollView {
            VStack(spacing: 24) {
                Spacer().frame(height: 60)
                
                // Header
                VStack(spacing: 12) {
                    // Animated lotus icon
                    Image(systemName: "leaf.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .scaleEffect(showContent ? 1.0 : 0.5)
                        .opacity(showContent ? 1 : 0)
                    
                    Text(localization.localizedString("app_title"))
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(.primary)
                        .opacity(showContent ? 1 : 0)
                        .offset(y: showContent ? 0 : 20)
                    
                    Text(localization.localizedString("emotion_prompt"))
                        .font(.system(size: 16, weight: .medium, design: .rounded))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                        .opacity(showContent ? 1 : 0)
                        .offset(y: showContent ? 0 : 15)
                }
                
                // Text Editor Card
                VStack(alignment: .leading, spacing: 8) {
                    Text(localization.localizedString("your_thoughts"))
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundColor(.secondary)
                        .padding(.leading, 4)
                    
                    TextEditor(text: $viewModel.emotionText)
                        .focused($isTextEditorFocused)
                        .frame(minHeight: 160, maxHeight: 220)
                        .padding(12)
                        .scrollContentBackground(.hidden)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(.ultraThinMaterial)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(
                                    isTextEditorFocused
                                        ? LinearGradient(colors: [.purple.opacity(0.6), .blue.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing)
                                        : LinearGradient(colors: [.gray.opacity(0.2), .gray.opacity(0.2)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                    lineWidth: isTextEditorFocused ? 2 : 1
                                )
                        )
                        .overlay(alignment: .topLeading, content: {
                            if viewModel.emotionText.isEmpty {
                                Text(localization.localizedString("emotion_placeholder"))
                                    .foregroundColor(.gray.opacity(0.5))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 20)
                                    .allowsHitTesting(false)
                            }
                        })
                        .font(.system(size: 16, design: .rounded))
                }
                .padding(.horizontal, 20)
                .opacity(showContent ? 1 : 0)
                .offset(y: showContent ? 0 : 30)
                
                // Character hint
                Text(localization.localizedString("emotion_hint"))
                    .font(.system(size: 13, design: .rounded))
                    .foregroundColor(.secondary.opacity(0.7))
                    .opacity(showContent ? 1 : 0)
                
                // Next button
                Button(action: {
                    isTextEditorFocused = false
                    viewModel.submitEmotionAndProceed()
                }) {
                    HStack(spacing: 8) {
                        Text(localization.localizedString("next_button"))
                            .font(.system(size: 17, weight: .semibold, design: .rounded))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 15, weight: .semibold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 54)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(
                                viewModel.canProceedEmotion
                                    ? LinearGradient(colors: [Color(red: 0.55, green: 0.48, blue: 0.85), Color(red: 0.42, green: 0.68, blue: 0.84)], startPoint: .leading, endPoint: .trailing)
                                    : LinearGradient(colors: [.gray.opacity(0.4), .gray.opacity(0.3)], startPoint: .leading, endPoint: .trailing)
                            )
                    )
                    .shadow(color: viewModel.canProceedEmotion ? Color(red: 0.55, green: 0.48, blue: 0.85).opacity(0.4) : .clear, radius: 12, y: 6)
                }
                .disabled(!viewModel.canProceedEmotion)
                .padding(.horizontal, 20)
                .opacity(showContent ? 1 : 0)
                .offset(y: showContent ? 0 : 20)
                
                Spacer().frame(height: 40)
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            withAnimation(.easeOut(duration: 0.8)) {
                showContent = true
            }
        }
    }
}

#Preview {
    EmotionInputView()
        .environment(WellBeingViewModel())
        .environment(LocalizationManager())
}
