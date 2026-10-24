import Foundation
import LocalAuthentication
import SwiftUI
import UIKit

final class AppLockManager: ObservableObject {
    static let shared = AppLockManager()

    @AppStorage("appLockEnabled") var appLockEnabled: Bool = false
    @AppStorage("lockTimeoutMinutes") var lockTimeoutMinutes: Int = 0

    @Published var isLocked: Bool = false

    private var lastBackgroundTime: Date?

    var canUseBiometric: Bool {
        let context = LAContext()
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    var biometricType: String {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return "Biometrics"
        }
        switch context.biometryType {
        case .faceID:
            return "Face ID"
        case .touchID:
            return "Touch ID"
        default:
            return "Biometrics"
        }
    }

    var biometricIcon: String {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return "lock.fill"
        }
        switch context.biometryType {
        case .faceID:
            return "faceid"
        case .touchID:
            return "touchid"
        default:
            return "lock.fill"
        }
    }

    private init() {
        // A cold launch must start locked: willEnterForeground never fires for
        // the first launch, so the lock would otherwise only engage after a
        // background/foreground cycle.
        isLocked = UserDefaults.standard.bool(forKey: "appLockEnabled")
        setupNotifications()
    }

    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(didEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(willEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    @objc private func didEnterBackground() {
        guard appLockEnabled else { return }
        lastBackgroundTime = Date()
    }

    @objc private func willEnterForeground() {
        guard appLockEnabled else { return }
        guard let backgroundTime = lastBackgroundTime else {
            isLocked = true
            return
        }
        let elapsed = Date().timeIntervalSince(backgroundTime)
        let timeoutSeconds = TimeInterval(lockTimeoutMinutes * 60)
        if elapsed >= timeoutSeconds {
            isLocked = true
        }
    }

    func authenticate() {
        let context = LAContext()
        var error: NSError?
        // .deviceOwnerAuthentication = biometrics with the device passcode as
        // fallback, so a failed Face ID (or one removed after App Lock was
        // turned on) can't lock the user out now that cold launches lock too.
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            // No passcode on the device at all, so nothing can satisfy the
            // lock; staying locked would shut the user out of their data.
            isLocked = false
            return
        }
        context.evaluatePolicy(
            .deviceOwnerAuthentication,
            localizedReason: "Unlock PennyWise to access your financial data"
        ) { [weak self] success, _ in
            DispatchQueue.main.async {
                if success {
                    self?.isLocked = false
                }
            }
        }
    }

    func lockIfEnabled() {
        if appLockEnabled {
            isLocked = true
        }
    }
}
