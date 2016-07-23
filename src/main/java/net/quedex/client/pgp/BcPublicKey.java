package net.quedex.client.pgp;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public final class BcPublicKey {

    private final PGPPublicKey signingKey;
    private final PGPPublicKey encryptionKey;

    private final String fingerprint;
    private final String mainKeyIdentity;

    public BcPublicKey(String armoredKeyString) throws PGPKeyInitialisationException {

        try {
            PGPPublicKeyRing pubKeyRing = new PGPPublicKeyRing(
                    PGPUtil.getDecoderStream(new ByteArrayInputStream(armoredKeyString.getBytes(StandardCharsets.UTF_8))),
                    new BcKeyFingerprintCalculator()
            );

            if (Iterators.size(pubKeyRing.getPublicKeys()) < 1) {
                throw new PGPKeyInitialisationException("No keys in keyring");
            }

            signingKey = pubKeyRing.getPublicKey();

            @SuppressWarnings("unchecked")
            List<PGPPublicKey> keys = Lists.newArrayList(pubKeyRing.getPublicKeys());

            if (keys.size() == 1) {
                encryptionKey = signingKey;
            } else {
                encryptionKey = keys.get(1);
            }

            if (!encryptionKey.isEncryptionKey()) {
                throw new PGPKeyInitialisationException("Error instatiating public key: sign-only key.");
            }

        } catch (RuntimeException | IOException e) {
            throw new PGPKeyInitialisationException("Error instantiating a public key", e);
        }
        checkNotNull(signingKey);
        checkNotNull(encryptionKey);

        fingerprint = hexFingerprint(signingKey);
        mainKeyIdentity = (String) signingKey.getUserIDs().next();
    }

    BcPublicKey(PGPPublicKey signingKey, PGPPublicKey encryptionKey) {
        this.signingKey = signingKey;
        this.encryptionKey = encryptionKey;
        fingerprint = hexFingerprint(signingKey);
        mainKeyIdentity = (String) signingKey.getUserIDs().next();
    }

    PGPPublicKey getSigningKey() {
        return signingKey;
    }

    PGPPublicKey getEncryptionKey() {
        return encryptionKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getMainKeyIdentity() {
        return mainKeyIdentity;
    }

    public String getArmored() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ArmoredOutputStream armored = new ArmoredOutputStream(out);
        try {
            signingKey.encode(armored);
            encryptionKey.encode(armored);
            armored.close();
        } catch (IOException e) {
            throw new IllegalStateException("Error writing armored public key", e);
        }
        return new String(out.toByteArray(), Charsets.UTF_8);
    }

    private static String hexFingerprint(PGPPublicKey publicKey) {
        byte[] bytes = publicKey.getFingerprint();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || o.getClass() != getClass()) {
            return false;
        }

        BcPublicKey that = (BcPublicKey) o;

        return this.fingerprint.equals(that.fingerprint);
    }

    @Override
    public int hashCode() {
        return fingerprint.hashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("fingerprint", fingerprint)
                .toString();
    }
}
