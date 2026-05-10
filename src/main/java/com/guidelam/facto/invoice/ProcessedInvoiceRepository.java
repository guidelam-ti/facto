package com.guidelam.facto.invoice;

import com.guidelam.facto.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProcessedInvoiceRepository extends JpaRepository<ProcessedInvoice, Long> {

    Optional<ProcessedInvoice> findByGmailMessageIdAndGmailAttachmentId(
            String gmailMessageId, String gmailAttachmentId);

    Optional<ProcessedInvoice> findByFileHash(String fileHash);

    boolean existsByDrivePath(String drivePath);

    List<ProcessedInvoice> findByInvoiceDateBetweenOrderByInvoiceDateDesc(
            LocalDate start, LocalDate end);

    List<ProcessedInvoice> findBySupplierOrderByInvoiceDateDesc(Supplier supplier);

    List<ProcessedInvoice> findAllByOrderByInvoiceDateDesc();

    /**
     * Variant that eagerly fetches the supplier so callers can dereference
     * {@code invoice.getSupplier().getDisplayName()} after the session closes
     * (open-in-view stays disabled). Sorted newest-first by invoice date.
     */
    @Query("SELECT i FROM ProcessedInvoice i LEFT JOIN FETCH i.supplier " +
            "ORDER BY i.invoiceDate DESC, i.id DESC")
    List<ProcessedInvoice> findAllWithSupplierOrderByInvoiceDateDesc();
}
