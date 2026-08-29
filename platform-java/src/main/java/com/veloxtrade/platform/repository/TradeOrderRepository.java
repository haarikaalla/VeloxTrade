package com.veloxtrade.platform.repository;

import com.veloxtrade.platform.domain.TradeOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, UUID> {

    List<TradeOrder> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Limit limit);
}
