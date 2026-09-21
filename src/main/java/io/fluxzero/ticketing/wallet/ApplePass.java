package io.fluxzero.ticketing.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxzero.sdk.Fluxzero;
import io.fluxzero.ticketing.admission.api.GetTicketPass.Pass;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.fluxzero.sdk.configuration.ApplicationProperties.requireProperty;

/** PassKit package with a detached CMS signature. The PKCS12 must include the WWDR intermediate. */
public final class ApplePass {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ApplePass() {}

    public static byte[] create(Pass pass) {
        try {
            var store = KeyStore.getInstance("PKCS12");
            char[] password = requireProperty("ticketing.wallet.apple.password").toCharArray();
            PrivateKey key;
            java.security.cert.Certificate[] chain;
            try {
                try (var input = Files.newInputStream(Path.of(requireProperty("ticketing.wallet.apple.keystore")))) {
                    store.load(input, password);
                }
                var aliases = Collections.list(store.aliases()).stream().filter(a -> {
                    try { return store.isKeyEntry(a); } catch (Exception e) { throw new IllegalStateException(e); }
                }).toList();
                if (aliases.size() != 1) throw new IllegalStateException("Use a PKCS12 with exactly one pass signing key");
                String alias = aliases.getFirst();
                key = (PrivateKey) store.getKey(alias, password);
                chain = store.getCertificateChain(alias);
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
            if (chain == null || chain.length < 2) throw new IllegalStateException("Include the WWDR certificate in the PKCS12 chain");
            var certificate = (X509Certificate) chain[0];
            certificate.checkValidity(Date.from(Fluxzero.currentTime()));
            var subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
            String passType = IETFUtils.valueToString(subject.getRDNs(BCStyle.UID)[0].getFirst().getValue());
            String team = IETFUtils.valueToString(subject.getRDNs(BCStyle.OU)[0].getFirst().getValue());
            if (!passType.startsWith("pass.")) throw new IllegalStateException("Use an Apple Pass Type ID certificate");
            var data = new LinkedHashMap<String, Object>();
            data.put("formatVersion", 1);
            data.put("passTypeIdentifier", passType);
            data.put("teamIdentifier", team);
            data.put("serialNumber", WalletIdentity.digest(pass.credential()));
            data.put("organizationName", "Fluxzero Ticketing");
            data.put("description", "Demo event ticket: " + pass.title());
            data.put("logoText", "Fluxzero Ticketing");
            data.put("foregroundColor", "rgb(255, 255, 255)");
            data.put("backgroundColor", "rgb(23, 31, 28)");
            data.put("labelColor", "rgb(195, 216, 204)");
            data.put("relevantDate", pass.startsAt().toString());
            data.put("voided", pass.checkIn() != null);
            data.put("barcodes", List.of(Map.of("format", "PKBarcodeFormatQR", "message", pass.credential(), "messageEncoding", "iso-8859-1")));
            data.put("eventTicket", Map.of(
                    "primaryFields", List.of(field("event", "EVENT", pass.title())),
                    "secondaryFields", List.of(field("hall", "VENUE", pass.hall()), field("section", "SECTION", pass.section())),
                    "auxiliaryFields", List.of(field("seat", "PLACE", pass.seat()),
                            field("ticketType", "TICKET", pass.ticket().admission().ticketTypeName()),
                            field("date", "START", pass.startsAt().atZone(pass.timeZone()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z", java.util.Locale.ENGLISH)))),
                    "backFields", List.of(field("demo", "DEMONSTRATION", "Not valid for real venue entry. Admission checks current ticket status online."))));
            var files = new LinkedHashMap<String, byte[]>();
            files.put("pass.json", JSON.writeValueAsBytes(data));
            for (int scale = 1; scale <= 3; scale++) files.put("icon" + (scale == 1 ? "" : "@" + scale + "x") + ".png", icon(29 * scale));
            var manifest = new LinkedHashMap<String, String>();
            for (var entry : files.entrySet()) manifest.put(entry.getKey(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(entry.getValue())));
            byte[] manifestBytes = JSON.writeValueAsBytes(manifest);
            var signer = new CMSSignedDataGenerator();
            var attributes = new java.util.Hashtable<org.bouncycastle.asn1.ASN1ObjectIdentifier, org.bouncycastle.asn1.cms.Attribute>();
            attributes.put(org.bouncycastle.asn1.cms.CMSAttributes.signingTime,
                    new org.bouncycastle.asn1.cms.Attribute(org.bouncycastle.asn1.cms.CMSAttributes.signingTime,
                            new org.bouncycastle.asn1.DERSet(new org.bouncycastle.asn1.cms.Time(Date.from(Fluxzero.currentTime())))));
            signer.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                    .setSignedAttributeGenerator(new org.bouncycastle.cms.DefaultSignedAttributeTableGenerator(new org.bouncycastle.asn1.cms.AttributeTable(attributes)))
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(key), certificate));
            signer.addCertificates(new JcaCertStore(List.of(chain)));
            files.put("manifest.json", manifestBytes);
            files.put("signature", signer.generate(new CMSProcessableByteArray(manifestBytes), false).getEncoded());
            var output = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(output)) {
                for (var entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create Apple Wallet pass; check the pass certificate and keystore configuration", e);
        }
    }

    private static Map<String, String> field(String key, String label, String value) { return Map.of("key", key, "label", label, "value", value); }

    private static byte[] icon(int size) throws java.io.IOException {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(23, 31, 28)); g.fillRect(0, 0, size, size);
            g.setColor(new Color(213, 255, 112));
            g.fillRoundRect(size / 5, size / 4, size * 3 / 5, size / 2, size / 10, size / 10);
            g.setColor(new Color(23, 31, 28));
            g.fillOval(size / 8, size * 2 / 5, size / 5, size / 5);
            g.fillOval(size * 7 / 10, size * 2 / 5, size / 5, size / 5);
        } finally { g.dispose(); }
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
    }
}
