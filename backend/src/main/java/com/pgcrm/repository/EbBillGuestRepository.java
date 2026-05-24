package com.pgcrm.repository;

import com.pgcrm.entity.EbBillGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EbBillGuestRepository extends JpaRepository<EbBillGuest, String> {
    List<EbBillGuest> findByGuestId(String guestId);
    Optional<EbBillGuest> findByEbBillIdAndGuestId(String ebBillId, String guestId);

    /** Get all guest shares for EB bills belonging to a guest in a date range — used by billing cron */
    List<EbBillGuest> findByEbBill_BlockIdAndGuestId(String blockId, String guestId);
}
