package com.guidelam.facto.invoice;

import com.guidelam.facto.supplier.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProcessedInvoiceRepository extends JpaRepository<ProcessedInvoice, Long> {

    Optional<ProcessedInvoice> findByGmailMessageIdAndGmailAttachmentId(
            String gmailMessageId, String gmailAttachmentId);

    Optional<ProcessedInvoice> findByFileHash(String fileHash);

    List<ProcessedInvoice> findByInvoiceDateBetweenOrderByInvoiceDateDesc(
            LocalDate start, LocalDate end);

    List<ProcessedInvoice> findBySupplierOrderByInvoiceDateDesc(Supplier supplier);

    List<ProcessedInvoice> findAllByOrderByInvoiceDateDesc();
}
