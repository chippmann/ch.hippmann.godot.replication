package ch.hippmann.godot.replication.transport

import godot.api.Crypto
import godot.api.CryptoKey
import godot.api.TLSOptions
import godot.api.X509Certificate

/**
 * One self signed certificate per process. Every member hands its PEM to the others through the member record, so a peer
 * that dials pins exactly that certificate; nothing needs a certificate authority.
 */
object SessionCertificate {
    const val COMMON_NAME = "replication-member"

    private var key: CryptoKey? = null
    private var certificate: X509Certificate? = null

    val pem: String
        get() = ensure().saveToString()

    fun serverOptions(): TLSOptions {
        ensure()
        return checkNotNull(TLSOptions.server(key, certificate)) { "Could not create DTLS server options" }
    }

    /** Pins [pem] when known; a typed address without any certificate trusts on first use. */
    fun clientOptions(pem: String?): TLSOptions {
        val trusted = pem?.takeIf { it.isNotBlank() }?.let { text -> X509Certificate().also { it.loadFromString(text) } }
        val options = if (trusted != null) TLSOptions.client(trusted, COMMON_NAME) else TLSOptions.clientUnsafe()
        return checkNotNull(options) { "Could not create DTLS client options" }
    }

    private fun ensure(): X509Certificate {
        certificate?.let { return it }
        val crypto = Crypto()
        val generatedKey = checkNotNull(crypto.generateRsa(KEY_BITS)) { "Could not generate the session key" }
        val generated = checkNotNull(
            crypto.generateSelfSignedCertificate(generatedKey, "CN=$COMMON_NAME,O=ch.hippmann.godot.replication,C=CH", NOT_BEFORE, NOT_AFTER),
        ) { "Could not generate the session certificate" }
        key = generatedKey
        certificate = generated
        return generated
    }

    private const val KEY_BITS = 2048
    private const val NOT_BEFORE = "20250101000000"
    private const val NOT_AFTER = "20450101000000"
}
