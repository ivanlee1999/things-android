package us.liyifan.things.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with a key that never leaves the device, used to keep the API key and the Cloudflare
 * service token out of a readable preferences file.
 *
 * This is deliberately sixty lines rather than a dependency: androidx.security-crypto is
 * deprecated and pulls Tink in for the same result. The ciphertext is base64 of IV ++ payload.
 *
 * Decryption failure returns null rather than throwing. The key is bound to this install, so a
 * restored backup or a cleared keystore yields bytes nobody can read, and the honest response to
 * that is to ask for the key again, not to crash on launch.
 */
class KeystoreCipher(private val alias: String = DEFAULT_ALIAS) {

    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    fun decrypt(encoded: String): String? = runCatching {
        if (encoded.isEmpty()) return ""
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        if (bytes.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
        )
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val DEFAULT_ALIAS = "things.secrets"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
