package us.liyifan.things.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What can be checked off a device.
 *
 * Robolectric's AndroidKeyStore is a stand-in that does no real cryptography, so a round trip
 * here would be testing the stand-in rather than the cipher — that one is verified on the phone,
 * by connecting once and reopening the app. What this file pins down is the behaviour that
 * decides whether the app launches at all: the cipher never throws, whatever it is handed.
 *
 * That matters because the key is bound to the install. A restored backup or a cleared keystore
 * leaves bytes nobody can read, and the honest response is to ask for the API key again rather
 * than to crash on the first frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeystoreCipherTest {

    private val cipher = KeystoreCipher(alias = "things.test")

    @Test fun `an empty secret is legal, and stays empty`() {
        assertEquals("", cipher.decrypt(""))
    }

    @Test fun `unreadable ciphertext is null rather than an exception`() {
        assertNull(cipher.decrypt("not base64 at all !!"))
        assertNull(cipher.decrypt("c2hvcnQ=")) // valid base64, too short to hold an IV
        assertNull(cipher.decrypt("////"))
    }

    @Test fun `encrypting never throws, whatever the platform does underneath`() {
        // Returns null when the keystore is unavailable, which the settings store treats as
        // "nothing to save" rather than propagating.
        cipher.encrypt("a secret")
    }
}
