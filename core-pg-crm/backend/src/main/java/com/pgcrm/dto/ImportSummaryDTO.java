package com.pgcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing the summary result of a bulk import operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryDTO {

    /** Total number of guests successfully imported and checked in. */
    private int totalImported;

    /** Total number of new rooms created in the process. */
    private int roomsCreated;
}
