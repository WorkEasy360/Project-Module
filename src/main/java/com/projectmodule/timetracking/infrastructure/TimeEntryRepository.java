package com.projectmodule.timetracking.infrastructure;

import com.projectmodule.timetracking.domain.TimeEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    List<TimeEntry> findByTaskIdOrderByWorkDateDescCreatedAtDesc(UUID taskId);

    List<TimeEntry> findByUserIdAndWorkDateBetweenOrderByWorkDateAscCreatedAtAsc(
            UUID userId, LocalDate from, LocalDate to);
}
