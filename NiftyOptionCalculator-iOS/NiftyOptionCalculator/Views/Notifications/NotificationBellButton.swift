import SwiftUI

struct NotificationBellButton: View {
    let unreadCount: Int
    let action: () -> Void

    var body: some View {
        VStack {
            HStack {
                Spacer()

                Button(action: action) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "bell.fill")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 36, height: 36)
                            .background {
                                Circle()
                                    .fill(Theme.surface)
                                    .overlay {
                                        Circle()
                                            .stroke(Color.white.opacity(0.08), lineWidth: 1)
                                    }
                                    .shadow(color: .black.opacity(0.2), radius: 4, y: 2)
                            }

                        if unreadCount > 0 {
                            Text(unreadCount > 99 ? "99+" : "\(unreadCount)")
                                .font(.system(size: 9, weight: .heavy, design: .rounded))
                                .foregroundColor(.white)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Capsule().fill(Theme.accentRed))
                                .offset(x: 4, y: -4)
                        }
                    }
                }
                .buttonStyle(.plain)
                .padding(.trailing, 16)
            }
            .padding(.top, 8)

            Spacer()
        }
    }
}
