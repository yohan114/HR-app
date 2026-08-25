import SwiftUI

// GENERATED from design/tokens.json — do not edit.
// Run `node design/generate.mjs` after changing a token.
// CI fails if this file is stale.

public enum DesignTokens {
    public struct TypeToken: Sendable {
        public let size: CGFloat
        public let lineHeight: CGFloat
        public let weight: Int
    }

    public enum Colors {
        public enum Light {
            static let brandPrimary = Color(red: 0.1059, green: 0.3686, blue: 0.6118)
            static let brandOnPrimary = Color(red: 1.0000, green: 1.0000, blue: 1.0000)
            static let brandPrimaryContainer = Color(red: 0.8275, green: 0.8941, blue: 0.9686)
            static let brandOnPrimaryContainer = Color(red: 0.0000, green: 0.1137, blue: 0.2039)
            static let secondary = Color(red: 0.3137, green: 0.3765, blue: 0.4353)
            static let surface = Color(red: 0.9882, green: 0.9882, blue: 1.0000)
            static let surfaceRaised = Color(red: 1.0000, green: 1.0000, blue: 1.0000)
            static let surfaceVariant = Color(red: 0.8706, green: 0.8902, blue: 0.9216)
            static let onSurface = Color(red: 0.1020, green: 0.1098, blue: 0.1176)
            static let onSurfaceMuted = Color(red: 0.2588, green: 0.2784, blue: 0.3059)
            static let outline = Color(red: 0.4353, green: 0.4588, blue: 0.4863)
            static let success = Color(red: 0.1059, green: 0.4980, blue: 0.2941)
            static let warning = Color(red: 0.5412, green: 0.3529, blue: 0.0000)
            static let danger = Color(red: 0.7294, green: 0.1020, blue: 0.1020)
            static let onDanger = Color(red: 1.0000, green: 1.0000, blue: 1.0000)
            static let info = Color(red: 0.1059, green: 0.3686, blue: 0.6118)
            static let outlineVariant = Color(red: 0.7647, green: 0.7843, blue: 0.8118)
        }

        public enum Dark {
            static let brandPrimary = Color(red: 0.5608, green: 0.7451, blue: 0.9176)
            static let brandOnPrimary = Color(red: 0.0000, green: 0.1961, blue: 0.3412)
            static let brandPrimaryContainer = Color(red: 0.0000, green: 0.2863, blue: 0.4863)
            static let brandOnPrimaryContainer = Color(red: 0.8275, green: 0.8941, blue: 0.9686)
            static let secondary = Color(red: 0.7176, green: 0.7882, blue: 0.8510)
            static let surface = Color(red: 0.1020, green: 0.1098, blue: 0.1176)
            static let surfaceRaised = Color(red: 0.1373, green: 0.1490, blue: 0.1647)
            static let surfaceVariant = Color(red: 0.2588, green: 0.2784, blue: 0.3059)
            static let onSurface = Color(red: 0.8863, green: 0.8863, blue: 0.8980)
            static let onSurfaceMuted = Color(red: 0.7608, green: 0.7804, blue: 0.8118)
            static let outline = Color(red: 0.5490, green: 0.5686, blue: 0.6000)
            static let success = Color(red: 0.4275, green: 0.8314, blue: 0.6039)
            static let warning = Color(red: 0.9412, green: 0.7608, blue: 0.4157)
            static let danger = Color(red: 1.0000, green: 0.7059, blue: 0.6706)
            static let onDanger = Color(red: 0.4118, green: 0.0000, blue: 0.0196)
            static let info = Color(red: 0.5608, green: 0.7451, blue: 0.9176)
            static let outlineVariant = Color(red: 0.2588, green: 0.2784, blue: 0.3059)
        }
    }

    /// 4pt base grid. The name states the value: s4 is 16.
    public enum Spacing {
        static let s1: CGFloat = 4
        static let s2: CGFloat = 8
        static let s3: CGFloat = 12
        static let s4: CGFloat = 16
        static let s6: CGFloat = 24
        static let s8: CGFloat = 32
    }

    public enum Radius {
        static let control: CGFloat = 8
        static let card: CGFloat = 12
        static let pill: CGFloat = 999
    }

    public enum TypeScale {
        static let displaySmall = TypeToken(size: 32, lineHeight: 40, weight: 600)
        static let headlineMedium = TypeToken(size: 24, lineHeight: 32, weight: 600)
        static let headlineSmall = TypeToken(size: 20, lineHeight: 28, weight: 600)
        static let titleLarge = TypeToken(size: 17, lineHeight: 24, weight: 500)
        static let bodyLarge = TypeToken(size: 15, lineHeight: 22, weight: 400)
        static let bodyMedium = TypeToken(size: 13, lineHeight: 20, weight: 400)
        static let labelSmall = TypeToken(size: 11, lineHeight: 16, weight: 500)
    }
}
