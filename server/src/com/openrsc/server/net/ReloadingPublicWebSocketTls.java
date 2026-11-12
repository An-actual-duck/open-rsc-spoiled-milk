package com.openrsc.server.net;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Direct public WSS only. Immutable startup paths; failed renewal keeps last good TLS. */
public final class ReloadingPublicWebSocketTls {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path certificate, key;
    private SslContext current;
    private byte[] fingerprint;
    private long nextCheck;
    public ReloadingPublicWebSocketTls(String certificate, String key) throws Exception {
        this.certificate=Paths.get(certificate); this.key=Paths.get(key);
        if (!this.certificate.isAbsolute() || !this.key.isAbsolute())
            throw new IllegalArgumentException("Public WSS requires absolute configured PEM paths");
        refresh();
    }
    public synchronized SslContext context() {
        long now=System.nanoTime();
        if (now>=nextCheck) {
            nextCheck=now+30_000_000_000L;
            try { refresh(); }
            catch(Exception failure) { LOGGER.warn("Public WSS renewal rejected; keeping last good certificate: {}",failure.toString()); }
        }
        return current;
    }
    public synchronized SslContext refresh() throws Exception {
        byte[] certBytes=read(certificate), keyBytes=read(key);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            digest.update(certBytes); digest.update((byte)0); digest.update(keyBytes); byte[] hash=digest.digest();
            if (Arrays.equals(hash,fingerprint)) return current;
            X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certBytes));
            cert.checkValidity();
            boolean host=false;Collection<List<?>> names=cert.getSubjectAlternativeNames();
            if(names!=null)for(List<?> name:names)if(Integer.valueOf(2).equals(name.get(0))&&"play.rscspoiledmilk.com".equals(name.get(1)))host=true;
            if(!host)throw new IllegalArgumentException("Public WSS certificate must name play.rscspoiledmilk.com");
            String pem=new String(keyBytes,StandardCharsets.US_ASCII);
            if(!pem.contains("-----BEGIN PRIVATE KEY-----")||pem.contains("ENCRYPTED"))
                throw new IllegalArgumentException("Public WSS key must be unencrypted PKCS8 PEM");
            byte[] der=Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----",""));
            try {
                String algorithm=cert.getPublicKey().getAlgorithm();
                PrivateKey privateKey=KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(der));
                String signatureAlgorithm="EC".equals(algorithm)?"SHA256withECDSA":"SHA256with"+algorithm;
                byte[] challenge="Spoiled Milk public WSS certificate pair".getBytes(StandardCharsets.US_ASCII);
                Signature signature=Signature.getInstance(signatureAlgorithm); signature.initSign(privateKey); signature.update(challenge);byte[] signed=signature.sign();
                signature.initVerify(cert.getPublicKey());signature.update(challenge);
                if(!signature.verify(signed))throw new IllegalArgumentException("Public WSS certificate/key mismatch");
            } finally { Arrays.fill(der,(byte)0); }
            SslContext candidate=SslContextBuilder.forServer(new ByteArrayInputStream(certBytes),new ByteArrayInputStream(keyBytes)).build();
            current=candidate;fingerprint=hash;nextCheck=System.nanoTime()+30_000_000_000L;
            return current;
        } finally { Arrays.fill(keyBytes,(byte)0); }
    }
    private static byte[] read(Path path) throws Exception {
        long size=Files.size(path);if(size<1||size>131072)throw new IllegalArgumentException("Public WSS PEM file size");
        byte[] bytes=Files.readAllBytes(path);if(bytes.length!=size)throw new IllegalArgumentException("Public WSS PEM file changed during read");return bytes;
    }
}
