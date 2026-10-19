package com.kvs.finance.service;

import com.kvs.finance.entity.FeeAccount;
import com.kvs.finance.entity.FeePaymentReceipt;
import com.kvs.finance.entity.FeeReceiptAllocation;
import com.kvs.finance.enums.ReceiptStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A one-page A4 PDF of a fee receipt (US-04004), written directly in PDF 1.4 with the standard Helvetica fonts,
 * so no PDF library is needed. Amounts are printed as "Rs. 18,000.00" (the rupee sign is not in the base fonts).
 */
@Component
public class ReceiptPdfWriter {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public byte[] write(FeePaymentReceipt r, ZoneId zone) {
        FeeAccount a = r.getAccount();
        StringBuilder c = new StringBuilder();
        text(c, "F2", 18, 50, 790, "Fee Receipt");
        text(c, "F1", 10, 50, 772, "Branch " + r.getBranchId() + "  |  Academic year " + a.getAcademicYear());
        text(c, "F2", 12, 400, 790, r.getReceiptNumber());
        text(c, "F1", 10, 400, 772, WHEN.format(r.getPaymentTimestamp().atZone(zone)));
        line(c, 50, 760, 545, 760);

        int y = 735;
        String section = a.getSectionName() == null ? "" : "-" + a.getSectionName();
        String[][] rows = {
                {"Student", a.getStudentName() + " (" + a.getStudentUid() + ")"},
                {"Class / section", a.getGradeName() + section},
                {"Received from", r.getPayerName() == null ? "-" : r.getPayerName()},
                {"Payment mode", r.getPaymentMode().name().replace('_', ' ') + instrument(r)},
                {"Collected by", r.getCollectedBy() == null ? "-" : r.getCollectedBy()},
        };
        for (String[] row : rows) {
            text(c, "F2", 10, 50, y, row[0]);
            text(c, "F1", 10, 170, y, row[1]);
            y -= 18;
        }

        y -= 10;
        text(c, "F2", 10, 50, y, "Fee head");
        text(c, "F2", 10, 300, y, "Installment");
        text(c, "F2", 10, 470, y, "Amount");
        line(c, 50, y - 6, 545, y - 6);
        y -= 22;
        for (FeeReceiptAllocation x : r.getAllocations()) {
            text(c, "F1", 10, 50, y, x.getLedger().getFeeHead().getHeadName());
            text(c, "F1", 10, 300, y, x.getLedger().getInstallmentLabel());
            text(c, "F1", 10, 470, y, money(x.getAmount()));
            y -= 16;
        }
        line(c, 50, y + 6, 545, y + 6);
        y -= 10;
        text(c, "F2", 11, 300, y, "Total paid");
        text(c, "F2", 11, 470, y, money(r.getTotalAmountPaid()));

        if (r.getReceiptStatus() == ReceiptStatus.BOUNCED) {
            y -= 36;
            text(c, "F2", 12, 50, y, "CHEQUE BOUNCED on " + DAY.format(r.getBouncedAt().atZone(zone)) + " - this receipt is void");
            y -= 16;
            text(c, "F1", 10, 50, y, "Reason: " + r.getBounceReason());
        }
        text(c, "F1", 8, 50, 60, "Computer-generated receipt. Verify at the school fee counter with the receipt number.");
        return assemble(c.toString());
    }

    private static String instrument(FeePaymentReceipt r) {
        if (r.getInstrumentNumber() == null) {
            return "";
        }
        return " No. " + r.getInstrumentNumber() + (r.getBankName() == null ? "" : ", " + r.getBankName())
                + (r.getInstrumentDate() == null ? "" : ", dated " + DAY.format(r.getInstrumentDate()));
    }

    static String money(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "Rs. " + f.format(amount);
    }

    private static void text(StringBuilder c, String font, int size, int x, int y, String s) {
        c.append("BT /").append(font).append(' ').append(size).append(" Tf ").append(x).append(' ').append(y)
                .append(" Td (").append(escape(s)).append(") Tj ET\n");
    }

    private static void line(StringBuilder c, int x1, int y1, int x2, int y2) {
        c.append("0.5 w ").append(x1).append(' ').append(y1).append(" m ").append(x2).append(' ').append(y2).append(" l S\n");
    }

    /** Keeps printable ASCII only (the base-14 fonts) and escapes the PDF string delimiters. */
    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == ')' || ch == '\\') {
                out.append('\\').append(ch);
            } else if (ch >= 32 && ch < 127) {
                out.append(ch);
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }

    private static byte[] assemble(String content) {
        byte[] stream = content.getBytes(StandardCharsets.US_ASCII);
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 6 0 R "
                + "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Length " + stream.length + " >>\nstream\n" + content + "endstream");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        offsets.forEach(o -> x.append(String.format("%010d 00000 n \n", o)));
        x.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, x.toString());
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
