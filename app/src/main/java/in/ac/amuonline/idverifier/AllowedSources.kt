package `in`.ac.amuonline.idverifier

/**
 * The only QR-code sources this app will treat as a genuine AMU ID card.
 * Anything else (typos, look-alike domains, http, other schemes) is rejected.
 *
 * Update this list if the ID-card generator starts issuing links from a new host.
 */
data class AllowedSource(
    val host: String,
    val requiredPathPrefix: String,
    val label: String
)

object AllowedSources {
    val ALL = listOf(
        AllowedSource(
            host = "moeps.amucoe.ac.in",
            requiredPathPrefix = "/generate/verifiable/qr-code/",
            label = "AMU MOEPS verification service"
        ),
        AllowedSource(
            host = "nep.amuonline.ac.in",
            requiredPathPrefix = "/admin/idcards/verify/",
            label = "NEP Cell AMU verification portal"
        )
    )
}
