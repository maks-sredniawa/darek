package app.darek.fuchy

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** The public half of `darek-fuchy`'s signing key (phase-F.md section 3); a leaked key means a release with a new one (ADR-0015). */
object FuchyKey {
    private const val SPKI =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEylQl66lypEahQLgByRLtYBLgvlPKdM1HAFkt1UAlUaX+7l1tSsc2opALHYZ1KiHvKGhyECB2qEJY12BUXtRRdw=="

    val maksForge: PublicKey by lazy { publicKey(SPKI) }

    fun publicKey(spkiBase64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(spkiBase64)))
}

/** ECDSA P-256 over SHA-256, DER-encoded, over the exact bytes inside the envelope's `fucha`. */
fun signedBy(
    fucha: ByteArray,
    signature: String?,
    key: PublicKey,
): Boolean {
    if (signature == null) return false
    return try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(fucha)
            verify(Base64.getDecoder().decode(signature))
        }
    } catch (e: GeneralSecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}
