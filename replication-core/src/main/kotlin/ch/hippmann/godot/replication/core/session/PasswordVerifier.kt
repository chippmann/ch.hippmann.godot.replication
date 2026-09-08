package ch.hippmann.godot.replication.core.session

import kotlinx.serialization.Serializable

/** Shared with every member so a new master can keep checking passwords after a migration. */
@Serializable
public class PasswordVerifier(public val salt: ByteArray, public val verifier: ByteArray) {
    public fun matches(nonce: ByteArray, proof: ByteArray): Boolean =
        PasswordChallenge.matches(PasswordChallenge.proof(nonce, verifier), proof)

    override fun equals(other: Any?): Boolean =
        other is PasswordVerifier && other.salt.contentEquals(salt) && other.verifier.contentEquals(verifier)

    override fun hashCode(): Int = 31 * salt.contentHashCode() + verifier.contentHashCode()

    public companion object {
        public fun forPassword(password: String): PasswordVerifier {
            val salt = PasswordChallenge.randomBytes(PasswordChallenge.SALT_SIZE)
            return PasswordVerifier(salt, PasswordChallenge.verifier(password, salt))
        }
    }
}
