package io.fluxzero.ticketing.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.sdk.test.TestFixture;
import io.fluxzero.sdk.tracking.handling.IllegalCommandException;
import io.fluxzero.sdk.tracking.handling.authentication.UnauthorizedException;
import io.fluxzero.ticketing.admission.api.RedeemTicket;
import io.fluxzero.ticketing.admission.api.SetGateOpen;
import io.fluxzero.ticketing.booking.api.CancelReservation;
import io.fluxzero.ticketing.booking.api.TicketId;
import io.fluxzero.ticketing.support.TicketingTestSupport;
import io.fluxzero.ticketing.wallet.api.GetAppleWalletPass;
import io.fluxzero.ticketing.wallet.api.GetGoogleWalletPass;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

/** Locally issued test certificates prove format/signature behavior, not Apple or Google issuer approval. */
class WalletTest extends TicketingTestSupport {
    private static final TicketId TICKET = new TicketId("alice-order:1");
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir static Path credentials;
    private static KeyPair signingKey;

    @BeforeAll static void createLocalSigningCredentials() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        signingKey = generator.generateKeyPair();
        var rootKey = generator.generateKeyPair();
        var ca = new X500Name("CN=Local test intermediate");
        var from = Date.from(NOW.minusSeconds(86400)); var until = Date.from(NOW.plusSeconds(86400 * 365));
        var root = new JcaX509v3CertificateBuilder(ca, BigInteger.ONE, from, until, ca, rootKey.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKey.getPrivate()));
        var leaf = new JcaX509v3CertificateBuilder(ca, BigInteger.TWO, from, until,
                new X500Name("UID=pass.test.ticketing,OU=TESTTEAM01,CN=Local test pass"), signingKey.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKey.getPrivate()));
        var converter = new JcaX509CertificateConverter();
        var store = KeyStore.getInstance("PKCS12"); store.load(null, null);
        store.setKeyEntry("pass", signingKey.getPrivate(), "test-only".toCharArray(),
                new Certificate[]{converter.getCertificate(leaf), converter.getCertificate(root)});
        try (var file = Files.newOutputStream(credentials.resolve("pass.p12"))) { store.store(file, "test-only".toCharArray()); }
        Files.writeString(credentials.resolve("google.json"), JSON.writeValueAsString(Map.of("type", "service_account",
                "client_email", "test@example.iam.gserviceaccount.com", "private_key", "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder().encodeToString(signingKey.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----")));
    }

    private TestFixture wallet(boolean async) {
        return paid(async).withProperty("ticketing.admission.signing-key", "fixture-only-admission-key-32-bytes-minimum")
                .withProperty("ticketing.wallet.apple.keystore", credentials.resolve("pass.p12").toString())
                .withProperty("ticketing.wallet.apple.password", "test-only")
                .withProperty("ticketing.wallet.google.service-account", credentials.resolve("google.json").toString())
                .withProperty("ticketing.wallet.google.issuer-id", "123456789")
                .withProperty("fluxzero.auth.external-base-url", "https://tickets.example.test")
                .givenCommandsByUser(OPERATOR, new SetGateOpen(SHOW, true));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void applePackageHasVerifiableManifestSignatureAndUsableAdmissionCode(boolean async) {
        wallet(async).whenExecuting(f -> {
            byte[] archive = ALICE.apply(() -> Fluxzero.queryAndWait(new GetAppleWalletPass(TICKET)));
            var files = new HashMap<String, byte[]>();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) files.put(entry.getName(), zip.readAllBytes());
            }
            var manifest = JSON.readTree(files.get("manifest.json"));
            assertEquals(files.size() - 2, manifest.size());
            for (var entry : files.entrySet()) if (manifest.has(entry.getKey()))
                assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(entry.getValue())), manifest.get(entry.getKey()).asText());
            var cms = new CMSSignedData(new CMSProcessableByteArray(files.get("manifest.json")), files.get("signature"));
            assertEquals(2, cms.getCertificates().getMatches(null).size());
            for (var signer : cms.getSignerInfos().getSigners()) {
                X509CertificateHolder certificate = (X509CertificateHolder) cms.getCertificates().getMatches(signer.getSID()).iterator().next();
                assertTrue(signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificate)));
                var signingTime = signer.getSignedAttributes().get(org.bouncycastle.asn1.cms.CMSAttributes.signingTime);
                assertEquals(NOW, org.bouncycastle.asn1.cms.Time.getInstance(signingTime.getAttrValues().getObjectAt(0)).getDate().toInstant());
            }
            var pass = JSON.readTree(files.get("pass.json"));
            assertEquals("pass.test.ticketing", pass.get("passTypeIdentifier").asText());
            assertEquals("TESTTEAM01", pass.get("teamIdentifier").asText());
            assertFalse(pass.get("voided").asBoolean());
            assertEquals("Row A / Seat 1", pass.at("/eventTicket/auxiliaryFields/0/value").asText());
            assertEquals("Standard", pass.at("/eventTicket/auxiliaryFields/1/value").asText());
            var icon = javax.imageio.ImageIO.read(new ByteArrayInputStream(files.get("icon@3x.png")));
            assertEquals(87, icon.getWidth());
            String code = pass.at("/barcodes/0/message").asText();
            OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW, code)));
            assertThrows(IllegalCommandException.class, () -> OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(new RedeemTicket(SHOW, code))));
        }).expectSuccessfulResult();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void googleSaveLinkHasValidSignatureAndDoesNotReviveACancelledTicket(boolean async) {
        wallet(async).whenExecuting(f -> {
            String link = ALICE.apply(() -> Fluxzero.queryAndWait(new GetGoogleWalletPass(TICKET)));
            assertTrue(link.startsWith("https://pay.google.com/gp/v/save/"));
            String[] parts = link.substring(link.lastIndexOf('/') + 1).split("\\.");
            var signature = Signature.getInstance("SHA256withRSA"); signature.initVerify(signingKey.getPublic());
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            assertTrue(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
            var claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            assertEquals("google", claims.get("aud").asText());
            assertEquals("savetowallet", claims.get("typ").asText());
            assertEquals(NOW.plusSeconds(600).getEpochSecond(), claims.get("exp").asLong());
            var ticket = claims.at("/payload/eventTicketObjects/0");
            assertEquals(claims.at("/payload/eventTicketClasses/0/id").asText(), ticket.get("classId").asText());
            assertTrue(ticket.get("id").asText().matches("123456789\\.[a-f0-9]{64}"));
            ALICE.apply(() -> Fluxzero.sendCommandAndWait(new CancelReservation(R)));
            assertThrows(IllegalCommandException.class, () -> OPERATOR.apply(() -> Fluxzero.sendCommandAndWait(
                    new RedeemTicket(SHOW, ticket.at("/barcode/value").asText()))));
        }).expectSuccessfulResult();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void walletIssuanceCannotBypassTicketOwnership(boolean async) {
        wallet(async).whenQueryByUser(BOB, new GetAppleWalletPass(TICKET)).expectExceptionalResult(UnauthorizedException.class)
                .andThen().whenQueryByUser(BOB, new GetGoogleWalletPass(TICKET)).expectExceptionalResult(UnauthorizedException.class);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellationRefusesNewWalletPasses(boolean async) {
        wallet(async).givenCommandsByUser(ALICE, new CancelReservation(R))
                .whenQueryByUser(ALICE, new GetAppleWalletPass(TICKET)).expectExceptionalResult(IllegalCommandException.class)
                .andThen().whenQueryByUser(ALICE, new GetGoogleWalletPass(TICKET)).expectExceptionalResult(IllegalCommandException.class);
    }
}
