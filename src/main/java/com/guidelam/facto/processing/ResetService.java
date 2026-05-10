package com.guidelam.facto.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResetService {

    private final ProcessingJobRepository jobRepository;
    private final ResetOrchestrator orchestrator;

    /**
     * Intentionally NOT @Transactional — same reasoning as ScanService.startScan
     * and ProcessService.startProcess: the job row must be committed before the
     * @Async dispatch.
     */
    public ProcessingJob startReset() {
        var running = jobRepository.findByStatusOrderByStartedAtDesc(ProcessingStatus.RUNNING);
        boolean alreadyRunning = running.stream()
                .anyMatch(j -> j.getType() == ProcessingJobType.RESET);
        if (alreadyRunning) {
            throw new IllegalStateException("Un reset est déjà en cours.");
        }
        ProcessingJob job = new ProcessingJob(ProcessingJobType.RESET);
        job.setCurrentStep("En attente de démarrage");
        ProcessingJob saved = jobRepository.save(job);
        orchestrator.runReset(saved.getId());
        return saved;
    }
}
