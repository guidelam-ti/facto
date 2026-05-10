package com.guidelam.facto.processing;

import com.google.api.services.drive.Drive;
import com.guidelam.facto.config.AsyncConfig;
import com.guidelam.facto.drive.DriveService;
import com.guidelam.facto.invoice.InvoiceFiles;
import com.guidelam.facto.invoice.ProcessedInvoice;
import com.guidelam.facto.invoice.ProcessedInvoiceRepository;
import com.guidelam.facto.settings.AppSettingKeys;
import com.guidelam.facto.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @Async worker for reset jobs. Lives in its own bean (not in {@link ResetService})
 * because Spring's AOP proxying only intercepts external method calls — calling
 * an @Async method from another method in the same class bypasses the proxy and
 * runs synchronously. Same pattern as {@link com.guidelam.facto.gmail.GmailMessageScanner}
 * for SCAN and {@link ProcessingOrchestrator} for PROCESS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResetOrchestrator {

    private static final int PROGRESS_UPDATE_EVERY = 5;

    private final DriveService driveService;
    private final SettingsService settings;
    private final ProcessedInvoiceRepository invoiceRepository;
    private final ProcessingJobRepository jobRepository;

    @Async(AsyncConfig.FACTO_TASK_EXECUTOR)
    public void runReset(Long jobId) {
        ProcessingJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Reset asked for unknown job id={}", jobId);
            return;
        }
        try {
            executeReset(job);
        } catch (Exception e) {
            log.error("Reset failed for job {}", jobId, e);
            job.setStatus(ProcessingStatus.FAILED);
            job.setErrorMessage(truncate(e.getMessage(), 1900));
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    private void executeReset(ProcessingJob job) throws IOException {
        Drive drive = driveService.client().orElseThrow(() ->
                new IllegalStateException("Compte Google non connecté"));

        String rootFolderId = settings.get(AppSettingKeys.DRIVE_ROOT_FOLDER_ID)
                .filter(s -> !s.isBlank())
                .orElse(null);

        job.setStatus(ProcessingStatus.RUNNING);
        job.setCurrentStep("Comptage des factures à supprimer");
        jobRepository.save(job);

        List<ProcessedInvoice> invoices = invoiceRepository.findAll();
        job.setTotalItems(invoices.size());
        job.setCurrentStep(String.format("%d facture(s) à supprimer de Drive et de la base", invoices.size()));
        jobRepository.save(job);

        // Phase 1: delete each PDF from Drive + DB row, track unique year/month folders
        Set<int[]> yearMonths = new HashSet<>();
        Map<Integer, Boolean> seenYears = new HashMap<>();
        int processed = 0;
        int errors = 0;
        for (ProcessedInvoice inv : invoices) {
            try {
                driveService.deleteFile(drive, inv.getDriveFileId());
                int year = inv.getInvoiceDate().getYear();
                int month = inv.getInvoiceDate().getMonthValue();
                yearMonths.add(new int[]{year, month});
                seenYears.put(year, Boolean.TRUE);
                invoiceRepository.delete(inv);
            } catch (Exception e) {
                errors++;
                log.warn("Failed to delete Drive file {} (DB row kept)", inv.getDriveFileId(), e);
            }
            processed++;
            if (processed % PROGRESS_UPDATE_EVERY == 0) {
                job.setProcessedItems(processed);
                job.setErrorCount(errors);
                job.setCurrentStep(String.format("Suppression des fichiers : %d / %d", processed, invoices.size()));
                jobRepository.save(job);
            }
        }
        job.setProcessedItems(processed);
        job.setErrorCount(errors);
        jobRepository.save(job);

        // Phase 2 + 3: cleanup empty year/month folders, then empty year folders
        if (rootFolderId != null) {
            job.setCurrentStep("Suppression des dossiers Drive vides");
            jobRepository.save(job);
            cleanupEmptyMonthFolders(drive, rootFolderId, yearMonths);
            cleanupEmptyYearFolders(drive, rootFolderId, seenYears.keySet());
        } else {
            log.warn("No drive root folder configured — skipping folder cleanup");
        }

        // Phase 4: clear PROCESS job history (keep current RESET and SCAN jobs)
        job.setCurrentStep("Nettoyage de l'historique des jobs de traitement");
        jobRepository.save(job);
        List<ProcessingJob> oldProcess = jobRepository.findByTypeOrderByStartedAtDesc(ProcessingJobType.PROCESS);
        jobRepository.deleteAll(oldProcess);

        // Final state
        job.setStatus(ProcessingStatus.SUCCESS);
        job.setFinishedAt(Instant.now());
        job.setCurrentStep(String.format(
                "Terminé : %d facture(s) supprimée(s), %d erreur(s), %d job(s) PROCESS purgé(s)",
                processed, errors, oldProcess.size()));
        jobRepository.save(job);
    }

    private void cleanupEmptyMonthFolders(Drive drive, String rootFolderId, Set<int[]> yearMonths) {
        for (int[] ym : yearMonths) {
            try {
                Optional<String> yearId = driveService.findFolderByName(drive, rootFolderId, String.valueOf(ym[0]));
                if (yearId.isEmpty()) continue;
                Optional<String> monthId = driveService.findFolderByName(drive, yearId.get(),
                        InvoiceFiles.monthFolderName(ym[1]));
                if (monthId.isEmpty()) continue;
                if (driveService.isFolderEmpty(drive, monthId.get())) {
                    driveService.deleteFile(drive, monthId.get());
                    log.debug("Deleted empty month folder {}/{}", ym[0], ym[1]);
                }
            } catch (IOException e) {
                log.warn("Failed cleanup of month folder {}/{}: {}", ym[0], ym[1], e.getMessage());
            }
        }
    }

    private void cleanupEmptyYearFolders(Drive drive, String rootFolderId, Set<Integer> years) {
        for (Integer year : years) {
            try {
                Optional<String> yearId = driveService.findFolderByName(drive, rootFolderId, String.valueOf(year));
                if (yearId.isEmpty()) continue;
                if (driveService.isFolderEmpty(drive, yearId.get())) {
                    driveService.deleteFile(drive, yearId.get());
                    log.debug("Deleted empty year folder {}", year);
                }
            } catch (IOException e) {
                log.warn("Failed cleanup of year folder {}: {}", year, e.getMessage());
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
