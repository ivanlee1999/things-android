package us.liyifan.things.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test fun `https is always fine`() {
        assertNull(UrlPolicy.validate("https://things.example.com", allowCleartext = false))
        assertNull(UrlPolicy.validate("https://things.example.com:8443/", allowCleartext = true))
    }

    @Test fun `a release build refuses cleartext even on the LAN`() {
        assertEquals(
            UrlPolicy.Problem.CleartextInRelease,
            UrlPolicy.validate("http://192.168.0.100:8097", allowCleartext = false),
        )
    }

    @Test fun `a debug build allows cleartext only to a private address`() {
        assertNull(UrlPolicy.validate("http://192.168.0.100:8097", allowCleartext = true))
        assertNull(UrlPolicy.validate("http://10.1.2.3", allowCleartext = true))
        assertNull(UrlPolicy.validate("http://172.16.0.1", allowCleartext = true))
        assertNull(UrlPolicy.validate("http://localhost:8080", allowCleartext = true))
        assertNull(UrlPolicy.validate("http://nas.local", allowCleartext = true))
        assertEquals(
            UrlPolicy.Problem.CleartextPublicHost,
            UrlPolicy.validate("http://things.example.com", allowCleartext = true),
        )
    }

    @Test fun `nonsense is reported as nonsense`() {
        assertEquals(UrlPolicy.Problem.Empty, UrlPolicy.validate("   ", allowCleartext = true))
        assertEquals(UrlPolicy.Problem.NotAUrl, UrlPolicy.validate("things.example.com", allowCleartext = true))
        assertEquals(UrlPolicy.Problem.WrongScheme, UrlPolicy.validate("ftp://x.example.com", allowCleartext = true))
    }

    @Test fun `private address ranges`() {
        listOf("127.0.0.1", "10.0.0.1", "172.31.255.254", "192.168.1.1", "169.254.1.1", "::1")
            .forEach { assertTrue(it, UrlPolicy.isPrivate(it)) }
        listOf("8.8.8.8", "172.32.0.1", "11.0.0.1", "example.com", "192.169.0.1")
            .forEach { assertFalse(it, UrlPolicy.isPrivate(it)) }
    }
}
