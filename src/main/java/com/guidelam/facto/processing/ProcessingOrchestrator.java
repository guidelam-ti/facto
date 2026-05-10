package com.guidelam.facto.processing;

import com.google.api.services.drive.Drive;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.guidelam.facto.config.AsyncConfig;
import com.guidelam.facto.drive.DriveFolderManager;
import com.guidelam.facto.drive.DriveService;
import com.guidelam.facto.gmail.GmailMessageScanner;
import com.guidelam.facto.gmail.GmailService;
import com.guidelam.facto.invoice.InvoiceFiles;
import com.guidelam.facto.invoice.ProcessedInvoice;
import com.guidelam.facto.invoice.ProcessedInvoiceRepository;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import com.guidelam.facto.supplier.Supplier;
import com.guidelam.facto.supplier.SupplierMapping;
import com.guidelam.facto.supplier.SupplierMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingOrchestrator {

    private static final long PAGE_SIZE = 500L;
    private static final int PROGRESS_UPDATE_EVERY = 5;

    private final GmailService gmailService;
    private final DriveService driveService;
    private final SettingsService settings;
    private final SupplierMappingRepository mappingRepository;
    private final ProcessedInvoiceRepository invoiceRepository;
    private final ProcessingJobRepository jobRepository;

    @Async(AsyncConfig.FACTO_TASK_EXECUTOR)
    public void runProcess(Long jobId) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Process asked for unknown job id={}", jobId);
            return;
        }
        try {
            executeProcess(job);
        } catch (Exception e) {
            log.error("Process failed for job {}", jobId, e);
            job.setStatus(ProcessingStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 1900));
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private void executeProcess(ProcessingJob job) throws IOException {
        Gmail gmail = gmailService.client().orElseThrow(() ->
                new IllegalStateException("Compte Google non connecté"));
        Drive drive = driveService.client().orElseThrow(() ->
                new IllegalStateException("Compte Google non connecté"));

        String rootFolderId = settings.get(AppSettingKeys.DRIVE_ROOT_FOLDER_ID)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun dossier Drive racine sélectionné — va dans Configuration."));

        if (job.getPeriodStart() == null || job.getPeriodEnd() == null) {
            throw new IllegalStateException("Période non spécifiée");
        }
        if (job.getPeriodStart().isAfter(job.getPeriodEnd())) {
            throw new IllegalStateException("Date de début après date de fin");
        }

        job.setStatus(ProcessingStatus.RUNNING);
        job.setCurrentStep("Recherche des messages dans la période");
        jobRepository.save(job);

        List<String> messageIds = listMessageIdsInPeriod(gmail, job);
        job.setTotalItems(messageIds.size());
        job.setCurrentStep(String.format("%d message(s) à examiner", messageIds.size()));
        jobRepository.save(job);

        DriveFolderManager folderManager = new DriveFolderManager(drive, rootFolderId);

        int processed = 0;
        int errors = 0;
        int archived = 0;
        String firstErrorMessage = null;
        for (String messageId : messageIds) {
            try {
                archived += processMessage(messageId, gmail, drive, folderManager);
            } catch (Exception e) {
                errors++;
                log.warn("Failed to process message {}", messageId, e);
                if (firstErrorMessage == null) {
                    firstErrorMessage = String.format("msg %s : %s — %s",
                            messageId, e.getClass().getSimpleName(), e.getMessage());
                }
            }
            processed++;
            if (processed % PROGRESS_UPDATE_EVERY == 0) {
                saveProgress(job, processed, errors, archived);
            }
        }

        job.setProcessedItems(processed);
        job.setArchivedItems(archived);
        job.setErrorCount(errors);
        job.setStatus(ProcessingStatus.SUCCESS);
        if (firstErrorMessage != null) {
            job.setErrorMessage(truncate(firstErrorMessage, 1900));
        }
        job.setCurrentStep(String.format(
                "Terminé : %d message(s) examiné(s), %d facture(s) archivée(s), %d erreur(s)",
                processed, archived, errors));
        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
    }

    private void saveProgress(ProcessingJob job, int processed, int errors, int archived) {
        job.setProcessedItems(processed);
        job.setArchivedItems(archived);
        job.setErrorCount(errors);
        job.setCurrentStep(String.format(
                "Examiné %d/%d · %d facture(s) archivée(s) · %d erreur(s)",
                processed, job.getTotalItems(), archived, errors));
        jobRepository.save(job);
    }

    private List<String> listMessageIdsInPeriod(Gmail gmail, ProcessingJob job) throws IOException {
        // Gmail's "before:" is exclusive, so we add one day to the end date for inclusivity.
        String periodQuery = GmailService.INVOICE_QUERY
                + " after:" + InvoiceFiles.gmailDateQuery(job.getPeriodStart())
                + " before:" + InvoiceFiles.gmailDateQuery(job.getPeriodEnd().plusDays(1));

        List<String> ids = new ArrayList<>();
        String pageToken = null;
        int pageNum = 0;
        do {
            pageNum++;
            job.setCurrentStep(String.format(
                    "Récupération de la page %d de l'index Gmail (%s → %s)…",
                    pageNum, job.getPeriodStart(), job.getPeriodEnd()));
            jobRepository.save(job);

            var resp = gmail.users().messages().list("me")
                    .setQ(periodQuery)
                    .setMaxResults(PAGE_SIZE)
                    .setPageToken(pageToken)
                    .execute();
            if (resp.getMessages() != null) {
                for (Message m : resp.getMessages()) {
                    ids.add(m.getId());
                }
            }
            pageToken = resp.getNextPageToken();
        } while (pageToken != null && !pageToken.isBlank());
        return ids;
    }

    /**
     * Processes a single Gmail message: skip if sender unmapped/ignored, otherwise
     * download all PDF attachments not yet archived and upload them to Drive.
     *
     * @return number of new ProcessedInvoice rows persisted (0 if all skipped)
     */
    private int processMessage(String messageId, Gmail gmail, Drive drive,
                               DriveFolderManager folderManager) throws IOException {
        Message full = gmail.users().messages().get("me", messageId)
                .setFormat("FULL")
                .execute();

        String fromHeader = GmailMessageScanner.extractHeader(full, "From");
        String email = GmailMessageScanner.normalizeEmail(fromHeader);
        if (email == null || email.isBlank()) {
            log.debug("Message {} has no From header, skipping", messageId);
            return 0;
        }

        SupplierMapping mapping = mappingRepository
                .findByEmailAddressFetchSupplier(email)
                .orElse(null);
        if (mapping == null) {
            log.warn("No SupplierMapping for {} (msg {}), skipping", email, messageId);
            return 0;
        }
        if (mapping.isIgnored()) {
            return 0;
        }
        Supplier supplier = mapping.getSupplier();
        if (supplier == null) {
            log.warn("Mapping for {} not yet decided, skipping (msg {})", email, messageId);
            return 0;
        }

        Long internal = full.getInternalDate();
        if (internal == null) {
            log.warn("Message {} has no internalDate, skipping", messageId);
            return 0;
        }
        LocalDate invoiceDate = Instant.ofEpochMilli(internal)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        List<MessagePart> pdfParts = new ArrayList<>();
        collectPdfParts(full.getPayload(), pdfParts);
        if (pdfParts.isEmpty()) {
            return 0;
        }

        String monthFolderId = null;
        int saved = 0;
        for (int idx = 0; idx < pdfParts.size(); idx++) {
            MessagePart part = pdfParts.get(idx);
            String attachmentId = part.getBody().getAttachmentId();

            if (invoiceRepository.findByGmailMessageIdAndGmailAttachmentId(messageId, attachmentId).isPresent()) {
                log.debug("Already archived {}/{}, skipping", messageId, attachmentId);
                continue;
            }

            byte[] data = downloadAttachment(gmail, messageId, attachmentId);
            String hash = InvoiceFiles.sha256Hex(data);

            if (invoiceRepository.findByFileHash(hash).isPresent()) {
                log.debug("Same hash already archived (msg {}, att {}), skipping", messageId, attachmentId);
                continue;
            }

            String archivedName = resolveArchivedName(invoiceDate, supplier.getCanonicalName());

            if (monthFolderId == null) {
                monthFolderId = folderManager.ensureMonthFolder(
                        invoiceDate.getYear(), invoiceDate.getMonthValue());
            }

            String driveFileId = driveService.uploadPdf(drive, monthFolderId, archivedName, data);

            ProcessedInvoice inv = new ProcessedInvoice();
            inv.setGmailMessageId(messageId);
            inv.setGmailAttachmentId(attachmentId);
            inv.setFileHash(hash);
            inv.setSupplier(supplier);
            inv.setInvoiceDate(invoiceDate);
            inv.setOriginalFileName(part.getFilename() != null ? part.getFilename() : "(unnamed).pdf");
            inv.setArchivedFileName(archivedName);
            inv.setDriveFileId(driveFileId);
            inv.setDrivePath(InvoiceFiles.buildDrivePath(invoiceDate, archivedName));
            invoiceRepository.save(inv);
            saved++;
            log.info("Archived {} → {}", part.getFilename(), inv.getDrivePath());
        }
        return saved;
    }

    /**
     * Resolves a non-conflicting archive filename. If the base name
     * {@code <date>_facture_<NAME>.pdf} already exists in DB for this year/month
     * folder, append {@code _1}, {@code _2}, ... until a free slot is found.
     * Handles both same-message multi-PDF and cross-message same-day collisions
     * uniformly: each successive PDF queries DB for the next free name.
     */
    private String resolveArchivedName(LocalDate date, String canonicalSupplierName) {
        String pathPrefix = date.getYear() + "/" + InvoiceFiles.monthFolderName(date.getMonthValue());
        String baseName = InvoiceFiles.buildArchivedFileName(date, canonicalSupplierName, 0);
        if (!invoiceRepository.existsByDrivePath(pathPrefix + "/" + baseName)) {
            return baseName;
        }
        String stem = baseName.substring(0, baseName.length() - 4); // strip ".pdf"
        for (int seq = 1; seq < 10000; seq++) {
            String candidate = stem + "_" + seq + ".pdf";
            if (!invoiceRepository.existsByDrivePath(pathPrefix + "/" + candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot disambiguate archive name: " + baseName);
    }

    private static void collectPdfParts(MessagePart part, List<MessagePart> out) {
        if (part == null) return;
        String filename = part.getFilename();
        boolean hasAttachment = part.getBody() != null && part.getBody().getAttachmentId() != null;
        if (filename != null && filename.toLowerCase().endsWith(".pdf") && hasAttachment) {
            out.add(part);
        }
        if (part.getParts() != null) {
            for (MessagePart sub : part.getParts()) {
                collectPdfParts(sub, out);
            }
        }
    }

    private static byte[] downloadAttachment(Gmail gmail, String messageId, String attachmentId)
            throws IOException {
        var body = gmail.users().messages().attachments()
                .get("me", messageId, attachmentId)
                .execute();
        return Base64.getUrlDecoder().decode(body.getData());
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
