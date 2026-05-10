package com.guidelam.facto.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProcessService {

    private final ProcessingJobRepository jobRepository;
    private final ProcessingOrchestrator orchestrator;

    /**
     * Intentionally NOT @Transactional. Same reason as ScanService.startScan: the
     * job row must be committed BEFORE the @Async dispatch so the worker thread
     * sees it via findById.
     */
    public ProcessingJob startProcess(LocalDate periodStart, LocalDate periodEnd) {
        var running = jobRepository.findByStatusOrderByStartedAtDesc(ProcessingStatus.RUNNING);
        boolean alreadyRunning = running.stream()
                .anyMatch(j -> j.getType() == ProcessingJobType.PROCESS);
        if (alreadyRunning) {
            throw new IllegalStateException("Un traitement est déjà en cours.");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalStateException("La période est requise.");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalStateException("La date de début doit être antérieure ou égale à la date de fin.");
        }

        ProcessingJob job = new ProcessingJob(ProcessingJobType.PROCESS);
        job.setPeriodStart(periodStart);
        job.setPeriodEnd(periodEnd);
        job.setCurrentStep("En attente de démarrage");
        ProcessingJob saved = jobRepository.save(job);
        orchestrator.runProcess(saved.getId());
        return saved;
    }
}
