package com.guidelam.facto.invoice;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Utility helpers for the invoice archiving pipeline: file naming, hashing,
 * French month folder names. Pure functions, no Spring beans needed.
 */
public final class InvoiceFiles {

    private static final String[] FRENCH_MONTHS = {
            "01-janvier", "02-fevrier", "03-mars", "04-avril",
            "05-mai", "06-juin", "07-juillet", "08-aout",
            "09-septembre", "10-octobre", "11-novembre", "12-decembre"
    };

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter GMAIL_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private InvoiceFiles() {
    }

    /**
     * "Hydro-Québec" → "HYDRO-QUEBEC", "Société générale" → "SOCIETE-GENERALE".
     * Strips accents, uppercases, replaces non-alphanumerics with dashes,
     * trims leading/trailing dashes.
     */
    public static String sanitizeSupplierName(String canonical) {
        if (canonical == null || canonical.isBlank()) return "INCONNU";
        String stripped = Normalizer.normalize(canonical, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String upper = stripped.toUpperCase(Locale.ROOT);
        String dashed = upper.replaceAll("[^A-Z0-9]+", "-");
        String trimmed = dashed.replaceAll("^-+", "").replaceAll("-+$", "");
        return trimmed.isEmpty() ? "INCONNU" : trimmed;
    }

    /**
     * Build the archived filename per spec : {@code aaaa-mm-jj_facture_NOM-FOURNISSEUR.pdf}
     * with an optional {@code _N} suffix when several PDFs share the same email
     * (1-based, only applied when there is more than one).
     *
     * @param invoiceDate         the email date (Phase 1)
     * @param supplierCanonical   supplier canonical name (will be sanitized)
     * @param positionWhenMultiple 1-based position if there are multiple PDFs in the
     *                             same message; pass 0 (or any value < 1) for a single PDF
     */
    public static String buildArchivedFileName(LocalDate invoiceDate,
                                               String supplierCanonical,
                                               int positionWhenMultiple) {
        String date = invoiceDate.format(ISO_DATE);
        String name = sanitizeSupplierName(supplierCanonical);
        String suffix = positionWhenMultiple >= 1 ? "_" + positionWhenMultiple : "";
        return date + "_facture_" + name + suffix + ".pdf";
    }

    public static String monthFolderName(int monthOneBased) {
        if (monthOneBased < 1 || monthOneBased > 12) {
            throw new IllegalArgumentException("Invalid month: " + monthOneBased);
        }
        return FRENCH_MONTHS[monthOneBased - 1];
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    public static String gmailDateQuery(LocalDate d) {
        return d.format(GMAIL_DATE);
    }

    /**
     * Builds the Drive path string stored on ProcessedInvoice for display purposes.
     * Example: {@code 2024/03-mars/2024-03-15_facture_BELL.pdf}.
     */
    public static String buildDrivePath(LocalDate invoiceDate, String archivedFileName) {
        return invoiceDate.getYear() + "/" + monthFolderName(invoiceDate.getMonthValue())
                + "/" + archivedFileName;
    }
}
