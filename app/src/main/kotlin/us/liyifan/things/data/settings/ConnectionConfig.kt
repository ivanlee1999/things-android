package us.liyifan.things.data.settings

/**
 * Where the backend is and how to prove we may talk to it.
 *
 * The API key speaks for a whole Things Cloud account, so it is only ever sent over HTTPS in a
 * release build; see [UrlPolicy]. The two Cloudflare fields are a service token for an Access
 * policy in front of the tunnel — the backend's own /mcp endpoint has no authentication at all,
 * so Access is what keeps the hostname private.
 */
data class ConnectionConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

/**
 * Whether an address may be used, and why not when it may not.
 *
 * Android's network-security-config cannot express "the local network" — a domain entry matches
 * one host exactly, and there is no CIDR syntax — so the debug build permits cleartext outright
 * and this is the rule that narrows it back down to addresses that cannot leave the building.
 */
object UrlPolicy {

    sealed interface Problem {
        val message: String

        data object Empty : Problem {
            override val message = "Enter the address of your things-cloud server."
        }

        data object NotAUrl : Problem {
            override val message = "That is not an address. It should look like https://things.example.com."
        }

        data object WrongScheme : Problem {
            override val message = "Use http:// or https://."
        }

        data object CleartextInRelease : Problem {
            override val message =
                "This build only speaks https. The API key stands in for your whole Things " +
                    "account, so it is not sent in the clear."
        }

        data object CleartextPublicHost : Problem {
            override val message =
                "http:// is only allowed to a private address on your own network. Use https:// " +
                    "for anything reachable from the internet."
        }
    }

    fun validate(url: String, allowCleartext: Boolean): Problem? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return Problem.Empty
        val parsed = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return Problem.NotAUrl
        val host = parsed.host ?: return Problem.NotAUrl
        return when (parsed.scheme?.lowercase()) {
            "https" -> null
            "http" -> when {
                !allowCleartext -> Problem.CleartextInRelease
                isPrivate(host) -> null
                else -> Problem.CleartextPublicHost
            }
            else -> Problem.WrongScheme
        }
    }

    /** Loopback, RFC 1918, link-local, or a name that only a LAN resolver knows. */
    fun isPrivate(host: String): Boolean {
        val h = host.trim('[', ']').lowercase()
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) return true
        if (h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) return true
        val parts = h.split(".")
        if (parts.size != 4 || parts.any { p -> p.isEmpty() || !p.all { it.isDigit() } }) return false
        val (a, b) = parts[0].toInt() to parts[1].toInt()
        return when {
            a == 127 -> true
            a == 10 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 169 && b == 254 -> true
            else -> false
        }
    }
}
