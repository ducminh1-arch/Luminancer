import SwiftUI

@main
struct LuminancerApp: App {
    var body: some Scene {
        WindowGroup {
            SynthView()
                .preferredColorScheme(.dark)
                .statusBar(hidden: true)
        }
    }
}
