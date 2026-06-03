package com.pgcrm.entity.enums;

/**
 * Enumeration of KYC (Know Your Customer) identity verification states for a
 * {@link com.pgcrm.entity.Guest}.
 *
 * <p>KYC verification is a regulatory compliance requirement for PG accommodations
 * in India. Each guest must submit identity documents (e.g., Aadhaar card, PAN card)
 * after check-in. The manager reviews the submission and updates the guest's
 * {@link com.pgcrm.entity.Guest#getKycStatus()} accordingly.</p>
 *
 * <p>The KYC status is displayed as a badge in the manager's guest-list dashboard
 * and in the guest's own profile view in the portal.</p>
 */
public enum KycStatus {

    /** Identity documents have not yet been submitted or reviewed. Default state at check-in. */
    PENDING,

    /** Identity documents have been reviewed and accepted by the manager. */
    VERIFIED,

    /**
     * Submitted documents were invalid, incomplete, or could not be verified.
     * The guest must resubmit corrected documents; the manager will then re-evaluate.
     */
    REJECTED
}
