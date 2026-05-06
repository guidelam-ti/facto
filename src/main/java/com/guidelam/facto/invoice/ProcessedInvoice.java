package com.guidelam.facto.invoice;

import com.guidelam.facto.supplier.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "processed_invoice",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_processed_invoice_message_attachment",
                columnNames = {"gmail_message_id", "gmail_attachment_id"}
        ),
        indexes = {
                @Index(name = "idx_processed_invoice_message_id", columnList = "gmail_message_id"),
                @Index(name = "idx_processed_invoice_file_hash", columnList = "file_hash"),
                @Index(name = "idx_processed_invoice_invoice_date", columnList = "invoice_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gmail_message_id", nullable = false, length = 100)
    private String gmailMessageId;

    @Column(name = "gmail_attachment_id", nullable = false, length = 200)
    private String gmailAttachmentId;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "archived_file_name", nullable = false, length = 500)
    private String archivedFileName;

    @Column(name = "drive_file_id", nullable = false, length = 100)
    private String driveFileId;

    @Column(name = "drive_path", nullable = false, length = 500)
    private String drivePath;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
