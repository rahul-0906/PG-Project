package com.pgcrm.mapper;

import com.pgcrm.dto.InvoiceResponse;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.InvoiceLineItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface InvoiceMapper {

    @Mapping(target = "guestId", source = "guest.id")
    @Mapping(target = "guestName", source = "guest.fullName")
    @Mapping(target = "status", expression = "java(invoice.getStatus() != null ? invoice.getStatus().name() : null)")
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(target = "type", expression = "java(lineItem.getType() != null ? lineItem.getType().name() : null)")
    InvoiceResponse.InvoiceLineItemResponse toLineItemResponse(InvoiceLineItem lineItem);
}
