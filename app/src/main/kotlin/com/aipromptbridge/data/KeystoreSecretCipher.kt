package com.aipromptbridge.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.aipromptbridge.core.storage.SecretCipher
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the Android Keystore (never leaves secure hardware / the keystore daemon).
 * Output layout: [1 byte IV length][IV][ciphertext+tag].
 *
 * NOTE: written from the standard Keystore pattern but NOT yet run on a device by the scaffold author.
 * Verify on a real device/emulator: save a key in the setup screen, restart the app, confirm it is still there.
 * TODO(security): consider setUserAuthenticationRequired(true) if you want a biometric gate before using keys.
 */
class KeystoreSecretCipher : SecretCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val ivLength = encrypted[0].toInt()
        val iv = encrypted.copyOfRange(1, 1 + ivLength)
        val payload = encrypted.copyOfRange(1 + ivLength, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(payload)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "aipromptbridge_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
