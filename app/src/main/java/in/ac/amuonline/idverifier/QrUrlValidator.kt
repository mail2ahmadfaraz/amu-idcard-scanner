package `in`.ac.amuonline.idverifier

import android.net.Uri

sealed class QrValidationResult {
    data class Valid(val url: String, val sourceLabel: String) : QrValidationResult()
    data class Invalid(val reason: String) : QrValidationResult()
}

/**
 * Decides whether raw QR-code text is a genuine AMU ID-card verification link.
 *
 * This is the entire security boundary: it never string-matches on the raw QR text
 * (which is trivially spoofable, e.g. "https://moeps.amucoe.ac.in.evil.com/..." or
 * "evil.com/?u=https://moeps.amucoe.ac.in"). Instead it parses the URL properly and
 * checks the *actual* scheme + host components against [AllowedSources.ALL].
 */
object QrUrlValidator {

    fun validate(rawText: String): QrValidationResult {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return QrValidationResult.Invalid("Empty QR code")
        }

        val uri = try {
            Uri.parse(trimmed)
        } catch (e: Exception) {
            return QrValidationResult.Invalid("Not a valid URL")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return QrValidationResult.Invalid(
                "Not a secure AMU verification link (scheme was \"${uri.scheme ?: "none"}\")"
            )
        }

        // Reject "user@host" authority tricks outright — a genuine verification link
        // never carries embedded credentials.
        if (!uri.userInfo.isNullOrEmpty()) {
            return QrValidationResult.Invalid("Malformed / suspicious link")
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) {
            return QrValidationResult.Invalid("Malformed link")
        }

        val match = AllowedSources.match(host)

        return if (match != null) {
            QrValidationResult.Valid(trimmed, match.label)
        } else {
            QrValidationResult.Invalid(
                "This QR code does not point to an official AMU verification service " +
                    "(host was \"$host\"). It may be a fake or tampered ID card."
            )
        }
    }
}
