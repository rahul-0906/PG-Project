package com.pgcrm.repository;

import com.pgcrm.entity.EbBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EbBillRepository extends JpaRepository<EbBill, String> {
    List<EbBill> findByBlockId(String blockId);
    Optional<EbBill> findByBlockIdAndBillingPeriodStartAndBillingPeriodEnd(
            String blockId, LocalDate start, LocalDate end);
}
