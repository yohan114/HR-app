import Foundation

/// Where the API is and who we are talking to it as.
///
/// A value rather than a global: tests construct one pointing at a stub, and the app builds one
/// from its build configuration. Mirrors Android's `BuildConfig.API_BASE_URL`, which is set per
/// build type in `android/app/build.gradle.kts`.
public struct APIConfiguration: Sendable {
    public let baseURL: URL

    /// Sent as `X-Tenant-Code` on unauthenticated endpoints only.
    ///
    /// Once a token exists the server takes the tenant from the `tenant_id` claim and a
    /// conflicting header is rejected with `TENANT_MISMATCH` — so this is deliberately not
    /// attached to authenticated requests. See spec/openapi.yaml, "Tenancy".
    public let tenantCode: String?

    public let timeout: TimeInterval

    public init(baseURL: URL, tenantCode: String? = nil, timeout: TimeInterval = 30) {
        self.baseURL = baseURL
        self.tenantCode = tenantCode
        self.timeout = timeout
    }

    /// Resolves a path such as `/v1/employees/me` against the base URL.
    ///
    /// Trims a leading slash so that a base URL carrying a path prefix is not discarded —
    /// `URL(string:relativeTo:)` treats a leading slash as "from the root of the host", which
    /// would silently drop a prefix like `/api` in a reverse-proxied deployment.
    func url(forPath path: String) -> URL? {
        let trimmed = path.hasPrefix("/") ? String(path.dropFirst()) : path
        return URL(string: trimmed, relativeTo: baseURL)?.absoluteURL
    }
}
