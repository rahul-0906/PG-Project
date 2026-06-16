package com.pgcrm.entity.enums;

/**
 * Enumeration of electricity-bill splitting algorithms available to PG buildings.
 *
 * <p>The {@code EbSplitMethod} determines how the total shared electricity cost of a
 * {@link com.pgcrm.entity.Block} is divided among its resident guests when a manager
 * records an {@link com.pgcrm.entity.EbBill}. The active split method for a building
 * is configured in {@link com.pgcrm.entity.BuildingConfig#getEbSplitMethod()}, and
 * the choice at the time of bill recording is snapshotted in
 * {@link com.pgcrm.entity.EbBill#getSplitMethod()} for historical accuracy.</p>
 *
 * <p>The {@code EbService} uses this enum to select the appropriate calculation
 * strategy pattern when creating {@link com.pgcrm.entity.EbBillGuest} share records.</p>
 */
public enum EbSplitMethod {

    /**
     * The total bill is divided equally among all active guests in the block.
     * <p>Formula: {@code shareAmount = totalAmount ÷ activeGuestCount}</p>
     * Simplest model — suitable for buildings where individual electricity
     * consumption is assumed to be roughly uniform across guests.
     */
    EQUAL_SPLIT,

    /**
     * A fixed per-bed electricity charge is applied to each guest, regardless
     * of actual consumption or the number of occupants in a room.
     * <p>Formula: {@code shareAmount = fixedRatePerBed}</p>
     * Suitable for buildings with a predictable, low-variance electricity profile.
     */
    PER_BED,

    /**
     * Each guest's share is computed from individual sub-meter readings captured at
     * the start and end of the billing period.
     * <p>Formula: {@code unitsConsumed = currentReading - previousReading;
     * shareAmount = unitsConsumed × ratePerUnit}</p>
     * Most accurate model — requires sub-meter hardware installed per bed or room.
     * Meter readings are stored in {@link com.pgcrm.entity.EbBillGuest}.
     */
    METER_BASED,

    /**
     * The manager manually enters each guest's electricity share amount directly,
     * without any automated calculation.
     * Suitable for buildings with irregular billing arrangements or complex
     * historical corrections that cannot be expressed by the other methods.
     */
    MANAGER_MANUAL
}
