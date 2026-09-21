package io.fluxzero.ticketing.admission;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import io.fluxzero.ticketing.admission.api.GetTicketPass.Pass;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/** A portable ticket with a vector QR, an embedded font and a human-readable seat identity. */
public final class TicketPdf {
    private TicketPdf() {}

    /** Keep arbitrary programme text inside the printable area without touching the admission code. */
    private static String fit(PDType0Font font, String value, float size, float width) throws java.io.IOException {
        var printable = new StringBuilder();
        float used = 0;
        float ellipsis = font.getStringWidth("…") * size / 1000;
        boolean clipped = false;
        for (int codePoint : value.codePoints().toArray()) {
            String glyph = Character.isISOControl(codePoint) ? " " : new String(Character.toChars(codePoint));
            try { font.encode(glyph); }
            catch (IllegalArgumentException unsupportedGlyph) { glyph = "?"; }
            float glyphWidth = font.getStringWidth(glyph) * size / 1000;
            if (used + glyphWidth > width - ellipsis) { clipped = true; break; }
            printable.append(glyph);
            used += glyphWidth;
        }
        return clipped ? printable + "…" : printable.toString();
    }
    public static byte[] render(Pass pass) {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream();
             var fontData = PDDocument.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            var font = PDType0Font.load(document, fontData);
            var page = new PDPage(PDRectangle.A4); document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(font, 12); content.newLineAtOffset(54, 730);
                content.showText("FLUXZERO / TICKET"); content.newLineAtOffset(0, -48);
                content.setFont(font, 22); content.showText(fit(font, pass.title(), 22, 504)); content.newLineAtOffset(0, -34);
                content.setFont(font, 12);
                content.showText(DateTimeFormatter.ofPattern("EEE d MMM uuuu, HH:mm", java.util.Locale.ENGLISH)
                        .withZone(pass.timeZone()).format(pass.startsAt()));
                for (String line : new String[]{pass.hall(), pass.section() + " / " + pass.seat(),
                        pass.ticket().admission().ticketTypeName(), pass.ticket().ticketId().toString(), "Demo ticket - not valid for real venue entry"}) {
                    content.newLineAtOffset(0, -22); content.showText(fit(font, line, 12, 504));
                }
                content.endText();
                var qr = new QRCodeWriter().encode(pass.credential(), BarcodeFormat.QR_CODE, 1, 1);
                float scale = 220f / qr.getWidth();
                for (int y = 0; y < qr.getHeight(); y++) for (int x = 0; x < qr.getWidth(); x++) {
                    if (qr.get(x, y)) content.addRect(54 + x * scale, 220 + (qr.getHeight() - y - 1) * scale, scale, scale);
                }
                content.fill();
            }
            document.save(output); return output.toByteArray();
        } catch (java.io.IOException | com.google.zxing.WriterException e) {
            throw new IllegalStateException("Could not create ticket PDF", e);
        }
    }
}
