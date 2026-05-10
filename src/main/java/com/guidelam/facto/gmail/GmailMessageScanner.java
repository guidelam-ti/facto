package com.guidelam.facto.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.guidelam.facto.config.AsyncConfig;
import com.guidelam.facto.processing.ProcessingJob;
import com.guidelam.facto.processing.ProcessingJobRepository;
import com.guidelam.facto.processing.ProcessingStatus;
import com.guidelam.facto.supplier.SupplierMapping;
import com.guidelam.facto.supplier.SupplierMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailMessageScanner {

    private static final long PAGE_SIZE = 500L;
    private static final int PROGRESS_UPDATE_EVERY = 25;

    private final GmailService gmailService;
    private final ProcessingJobRepository jobRepository;
    private final SupplierMappingRepository mappingRepository;

    @Async(AsyncConfig.FACTO_TASK_EXECUTOR)
    public void runScan(Long jobId) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Scan asked for unknown job id={}", jobId);
            return;
        }
        try {
            executeScan(job);
        } catch (Exception e) {
            log.error("Scan failed for job {}", jobId, e);
            job.setStatus(ProcessingStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 1900));
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private void executeScan(ProcessingJob job) throws IOException {
        Gmail gmail = gmailService.client().orElseThrow(() ->
                new IllegalStateException("Compte Google non connecté"));

        job.setStatus(ProcessingStatus.RUNNING);
        job.setCurrentStep("Initialisation du scan Gmail");
        jobRepository.save(job);

        Map<String, Integer> countsByEmail = new HashMap<>();
        String pageToken = null;
        int processed = 0;
        int pageNum = 0;

        do {
            pageNum++;
            job.setCurrentStep(String.format(
                    "Récupération de la page %d de l'index Gmail…", pageNum));
            jobRepository.save(job);

            var listResp = gmail.users().messages().list("me")
                    .setQ(GmailService.INVOICE_QUERY)
                    .setMaxResults(PAGE_SIZE)
                    .setPageToken(pageToken)
                    .execute();

            // Gmail's resultSizeEstimate is rough and can differ wildly from the
            // real count (we've seen 501 estimated for 1345 actual). Refresh on
            // every page and never let the total fall below what we've already
            // processed, so the progress bar can't regress.
            if (listResp.getResultSizeEstimate() != null) {
                int est = listResp.getResultSizeEstimate().intValue();
                int adjusted = Math.max(Math.max(job.getTotalItems(), est), processed);
                job.setTotalItems(adjusted);
            }

            List<Message> messages = listResp.getMessages();
            if (messages != null) {
                for (Message stub : messages) {
                    Message full = gmail.users().messages().get("me", stub.getId())
                            .setFormat("METADATA")
                            .setMetadataHeaders(List.of("From"))
                            .execute();

                    String from = extractHeader(full, "From");
                    String email = normalizeEmail(from);
                    if (email != null && !email.isBlank()) {
                        countsByEmail.merge(email, 1, Integer::sum);
                    }
                    processed++;

                    if (processed % PROGRESS_UPDATE_EVERY == 0) {
                        saveProgress(job, processed, countsByEmail.size(), pageNum);
                    }
                }
                saveProgress(job, processed, countsByEmail.size(), pageNum);
            }

            pageToken = listResp.getNextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        job.setCurrentStep("Sauvegarde des fournisseurs détectés");
        jobRepository.save(job);

        for (Map.Entry<String, Integer> entry : countsByEmail.entrySet()) {
            SupplierMapping mapping = mappingRepository.findByEmailAddress(entry.getKey())
                    .orElseGet(() -> new SupplierMapping(entry.getKey()));
            mapping.setMessageCount(entry.getValue());
            mappingRepository.save(mapping);
        }

        if (job.getTotalItems() < processed) {
            job.setTotalItems(processed);
        }
        job.setStatus(ProcessingStatus.SUCCESS);
        job.setFinishedAt(Instant.now());
        job.setCurrentStep(String.format("Terminé : %d messages, %d expéditeurs uniques",
                processed, countsByEmail.size()));
        jobRepository.save(job);
    }

    private void saveProgress(ProcessingJob job, int processed, int uniqueSenders, int pageNum) {
        job.setProcessedItems(processed);
        if (job.getTotalItems() < processed) {
            job.setTotalItems(processed);
        }
        job.setCurrentStep(String.format(
                "Page %d · %d messages examinés · %d expéditeurs uniques",
                pageNum, processed, uniqueSenders));
        jobRepository.save(job);
    }

    public static String extractHeader(Message msg, String name) {
        if (msg.getPayload() == null || msg.getPayload().getHeaders() == null) {
            return null;
        }
        for (MessagePartHeader h : msg.getPayload().getHeaders()) {
            if (name.equalsIgnoreCase(h.getName())) {
                return h.getValue();
            }
        }
        return null;
    }

    public static String normalizeEmail(String fromHeader) {
        if (fromHeader == null) return null;
        int lt = fromHeader.lastIndexOf('<');
        int gt = fromHeader.lastIndexOf('>');
        String raw;
        if (lt >= 0 && gt > lt) {
            raw = fromHeader.substring(lt + 1, gt).trim();
        } else {
            raw = fromHeader.trim();
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
