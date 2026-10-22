import SwiftUI
import UIKit

/// Transparent gesture recognizer overlay for Pan, Pinch, Rotate, and Double Tap
public struct GesturesView: UIViewRepresentable {
    @ObservedObject var viewModel: SynthViewModel
    
    public init(viewModel: SynthViewModel) {
        self.viewModel = viewModel
    }
    
    public func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isMultipleTouchEnabled = true
        
        // 1. Pan Gesture (1 finger)
        let panGesture = UIPanGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handlePan(_:)))
        panGesture.maximumNumberOfTouches = 1
        panGesture.delegate = context.coordinator
        view.addGestureRecognizer(panGesture)
        
        // 2. Pinch Gesture (Zoom)
        let pinchGesture = UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handlePinch(_:)))
        pinchGesture.delegate = context.coordinator
        view.addGestureRecognizer(pinchGesture)
        
        // 3. Rotation Gesture (Spin / Rotate)
        let rotateGesture = UIRotationGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleRotate(_:)))
        rotateGesture.delegate = context.coordinator
        view.addGestureRecognizer(rotateGesture)
        
        // 4. Double Tap (Reset)
        let doubleTapGesture = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTapGesture.numberOfTapsRequired = 2
        view.addGestureRecognizer(doubleTapGesture)
        
        return view
    }
    
    public func updateUIView(_ uiView: UIView, context: Context) {}
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(viewModel: viewModel)
    }
    
    public final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        let viewModel: SynthViewModel
        private var lastPanTranslation: CGPoint = .zero
        
        init(viewModel: SynthViewModel) {
            self.viewModel = viewModel
        }
        
        @objc func handlePan(_ gesture: UIPanGestureRecognizer) {
            let translation = gesture.translation(in: gesture.view)
            if gesture.state == .began {
                lastPanTranslation = translation
            } else if gesture.state == .changed {
                let delta = CGSize(
                    width: translation.x - lastPanTranslation.x,
                    height: translation.y - lastPanTranslation.y
                )
                lastPanTranslation = translation
                viewModel.onPanChanged(delta: delta)
            }
        }
        
        @objc func handlePinch(_ gesture: UIPinchGestureRecognizer) {
            if gesture.state == .changed {
                viewModel.onPinchChanged(scale: gesture.scale)
                gesture.scale = 1.0 // Reset incremental scale
            }
        }
        
        @objc func handleRotate(_ gesture: UIRotationGestureRecognizer) {
            if gesture.state == .changed {
                viewModel.onRotateChanged(rotation: gesture.rotation)
                gesture.rotation = 0.0 // Reset incremental rotation
            }
        }
        
        @objc func handleDoubleTap(_ gesture: UITapGestureRecognizer) {
            if gesture.state == .ended {
                viewModel.onDoubleTapReset()
            }
        }
        
        // Allow simultaneous pinch and rotation
        public func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            return true
        }
    }
}
