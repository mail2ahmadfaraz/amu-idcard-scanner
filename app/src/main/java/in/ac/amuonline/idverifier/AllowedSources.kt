package `in`.ac.amuonline.idverifier

/**
 * The only QR-code sources this app will treat as a genuine AMU ID card.
 * Anything else (typos, look-alike domains, http, other schemes) is rejected.
 *
 * Update this list if the ID-card generator starts issuing links from a new host.
 *
 * [allowSubdomains] = true means any host ending in ".$host" (a real subdomain,
 * matched on a literal dot boundary) is also accepted — e.g. "amu.ac.in" with
 * allowSubdomains=true matches "digital.amu.ac.in" but NOT "evil-amu.ac.in".
 */
data class AllowedSource(
    val host: String,
    val label: String,
    val allowSubdomains: Boolean = false
)

object AllowedSources {
    val ALL = listOf(
        AllowedSource(
            host = "moeps.amucoe.ac.in",
            label = "AMU MOEPS verification service"
        ),
        AllowedSource(
            host = "nep.amuonline.ac.in",
            label = "NEP Cell AMU verification portal"
        ),
        AllowedSource(
            host = "amu.ac.in",
            label = "Aligarh Muslim University",
            allowSubdomains = true
        )
    )

    fun match(host: String): AllowedSource? = ALL.firstOrNull { source ->
        host == source.host || (source.allowSubdomains && host.endsWith("." + source.host))
    }
}
