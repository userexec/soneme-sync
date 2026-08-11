package com.userexec.soneme.sync

import java.net.URI

data class ParsedRemoteAddress(
    val host: String,
    val port: Int? = null,
    val path: String? = null,
    val username: String? = null,
    val password: String? = null
)

object RemoteAddressParser {
    fun parse(value: String): ParsedRemoteAddress? {
        val text = value.trim()
        if (text.isBlank() || !text.contains("://")) return null
        return try {
            val uri = URI(text)
            val host = (uri.host ?: return null).removeSurrounding("[", "]")
            val userInfo = uri.userInfo
            val username: String?
            val password: String?
            if (userInfo != null) {
                val split = userInfo.split(':', limit = 2)
                username = split[0]
                password = split.getOrNull(1)
            } else {
                username = null
                password = null
            }
            ParsedRemoteAddress(
                host = host,
                port = uri.port.takeIf { it >= 0 },
                path = uri.path?.takeIf { it.isNotBlank() },
                username = username,
                password = password
            )
        } catch (_: Exception) {
            null
        }
    }

}
