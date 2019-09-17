package com.blocksdecoded.dex.core.security.encryption

import androidx.biometric.BiometricPrompt
import com.blocksdecoded.dex.core.security.IKeyProvider
import javax.crypto.Cipher

class EncryptionManager(private val keyProvider: IKeyProvider) : IEncryptionManager {

    @Synchronized
    override fun encrypt(data: String): String =
        CipherWrapper(keyProvider.transformationSymmetric).encrypt(data, keyProvider.getKey())

    @Synchronized
    override fun decrypt(data: String): String =
        CipherWrapper(keyProvider.transformationSymmetric).decrypt(data, keyProvider.getKey())

    override fun getCryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = CipherWrapper(keyProvider.transformationSymmetric).cipher
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getKey())

        return BiometricPrompt.CryptoObject(cipher)
    }
}
