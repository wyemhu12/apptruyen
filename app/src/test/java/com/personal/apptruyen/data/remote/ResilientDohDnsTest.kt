package com.personal.apptruyen.data.remote

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ResilientDohDnsTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `lookup resolves domain via DoH JSON and preserves hostname for TLS SNI`() {
        val dohJson =
            """
            {
              "Status": 0,
              "Answer": [
                {"name": "wikicv.org", "type": 1, "TTL": 300, "data": "104.21.48.209"},
                {"name": "wikicv.org", "type": 1, "TTL": 300, "data": "172.67.190.12"}
              ]
            }
            """.trimIndent()
        server.enqueue(MockResponse(code = 200, body = dohJson))

        val systemDnsCalled = AtomicInteger(0)
        val fakeSystemDns =
            Dns {
                systemDnsCalled.incrementAndGet()
                throw UnknownHostException("Blocked by corporate DNS")
            }

        val dns =
            ResilientDohDns(
                systemDns = fakeSystemDns,
                dohEndpoints = listOf(server.url("/dns-query").toString()),
                bootstrapClient = OkHttpClient(),
            )

        val addresses = dns.lookup("wikicv.org")

        assertEquals(2, addresses.size)
        assertEquals("wikicv.org", addresses[0].hostName)
        assertEquals("104.21.48.209", addresses[0].hostAddress)
        assertEquals("172.67.190.12", addresses[1].hostAddress)
        assertEquals(0, systemDnsCalled.get())

        val recorded = server.takeRequest()
        assertEquals("application/dns-json", recorded.headers["Accept"])
        assertTrue(recorded.url.toString().contains("name=wikicv.org&type=A"))
    }

    @Test
    fun `lookup filters out sinkhole and bogon IPs from DoH response`() {
        val dohJson =
            """
            {
              "Status": 0,
              "Answer": [
                {"name": "sstruyen.pics", "type": 1, "TTL": 300, "data": "0.0.0.0"},
                {"name": "sstruyen.pics", "type": 1, "TTL": 300, "data": "127.0.0.1"},
                {"name": "sstruyen.pics", "type": 1, "TTL": 300, "data": "192.168.1.1"},
                {"name": "sstruyen.pics", "type": 1, "TTL": 300, "data": "104.21.55.10"}
              ]
            }
            """.trimIndent()
        server.enqueue(MockResponse(code = 200, body = dohJson))

        val dns =
            ResilientDohDns(
                systemDns = Dns { throw UnknownHostException("System DNS should not be called") },
                dohEndpoints = listOf(server.url("/dns-query").toString()),
                bootstrapClient = OkHttpClient(),
            )

        val addresses = dns.lookup("sstruyen.pics")

        assertEquals(1, addresses.size)
        assertEquals("104.21.55.10", addresses.first().hostAddress)
        assertEquals("sstruyen.pics", addresses.first().hostName)
    }

    @Test
    fun `lookup caches resolved addresses within TTL and re-queries after TTL expires`() {
        val dohJson =
            """
            {
              "Status": 0,
              "Answer": [
                {"name": "tangthuvien.org", "type": 1, "TTL": 300, "data": "104.21.10.20"}
              ]
            }
            """.trimIndent()
        server.enqueue(MockResponse(code = 200, body = dohJson))
        server.enqueue(MockResponse(code = 200, body = dohJson))

        val fakeClock = AtomicLong(1_000L)
        val dns =
            ResilientDohDns(
                systemDns = Dns { throw UnknownHostException("System DNS not used") },
                dohEndpoints = listOf(server.url("/dns-query").toString()),
                bootstrapClient = OkHttpClient(),
                cacheTtlMs = 600_000L,
                clock = { fakeClock.get() },
            )

        val first = dns.lookup("tangthuvien.org")
        fakeClock.addAndGet(300_000L) // Still within 10-minute TTL
        val second = dns.lookup("tangthuvien.org")

        assertEquals(1, server.requestCount, "Second lookup within TTL must be served from cache")
        assertEquals(first.map { it.hostAddress }, second.map { it.hostAddress })

        fakeClock.addAndGet(400_000L) // Expired (> 600_000L)
        dns.lookup("tangthuvien.org")
        assertEquals(2, server.requestCount, "Lookup after TTL expiry must re-query DoH")
    }

    @Test
    fun `lookup falls back to systemDns when all DoH endpoints fail and throws UnknownHostException when both fail`() {
        server.enqueue(MockResponse(code = 500, body = "Server Error"))

        val fallbackIp = InetAddress.getByAddress("wikicv.org", byteArrayOf(104.toByte(), 21, 48, 209.toByte()))
        val dnsWithWorkingSystem =
            ResilientDohDns(
                systemDns = Dns { listOf(fallbackIp) },
                dohEndpoints = listOf(server.url("/dns-query").toString()),
                bootstrapClient = OkHttpClient(),
            )

        val fallbackResult = dnsWithWorkingSystem.lookup("wikicv.org")
        assertEquals(1, fallbackResult.size)
        assertEquals("104.21.48.209", fallbackResult.first().hostAddress)

        server.enqueue(MockResponse(code = 500, body = "Server Error"))
        val dnsAllFailing =
            ResilientDohDns(
                systemDns = Dns { throw UnknownHostException("DNS poisoned") },
                dohEndpoints = listOf(server.url("/dns-query").toString()),
                bootstrapClient = OkHttpClient(),
            )

        assertThrows(UnknownHostException::class.java) {
            dnsAllFailing.lookup("wikicv.org")
        }
    }

    @Test
    fun `lookup bypasses DoH for localhost and IP literals`() {
        val systemCalls = AtomicInteger(0)
        val loopback = InetAddress.getByAddress("localhost", byteArrayOf(127, 0, 0, 1))
        val dns =
            ResilientDohDns(
                systemDns =
                    Dns {
                        systemCalls.incrementAndGet()
                        listOf(loopback)
                    },
                dohEndpoints = listOf(server.url("/dns-query").toString()),
                bootstrapClient = OkHttpClient(),
            )

        dns.lookup("localhost")
        dns.lookup("127.0.0.1")
        dns.lookup("::1")

        assertEquals(3, systemCalls.get())
        assertEquals(0, server.requestCount, "Localhost and IP literals must bypass DoH")
    }
}
