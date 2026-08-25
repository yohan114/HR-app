import Foundation
import LocalAuthentication
import Security

/// Stores the refresh token sealed behind Face ID or Touch ID.
///
/// ## The problem this solves
///
/// The most-cited complaint about the product we are replacing is that biometric sign-in does not
/// actually remove the password: *"fingerprint login is enabled, but username and password are
/// still required… like no one tested this app."* That is what happens when biometrics are treated
/// as a UI gesture — check the face, then perform a normal password login anyway.
///
/// Here the biometric check is load-bearing. The refresh token is stored in the Keychain under an
/// access control flagged `.biometryCurrentSet`, so the Secure Enclave will not release it without
/// a successful user presence check. There is no code path that reads the token without one,
/// because the Keychain simply refuses to return the item.
///
/// ## Why `.biometryCurrentSet` rather than `.biometryAny`
///
/// `.biometryCurrentSet` invalidates the item when the device's enrolled biometrics change. That
/// is deliberate and is the security property that matters: with `.biometryAny`, someone with
/// access to an unlocked device could enrol their own face and inherit the previous owner's
/// session. The cost is that a legitimate re-enrolment forces one password sign-in, which is why
/// ``UnsealResult/biometryChanged`` exists as a distinct outcome the UI can explain.
///
/// Mirrors `SecureTokenStore` on Android. See docs/sync-protocol.md §8.
public actor SecureTokenStore {
    private let service: String
    private let account = "refresh-token"

    public init(service: String = "io.hrapp.tokens") {
        self.service = service
    }

    public var hasSealedToken: Bool {
        var query = baseQuery()
        query[kSecReturnData as String] = false
        // Do not prompt merely to discover whether an item exists — the caller is deciding which
        // sign-in form to show, not authenticating.
        query[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUIFail

        let status = SecItemCopyMatching(query as CFDictionary, nil)
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    /// Seals the refresh token, replacing any existing one.
    ///
    /// Writing does not require a biometric prompt — only reading does. Prompting on write would
    /// mean asking for a face scan immediately after the user has just typed their password,
    /// which is friction for no security gain.
    public func seal(refreshToken: String) throws {
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            .biometryCurrentSet,
            &error
        ) else {
            throw SecureStoreError.accessControlUnavailable(error?.takeRetainedValue())
        }

        // Delete first: SecItemUpdate cannot change an item's access control.
        SecItemDelete(baseQuery() as CFDictionary)

        var query = baseQuery()
        query[kSecValueData as String] = Data(refreshToken.utf8)
        query[kSecAttrAccessControl as String] = access

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw SecureStoreError.keychain(status)
        }
    }

    /// Unseals the refresh token, prompting for biometrics.
    ///
    /// - Parameter reason: shown in the system prompt. Must explain what is about to happen — a
    ///   vague reason is a dark pattern and iOS reviewers reject it.
    public func unseal(reason: String) async -> UnsealResult {
        let context = LAContext()
        context.localizedReason = reason
        // No password fallback here: the caller offers "Use password" as an explicit alternative
        // route, which lands on the normal sign-in form rather than the device passcode. Falling
        // back to the passcode would weaken the guarantee — a shoulder-surfed passcode should not
        // unlock someone's payroll.
        context.localizedFallbackTitle = ""

        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecUseAuthenticationContext as String] = context

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)

        switch status {
        case errSecSuccess:
            guard let data = item as? Data, let token = String(data: data, encoding: .utf8) else {
                return .failed("Stored token could not be decoded")
            }
            return .success(token)

        case errSecItemNotFound:
            // Either never enrolled, or the item was invalidated by a biometric change — iOS
            // reports both as "not found". We cannot distinguish them, so the UI treats this as
            // "sign in with your password", which is correct either way.
            return .noSealedToken

        case errSecUserCanceled:
            return .cancelled

        case errSecAuthFailed:
            return .biometryChanged

        default:
            return .failed("Keychain error \(status)")
        }
    }

    /// Removes the sealed token.
    ///
    /// Called on sign-out and on device revocation. A revoked device must retain nothing usable.
    public func clear() {
        SecItemDelete(baseQuery() as CFDictionary)
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

public enum UnsealResult: Sendable, Equatable {
    case success(String)
    /// Nothing enrolled on this device, or the item was invalidated. Show the password form.
    case noSealedToken
    /// The user dismissed the prompt. Not an error — do not show one.
    case cancelled
    /// Device biometrics changed; the token is unrecoverable. Explain, then require a password.
    case biometryChanged
    case failed(String)
}

public enum SecureStoreError: Error {
    case keychain(OSStatus)
    case accessControlUnavailable(CFError?)
}
