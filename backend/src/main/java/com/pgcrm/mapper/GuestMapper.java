package com.pgcrm.mapper;

import com.pgcrm.dto.GuestResponse;
import com.pgcrm.dto.GuestCheckInRequest;
import com.pgcrm.entity.Guest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface GuestMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "bedId", source = "bed.id")
    @Mapping(target = "bedLabel", source = "bed.bedLabel")
    @Mapping(target = "roomNumber", source = "bed.room.roomNumber")
    @Mapping(target = "floorName", source = "bed.room.floor.floorLabel")
    @Mapping(target = "kycStatus", expression = "java(guest.getKycStatus() != null ? guest.getKycStatus().name() : null)")
    GuestResponse toResponse(Guest guest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "bed", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Guest toEntity(GuestCheckInRequest request);
}
