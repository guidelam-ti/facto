package com.guidelam.facto.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ResetService {

    private final ProcessingJobRepository jobRepository;
    private final ResetOrchestrator orchestrator;

    /**
     * Intentionally NOT @Transactional — same reasoning as ScanService.startScan
     * and ProcessService.startProcess: the job row must be committed before the
     * @Async dispatch.
     *
     * <p>When {@code periodStart} and {@code periodEnd} are both null, this is a
     * full reset (delete every invoice + purge PROCESS history). When both are
     * set, the reset is scoped: only invoices with {@code invoiceDate} in
     * {@code [periodStart, periodEnd]} (both inclusive) are deleted, and PROCESS
     * job history is left untouched.
     */
    public ProcessingJob startReset(LocalDate periodStart, LocalDate periodEnd) {
        var running = jobRepository.findByStatusOrderByStartedAtDesc(ProcessingStatus.RUNNING);
        boolean alreadyRunning = running.stream()
                .anyMatch(j -> j.getType() == ProcessingJobType.RESET);
        if (alreadyRunning) {
            throw new IllegalStateException("Un nettoyage est déjà en cours.");
        }
        ProcessingJob job = new ProcessingJob(ProcessingJobType.RESET);
        job.setPeriodStart(periodStart);
        job.setPeriodEnd(periodEnd);
        job.setCurrentStep("En attente de démarrage");
        ProcessingJob saved = jobRepository.save(job);
        orchestrator.runReset(saved.getId());
        return saved;
    }
}
