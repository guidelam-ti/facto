package com.guidelam.facto.processing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    List<ProcessingJob> findByStatusOrderByStartedAtDesc(ProcessingStatus status);

    List<ProcessingJob> findByTypeOrderByStartedAtDesc(ProcessingJobType type);

    Optional<ProcessingJob> findFirstByTypeOrderByStartedAtDesc(ProcessingJobType type);
}
