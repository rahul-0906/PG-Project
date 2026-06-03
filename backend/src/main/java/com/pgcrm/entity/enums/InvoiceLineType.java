package com.pgcrm.entity.enums;

/**
 * Enumeration of charge categories that can appear as line items on a guest invoice.
 *
 * <p>Each {@code InvoiceLineType} value corresponds to one logical billing category
 * within a guest's monthly {@link com.pgcrm.entity.Invoice}. The
 * {@code InvoiceService} creates one {@link com.pgcrm.entity.InvoiceLineItem} per
 * applicable type during invoice generation, based on the guest's activity data
 * for the billing period.</p>
 *
 * <p><strong>Billing Sources per Type:</strong></p>
 * <ul>
 *   <li>{@link #RENT} — Fixed per-bed monthly rent from {@link com.pgcrm.entity.Room#getBaseRent()}.</li>
 *   <li>{@link #EB} — Guest's electricity share from {@link com.pgcrm.entity.EbBillGuest#getShareAmount()}.</li>
 *   <li>{@link #FOOD} — Aggregated meal charges from {@link com.pgcrm.entity.DailyLog} records
 *       (breakfast, lunch, dinner, omelette, boiled egg).</li>
 *   <li>{@link #LAUNDRY} — Aggregated washing machine charges from {@link com.pgcrm.entity.DailyLog} records.</li>
 * </ul>
 */
public enum InvoiceLineType {

    /** Fixed monthly base rent for the guest's assigned bed. */
    RENT,

    /** Electricity bill share allocated to the guest for the billing period. */
    EB,

    /**
     * Aggregate food and add-on charges, including breakfast, lunch, dinner,
     * omelette, and boiled egg items consumed during the month.
     */
    FOOD,

    /** Aggregate washing machine usage charges for the billing period. */
    LAUNDRY
}
