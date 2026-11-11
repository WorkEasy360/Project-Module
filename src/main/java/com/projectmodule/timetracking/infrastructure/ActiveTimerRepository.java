package com.projectmodule.timetracking.infrastructure;

import com.projectmodule.timetracking.domain.ActiveTimer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActiveTimerRepository extends JpaRepository<ActiveTimer, UUID> {
}
