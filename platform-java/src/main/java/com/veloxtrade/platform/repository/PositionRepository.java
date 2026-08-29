package com.veloxtrade.platform.repository;

import com.veloxtrade.platform.domain.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByAccountId(UUID accountId);

    Optional<Position> findByAccountIdAndSymbol(UUID accountId, String symbol);
}
