package com.guidelam.facto.processing;

import com.guidelam.facto.gmail.GmailMessageScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScanService {

    private final ProcessingJobRepository jobRepository;
    private final GmailMessageScanner scanner;

    /**
     * Intentionally NOT @Transactional. We want the job row committed before the
     * @Async dispatch so the scanner thread can read it; otherwise the async
     * thread sees an empty Optional from findById and exits.
     */
    public ProcessingJob startScan() {
        var running = jobRepository.findByStatusOrderByStartedAtDesc(ProcessingStatus.RUNNING);
        boolean alreadyRunning = running.stream()
                .anyMatch(j -> j.getType() == ProcessingJobType.SCAN);
        if (alreadyRunning) {
            throw new IllegalStateException("Un scan est déjà en cours.");
        }
        ProcessingJob job = new ProcessingJob(ProcessingJobType.SCAN);
        job.setCurrentStep("En attente de démarrage");
        ProcessingJob saved = jobRepository.save(job);
        scanner.runScan(saved.getId());
        return saved;
    }
}
