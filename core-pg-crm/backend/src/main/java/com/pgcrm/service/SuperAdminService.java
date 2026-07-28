package com.pgcrm.service;

import com.pgcrm.dto.FinancialOverrideRequest;
import com.pgcrm.dto.SuperAdminSummaryResponse;
import com.pgcrm.dto.UserRoleUpdateRequest;
import com.pgcrm.entity.AuditLog;
import com.pgcrm.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SuperAdminService {

    SuperAdminSummaryResponse getSummary();

    List<User> getAllUsers();

    User createPgOwner(com.pgcrm.dto.CreatePgOwnerRequest request);

    User updateUserRole(UserRoleUpdateRequest request, String adminActorId);

    AuditLog applyFinancialOverride(FinancialOverrideRequest request, String adminActorId);

    Page<AuditLog> getAuditLogs(String search, Pageable pageable);

    void anonymizeGuest(String guestId, String adminActorId);

    void deleteUser(String userId, String adminActorId);
}
