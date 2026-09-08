package ch.hippmann.godot.replication.core.session

import java.security.MessageDigest
import java.security.SecureRandom

/** Salted challenge and response so a password never travels in clear text; not a transport encryption. */
public object PasswordChallenge {
    public const val SALT_SIZE: Int = 16
    public const val NONCE_SIZE: Int = 16

    private val random = SecureRandom()

    public fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    public fun verifier(password: String, salt: ByteArray): ByteArray = sha256(salt + password.encodeToByteArray())

    public fun proof(nonce: ByteArray, verifier: ByteArray): ByteArray = sha256(nonce + verifier)

    public fun proofFor(password: String, salt: ByteArray, nonce: ByteArray): ByteArray = proof(nonce, verifier(password, salt))

    public fun matches(expected: ByteArray, actual: ByteArray): Boolean = MessageDigest.isEqual(expected, actual)

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}
