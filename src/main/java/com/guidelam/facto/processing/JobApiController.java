package com.guidelam.facto.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobApiController {

    private final ProcessingJobRepository repository;

    @GetMapping("/{id}/status")
    public ResponseEntity<JobStatusView> getStatus(@PathVariable Long id) {
        return repository.findById(id)
                .map(JobStatusView::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record JobStatusView(
            Long id,
            String type,
            String status,
            int totalItems,
            int processedItems,
            int archivedItems,
            int errorCount,
            int percentComplete,
            String currentStep,
            String errorMessage,
            String startedAtIso,
            String finishedAtIso
    ) {
        public static JobStatusView from(ProcessingJob j) {
            int percent;
            if (j.getStatus() == ProcessingStatus.SUCCESS) {
                percent = 100;
            } else if (j.getTotalItems() > 0) {
                int raw = (int) Math.round(100.0 * j.getProcessedItems() / j.getTotalItems());
                // Cap at 99% while running: 100% should only display on SUCCESS,
                // otherwise a refining-estimate scan that hits 100% mid-run looks
                // like it's stuck.
                percent = Math.max(0, Math.min(99, raw));
            } else {
                percent = 0;
            }
            return new JobStatusView(
                    j.getId(),
                    j.getType().name(),
                    j.getStatus().name(),
                    j.getTotalItems(),
                    j.getProcessedItems(),
                    j.getArchivedItems(),
                    j.getErrorCount(),
                    percent,
                    j.getCurrentStep(),
                    j.getErrorMessage(),
                    j.getStartedAt() != null ? j.getStartedAt().toString() : null,
                    j.getFinishedAt() != null ? j.getFinishedAt().toString() : null
            );
        }
    }
}
