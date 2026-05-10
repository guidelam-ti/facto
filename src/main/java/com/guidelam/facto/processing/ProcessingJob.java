package com.guidelam.facto.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "processing_job", indexes = {
        @Index(name = "idx_processing_job_status", columnList = "status"),
        @Index(name = "idx_processing_job_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ProcessingJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_items", nullable = false)
    private int totalItems = 0;

    @Column(name = "processed_items", nullable = false)
    private int processedItems = 0;

    @Column(name = "archived_items", nullable = false)
    @ColumnDefault("0")
    private int archivedItems = 0;

    @Column(name = "error_count", nullable = false)
    private int errorCount = 0;

    @Column(name = "current_step", length = 500)
    private String currentStep;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    public ProcessingJob(ProcessingJobType type) {
        this.type = type;
    }
}
