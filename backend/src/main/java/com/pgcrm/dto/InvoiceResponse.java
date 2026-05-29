package com.pgcrm.dto;

import com.pgcrm.entity.Invoice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {
    private String id;
    private String guestId;
    private String guestName;
    private int month;
    private int year;
    private BigDecimal totalAmount;
    private String status;
    private LocalDate dueDate;
    private LocalDateTime generatedAt;
    private LocalDateTime paidAt;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String paymentMethod;
    private List<InvoiceLineItemResponse> lineItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceLineItemResponse {
        private String id;
        private String type;
        private String description;
        private BigDecimal amount;
    }

    public static InvoiceResponse fromEntity(Invoice invoice) {
        if (invoice == null) return null;
        List<InvoiceLineItemResponse> lines = invoice.getLineItems() == null ? java.util.Collections.emptyList() :
                invoice.getLineItems().stream()
                        .map(li -> InvoiceLineItemResponse.builder()
                                .id(li.getId())
                                .type(li.getType() != null ? li.getType().name() : null)
                                .description(li.getDescription())
                                .amount(li.getAmount())
                                .build())
                        .collect(java.util.stream.Collectors.toList());

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .guestId(invoice.getGuest() != null ? invoice.getGuest().getId() : null)
                .guestName(invoice.getGuest() != null ? invoice.getGuest().getFullName() : null)
                .month(invoice.getMonth())
                .year(invoice.getYear())
                .totalAmount(invoice.getTotalAmount())
                .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                .dueDate(invoice.getDueDate())
                .generatedAt(invoice.getGeneratedAt())
                .paidAt(invoice.getPaidAt())
                .razorpayOrderId(invoice.getRazorpayOrderId())
                .razorpayPaymentId(invoice.getRazorpayPaymentId())
                .paymentMethod(invoice.getPaymentMethod())
                .lineItems(lines)
                .build();
    }
}
