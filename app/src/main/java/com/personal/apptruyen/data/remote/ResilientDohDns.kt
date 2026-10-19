package com.personal.apptruyen.data.remote

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * [Dns] implementation ưu tiên phân giải tên miền qua DNS-over-HTTPS (Cloudflare & Google DoH JSON API
 * `application/dns-json`) để vượt chặn DNS trên mạng WiFi công ty/ISP, kèm bộ lọc IP sinkhole/bogon,
 * bộ nhớ đệm TTL 10 phút và cơ chế fallback an toàn về [Dns.SYSTEM].
 */
class ResilientDohDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val dohEndpoints: List<String> = DEFAULT_DOH_ENDPOINTS,
    private val bootstrapClient: OkHttpClient = buildBootstrapClient(),
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val bypassLocalhostCheckForTest: Boolean = false,
) : Dns {

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun lookup(hostname: String): List<InetAddress> {
        val normalizedHost = hostname.trim().lowercase()
        if (normalizedHost.isBlank()) {
            throw UnknownHostException("Hostname is blank")
        }

        if (!bypassLocalhostCheckForTest && isLocalOrIpLiteral(normalizedHost)) {
            return systemDns.lookup(hostname)
        }

        val now = clock()
        val cached = cache[normalizedHost]
        if (cached != null && now < cached.expiresAtMs && cached.addresses.isNotEmpty()) {
            return cached.addresses
        }

        for (endpoint in dohEndpoints) {
            val resolved = queryDohEndpoint(endpoint, hostname, normalizedHost)
            if (resolved.isNotEmpty()) {
                putCache(normalizedHost, resolved, clock() + cacheTtlMs)
                return resolved
            }
        }

        return try {
            val sysAddresses = systemDns.lookup(hostname)
            val publicAddresses = sysAddresses.filter { isValidPublicAddress(it) }
            if (publicAddresses.isNotEmpty()) {
                putCache(normalizedHost, publicAddresses, clock() + cacheTtlMs)
                publicAddresses
            } else if (sysAddresses.isNotEmpty()) {
                sysAddresses
            } else {
                cached?.addresses?.takeIf { it.isNotEmpty() }
                    ?: throw UnknownHostException("Không phân giải được địa chỉ cho $hostname")
            }
        } catch (e: UnknownHostException) {
            cached?.addresses?.takeIf { it.isNotEmpty() } ?: throw e
        }
    }

    private fun queryDohEndpoint(
        endpoint: String,
        originalHostname: String,
        normalizedHost: String,
    ): List<InetAddress> {
        val baseHttpUrl = endpoint.toHttpUrlOrNull() ?: return emptyList()
        val url =
            baseHttpUrl
                .newBuilder()
                .addQueryParameter("name", normalizedHost)
                .addQueryParameter("type", "A")
                .build()

        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .get()
                .build()

        return try {
            bootstrapClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body.string()
                if (body.isBlank()) return emptyList()
                parseDohJsonResponse(originalHostname, body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun parseDohJsonResponse(
        originalHostname: String,
        jsonBody: String,
    ): List<InetAddress> {
        return try {
            val root = JSONObject(jsonBody)
            if (root.optInt("Status", -1) != 0) return emptyList()
            val answers = root.optJSONArray("Answer") ?: return emptyList()
            val result = mutableListOf<InetAddress>()

            for (i in 0 until answers.length()) {
                val item = answers.optJSONObject(i) ?: continue
                val type = item.optInt("type", -1)
                if (type != DNS_TYPE_A && type != DNS_TYPE_AAAA) continue

                val data = item.optString("data", "").trim()
                if (data.isBlank()) continue

                val rawBytes = parseIpLiteralToBytes(data) ?: continue
                val addr = InetAddress.getByAddress(originalHostname, rawBytes)
                if (isValidPublicAddress(addr)) {
                    result.add(addr)
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun putCache(
        normalizedHost: String,
        addresses: List<InetAddress>,
        expiresAtMs: Long,
    ) {
        if (cache.size >= MAX_CACHE_ENTRIES) {
            val now = clock()
            val expiredKeys = cache.entries.filter { it.value.expiresAtMs <= now }.map { it.key }
            expiredKeys.forEach { cache.remove(it) }
            if (cache.size >= MAX_CACHE_ENTRIES) {
                cache.keys.firstOrNull()?.let { cache.remove(it) }
            }
        }
        cache[normalizedHost] = CacheEntry(addresses = addresses, expiresAtMs = expiresAtMs)
    }

    companion object {
        val DEFAULT_DOH_ENDPOINTS =
            listOf(
                "https://1.1.1.1/dns-query",
                "https://1.0.0.1/dns-query",
                "https://8.8.8.8/resolve",
            )
        const val DEFAULT_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_CACHE_ENTRIES = 128
        private const val DOH_TIMEOUT_SECONDS = 3L
        private const val DNS_TYPE_A = 1
        private const val DNS_TYPE_AAAA = 28
        private val IPV4_LITERAL_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        internal fun isLocalOrIpLiteral(normalizedHost: String): Boolean {
            if (
                normalizedHost == "localhost" ||
                normalizedHost == "127.0.0.1" ||
                normalizedHost == "::1" ||
                normalizedHost == "[::1]"
            ) {
                return true
            }
            if (IPV4_LITERAL_REGEX.matches(normalizedHost)) {
                return true
            }
            return normalizedHost.contains(':')
        }

        internal fun parseIpLiteralToBytes(ip: String): ByteArray? {
            if (IPV4_LITERAL_REGEX.matches(ip)) {
                val parts = ip.split('.')
                if (parts.size != 4) return null
                val bytes = ByteArray(4)
                for (i in 0..3) {
                    val octet = parts[i].toIntOrNull() ?: return null
                    if (octet !in 0..255) return null
                    bytes[i] = octet.toByte()
                }
                return bytes
            }
            if (ip.contains(':')) {
                return runCatching { InetAddress.getByName(ip).address }.getOrNull()
            }
            return null
        }

        internal fun isValidPublicAddress(addr: InetAddress): Boolean =
            !addr.isAnyLocalAddress &&
                !addr.isLoopbackAddress &&
                !addr.isLinkLocalAddress &&
                !addr.isSiteLocalAddress &&
                !addr.isMulticastAddress

        private fun buildBootstrapClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DOH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
    }
}
