package us.liyifan.things.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The API key is stored as ciphertext under a key that never leaves the device, so a readable
 * preferences file does not hand over a whole Things account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeystoreCipherTest {

    private val cipher = KeystoreCipher(alias = "things.test")

    @Test fun `what goes in comes back out`() {
        val secret = "d2f8c1a0-not-a-real-key"
        val encoded = cipher.encrypt(secret)
        assertNotEquals("the plaintext must not be what is written", secret, encoded)
        assertEquals(secret, cipher.decrypt(encoded!!))
    }

    @Test fun `an empty secret is legal, and stays empty`() {
        assertEquals("", cipher.decrypt(""))
    }

    @Test fun `the same text encrypts differently every time`() {
        // A fresh IV per write, or two identical keys would be visibly identical on disk.
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test fun `unreadable ciphertext is null rather than a crash`() {
        // A restored backup or a cleared keystore leaves bytes nobody can read, and the honest
        // response is to ask for the key again, not to fail on launch.
        assertNull(cipher.decrypt("not base64 at all !!"))
        assertNull(cipher.decrypt("c2hvcnQ="))
    }
}
