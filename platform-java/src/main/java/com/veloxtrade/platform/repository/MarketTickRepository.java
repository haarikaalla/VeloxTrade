package com.veloxtrade.platform.repository;

import com.veloxtrade.platform.domain.MarketTick;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketTickRepository extends JpaRepository<MarketTick, UUID> {

    List<MarketTick> findBySymbolOrderByObservedAtDesc(String symbol, Limit limit);
}
