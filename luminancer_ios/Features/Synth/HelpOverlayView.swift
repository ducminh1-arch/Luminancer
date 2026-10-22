import SwiftUI

public struct HelpOverlayView: View {
    @Binding var isPresented: Bool
    
    public init(isPresented: Binding<Bool>) {
        self._isPresented = isPresented
    }
    
    public var body: some View {
        ZStack {
            Color.black.opacity(0.85)
                .ignoresSafeArea()
                .onTapGesture { isPresented = false }
            
            VStack(alignment: .leading, spacing: 18) {
                // Title
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("LUMINANCER GUIDE")
                            .font(.system(size: 16, weight: .black, design: .monospaced))
                            .foregroundColor(.cyan)
                        Text("ANALOG VIDEO SYNTHESIZER")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.gray)
                    }
                    Spacer()
                    Button(action: { isPresented = false }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
                
                Divider().background(Color.white.opacity(0.2))
                
                // Gestures Section
                Text("GESTURE CONTROLS")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.yellow)
                
                VStack(spacing: 10) {
                    GestureRow(icon: "hand.point.up.left.fill", title: "1-FINGER PAN", desc: "Controls feedback drift offset & direction")
                    GestureRow(icon: "arrow.up.left.and.down.right.magnifyingglass", title: "PINCH", desc: "Zooms feedback loop in & out (recursive tunnel)")
                    GestureRow(icon: "arrow.triangle.2.circlepath", title: "2-FINGER ROTATE", desc: "Spins and twists feedback trails")
                    GestureRow(icon: "hand.tap.fill", title: "DOUBLE TAP", desc: "Resets zoom, rotation, and pan to default")
                }
                
                Divider().background(Color.white.opacity(0.2))
                
                // Core Parameters Section
                Text("SYNTH PARAMETERS")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.orange)
                
                VStack(spacing: 8) {
                    ParamRow(name: "THRESHOLD", desc: "Luminance cutoff limit for light synthesis")
                    ParamRow(name: "DECAY", desc: "Feedback persistence and trail lingering time")
                    ParamRow(name: "TRAILS", desc: "Intensity multiplier for recursive feedback buffer")
                    ParamRow(name: "CHAN / COLOR", desc: "Switches light detection and spectrum strobe modes")
                    ParamRow(name: "BLEND", desc: "Composites synth output with original video")
                }
                
                Spacer()
                
                Button(action: { isPresented = false }) {
                    Text("RESUME SYNTHESIS")
                        .font(.system(size: 13, weight: .black, design: .monospaced))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.cyan)
                        .cornerRadius(8)
                }
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(white: 0.14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.cyan.opacity(0.5), lineWidth: 1)
                    )
            )
            .padding(.horizontal, 24)
            .padding(.vertical, 40)
        }
    }
}

private struct GestureRow: View {
    let icon: String
    let title: String
    let desc: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.cyan)
                .frame(width: 24)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(.white)
                Text(desc)
                    .font(.system(size: 10, design: .default))
                    .foregroundColor(.gray)
            }
        }
    }
}

private struct ParamRow: View {
    let name: String
    let desc: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text(name)
                .font(.system(size: 10, weight: .bold, design: .monospaced))
                .foregroundColor(.cyan)
                .frame(width: 80, alignment: .leading)
            Text(desc)
                .font(.system(size: 10, design: .default))
                .foregroundColor(.white.opacity(0.8))
            Spacer()
        }
    }
}
