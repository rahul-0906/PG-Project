package com.pgcrm.entity.enums;

public enum EbSplitMethod {
    EQUAL_SPLIT,     // Total bill ÷ active guest count
    PER_BED,         // Fixed rate per bed
    METER_BASED,     // Previous reading vs current reading per guest (sub-meter)
    MANAGER_MANUAL   // Manager enters individual amounts
}
