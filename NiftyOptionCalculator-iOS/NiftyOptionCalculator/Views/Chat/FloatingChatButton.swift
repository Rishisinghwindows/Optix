//
//  FloatingChatButton.swift
//  NiftyOptionCalculator
//
//  Floating action button to open chat - draggable anywhere on screen
//

import SwiftUI

struct FloatingChatButton: View {
    @State private var showChat = false
    @State private var isPressed = false
    @State private var isDragging = false

    // Position state - stored in AppStorage for persistence
    @AppStorage("chatButtonX") private var positionX: Double = -1
    @AppStorage("chatButtonY") private var positionY: Double = -1

    @State private var dragOffset: CGSize = .zero

    private let buttonSize: CGFloat = 56
    private let edgePadding: CGFloat = 16

    var body: some View {
        GeometryReader { geometry in
            let safeArea = geometry.safeAreaInsets
            let screenWidth = geometry.size.width
            let screenHeight = geometry.size.height

            // Default position (bottom right, above tab bar)
            let defaultX = screenWidth - buttonSize - edgePadding
            let defaultY = screenHeight - buttonSize - 100

            // Current position
            let currentX = positionX < 0 ? defaultX : positionX
            let currentY = positionY < 0 ? defaultY : positionY

            ZStack {
                // Background with gradient
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Theme.accentPrimary, Theme.accentSecondary],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: buttonSize, height: buttonSize)
                    .shadow(color: Theme.accentPrimary.opacity(0.4), radius: isDragging ? 12 : 8, x: 0, y: 4)

                // Icon
                Image(systemName: "sparkles")
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundColor(.white)
            }
            .scaleEffect(isPressed ? 0.9 : (isDragging ? 1.1 : 1.0))
            .animation(.spring(response: 0.3), value: isPressed)
            .animation(.spring(response: 0.3), value: isDragging)
            .position(
                x: currentX + buttonSize / 2 + dragOffset.width,
                y: currentY + buttonSize / 2 + dragOffset.height
            )
            .gesture(
                DragGesture(minimumDistance: 5)
                    .onChanged { value in
                        isDragging = true
                        dragOffset = value.translation
                    }
                    .onEnded { value in
                        isDragging = false

                        // Calculate new position
                        var newX = currentX + value.translation.width
                        var newY = currentY + value.translation.height

                        // Clamp to screen bounds
                        let minX = edgePadding
                        let maxX = screenWidth - buttonSize - edgePadding
                        let minY = safeArea.top + edgePadding
                        let maxY = screenHeight - buttonSize - safeArea.bottom - 90 // Above tab bar

                        newX = max(minX, min(maxX, newX))
                        newY = max(minY, min(maxY, newY))

                        // Snap to nearest edge horizontally
                        let snapToLeft = newX < screenWidth / 2
                        newX = snapToLeft ? minX : maxX

                        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                            positionX = newX
                            positionY = newY
                            dragOffset = .zero
                        }
                    }
            )
            .simultaneousGesture(
                TapGesture()
                    .onEnded {
                        if !isDragging {
                            showChat = true
                        }
                    }
            )
            .onLongPressGesture(minimumDuration: 0.1, pressing: { pressing in
                isPressed = pressing
            }, perform: {})
        }
        .fullScreenCover(isPresented: $showChat) {
            ChatView()
        }
    }
}

// MARK: - Chat Button Overlay Modifier

struct ChatButtonOverlay: ViewModifier {
    func body(content: Content) -> some View {
        ZStack {
            content
            FloatingChatButton()
        }
    }
}

extension View {
    func withChatButton() -> some View {
        modifier(ChatButtonOverlay())
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Theme.backgroundPrimary
            .ignoresSafeArea()

        Text("Main Content")
            .foregroundColor(Theme.textPrimary)
    }
    .withChatButton()
}
