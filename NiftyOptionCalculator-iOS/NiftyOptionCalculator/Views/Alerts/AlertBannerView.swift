import SwiftUI

// MARK: - Alert Banner View

struct AlertBannerView: View {
    let alert: SmartAlert
    let onDismiss: () -> Void
    var onTap: (() -> Void)?

    @State private var offset: CGFloat = -120
    @State private var dragOffset: CGFloat = 0

    var body: some View {
        HStack(spacing: 12) {
            // Severity + Type Icon
            ZStack {
                Circle()
                    .fill(alert.type.color.opacity(0.2))
                    .frame(width: 40, height: 40)

                Image(systemName: alert.type.icon)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(alert.type.color)
            }

            // Content
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(alert.title)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                        .lineLimit(1)

                    Spacer()

                    // Severity badge
                    Text(alert.severity.label)
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundColor(alert.severity.color)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background {
                            Capsule()
                                .fill(alert.severity.color.opacity(0.15))
                        }
                }

                Text(alert.message)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(2)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(alert.type.color.opacity(0.3), lineWidth: 1)
                }
                .shadow(color: alert.type.color.opacity(0.15), radius: 12, x: 0, y: 4)
                .shadow(color: .black.opacity(0.3), radius: 16, x: 0, y: 8)
        }
        .padding(.horizontal, 16)
        .offset(y: offset + dragOffset)
        .gesture(
            DragGesture()
                .onChanged { value in
                    if value.translation.height < 0 {
                        dragOffset = value.translation.height
                    }
                }
                .onEnded { value in
                    if value.translation.height < -30 {
                        // Swipe up to dismiss
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            offset = -200
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            onDismiss()
                        }
                    } else {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            dragOffset = 0
                        }
                    }
                }
        )
        .onTapGesture {
            onTap?()
        }
        .onAppear {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) {
                offset = 0
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(alert.severity.label) alert: \(alert.title). \(alert.message)")
        .accessibilityHint("Swipe up to dismiss, tap for details")
    }
}

// MARK: - Alert Banner Container

/// Overlay container that manages banner display based on AlertManager state
struct AlertBannerOverlay: View {
    @ObservedObject var alertManager: AlertManager

    var body: some View {
        VStack {
            if let alert = alertManager.currentBanner {
                AlertBannerView(
                    alert: alert,
                    onDismiss: {
                        alertManager.dismissBanner()
                    }
                )
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(100)
            }

            Spacer()
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: alertManager.currentBanner?.id)
    }
}
