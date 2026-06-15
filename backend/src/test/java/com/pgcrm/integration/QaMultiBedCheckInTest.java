package com.pgcrm.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgcrm.dto.GuestCheckInRequest;
import com.pgcrm.entity.Bed;
import com.pgcrm.entity.Building;
import com.pgcrm.entity.Room;
import com.pgcrm.entity.User;
import com.pgcrm.entity.Guest;
import com.pgcrm.entity.enums.BedStatus;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.BedRepository;
import com.pgcrm.repository.BuildingRepository;
import com.pgcrm.repository.GuestRepository;
import com.pgcrm.repository.RoomRepository;
import com.pgcrm.repository.UserRepository;
import com.pgcrm.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.BodyPart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class QaMultiBedCheckInTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @SpyBean
    private EmailService emailService;

    @Test
    public void testQaMultiBedCheckInFlow() throws Exception {
        String newManagerEmail = "qa.manager@pgcrm.com";
        String guestEmail = "qa.guest@pgcrm.com";

        // Mock mail sender to return a real MimeMessage so sendHtmlMail succeeds and allows verification
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Perform initial cleanup to ensure clean start
        cleanupTestData(newManagerEmail, guestEmail);

        try {
            // 1. Assert Spring context loaded & seed verification
            Optional<User> superAdminOpt = userRepository.findByEmailIgnoreCase("owner@pgcrm.com");
            assertTrue(superAdminOpt.isPresent(), "Super Admin (owner@pgcrm.com) should have been seeded by DatabaseSeeder");
            assertEquals(Role.PG_OWNER, superAdminOpt.get().getRole());

            Optional<Building> buildingOpt = buildingRepository.findByNameIgnoreCase("Main Building");
            assertTrue(buildingOpt.isPresent(), "Main Building layout should have been seeded by DataSeeder");
            Building building = buildingOpt.get();

            List<Bed> vacantBeds = bedRepository.findVacant();
            assertFalse(vacantBeds.isEmpty(), "There should be vacant beds seeded in the layout");

            // 2. Authenticate as Super Admin
            String loginJson = objectMapper.writeValueAsString(Map.of(
                    "email", "owner@pgcrm.com",
                    "password", "Admin@123"
            ));

            String adminLoginResponse = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode adminNode = objectMapper.readTree(adminLoginResponse);
            String adminToken = adminNode.get("accessToken").asText();
            assertNotNull(adminToken);

            // 3. Create a new Tier 2 Manager (PG_MANAGER) using Super Admin JWT
            Map<String, String> managerRequest = new HashMap<>();
            managerRequest.put("email", newManagerEmail);
            managerRequest.put("fullName", "QA Automation Manager");
            managerRequest.put("branchId", building.getId());

            String managerCreationResponse = mockMvc.perform(post("/api/owner/managers")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(managerRequest)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode managerNode = objectMapper.readTree(managerCreationResponse);
            String managerId = managerNode.get("id").asText();
            assertNotNull(managerId);

            Optional<User> managerOpt = userRepository.findById(managerId);
            assertTrue(managerOpt.isPresent());
            assertEquals(Role.PG_MANAGER, managerOpt.get().getRole());
            assertEquals(building.getId(), managerOpt.get().getBranchId());

            // 4. Authenticate as the newly created Tier 2 Manager
            String managerLoginJson = objectMapper.writeValueAsString(Map.of(
                    "email", newManagerEmail,
                    "password", "Manager@123"
            ));

            String managerLoginResponse = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(managerLoginJson))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode managerLoginNode = objectMapper.readTree(managerLoginResponse);
            String managerToken = managerLoginNode.get("accessToken").asText();
            assertNotNull(managerToken);

            // 5. Query building layout to find a room with multiple vacant beds
            List<Room> rooms = roomRepository.findAll().stream()
                    .filter(r -> r.getSharingType() > 1)
                    .collect(Collectors.toList());

            Room targetRoom = null;
            List<Bed> targetBeds = null;
            for (Room r : rooms) {
                List<Bed> bedsInRoom = bedRepository.findByRoomId(r.getId());
                boolean allVacant = bedsInRoom.stream().allMatch(b -> b.getStatus() == BedStatus.VACANT);
                if (allVacant && bedsInRoom.size() > 1) {
                    targetRoom = r;
                    targetBeds = bedsInRoom;
                    break;
                }
            }

            assertNotNull(targetRoom, "Should find a room with multiple vacant beds for check-in");
            assertNotNull(targetBeds);
            assertTrue(targetBeds.size() > 1);

            // 6. Check in a guest in Multi-Bed Mode (Whole Room Booking) using Manager JWT
            List<String> targetBedIds = targetBeds.stream().map(Bed::getId).collect(Collectors.toList());

            GuestCheckInRequest checkInRequest = GuestCheckInRequest.builder()
                    .bedIds(targetBedIds)
                    .fullName("QA Multi Bed Guest")
                    .email(guestEmail)
                    .phone("9999999999")
                    .whatsappNumber("9999999999")
                    .advanceDeposit(BigDecimal.valueOf(5000))
                    .checkInDate(LocalDate.now())
                    .isBookEntireRoom(true)
                    .isVeg(true)
                    .breakfastOpted(true)
                    .lunchOpted(true)
                    .dinnerOpted(true)
                    .build();

            mockMvc.perform(post("/api/manager/guests")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(checkInRequest)))
                    .andExpect(status().isOk());

            // 7. Verify all beds in the room are now OCCUPIED
            for (String bedId : targetBedIds) {
                Optional<Bed> bedOpt = bedRepository.findById(bedId);
                assertTrue(bedOpt.isPresent());
                assertEquals(BedStatus.OCCUPIED, bedOpt.get().getStatus(), "All beds in the room must be updated to OCCUPIED");
            }

            // 8. Verify welcome email was processed correctly and contains the assignedBeds
            ArgumentCaptor<Guest> guestCaptor = ArgumentCaptor.forClass(Guest.class);
            Mockito.verify(emailService, Mockito.times(1)).sendGuestWelcomeEmail(guestCaptor.capture(), Mockito.anyString());
            Guest sentGuest = guestCaptor.getValue();
            assertNotNull(sentGuest);
            assertNotNull(sentGuest.getBeds());
            assertEquals(targetBeds.size(), sentGuest.getBeds().size());

            // Check that the bed labels match
            List<String> actualBedLabels = sentGuest.getBeds().stream()
                    .map(Bed::getBedLabel)
                    .collect(Collectors.toList());
            for (Bed bed : targetBeds) {
                assertTrue(actualBedLabels.contains(bed.getBedLabel()), 
                        "Email guest should have bed label: " + bed.getBedLabel());
            }

            // Also verify JavaMailSender was called to send the email
            Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(MimeMessage.class));

        } finally {
            // Clean up database records and reset bed status
            cleanupTestData(newManagerEmail, guestEmail);
        }
    }

    private void cleanupTestData(String managerEmail, String guestEmail) {
        // Revert bed statuses for any beds assigned to the guest
        List<String> bedIds = jdbcTemplate.queryForList(
                "SELECT bed_id FROM guest_beds gb JOIN guests g ON gb.guest_id = g.id WHERE g.email = ?",
                String.class,
                guestEmail
        );
        for (String bedId : bedIds) {
            bedRepository.findById(bedId).ifPresent(bed -> {
                bed.setStatus(BedStatus.VACANT);
                bedRepository.saveAndFlush(bed);
            });
        }

        // Physically delete guest relationship and guest record to avoid constraint violation on user deletion
        Optional<User> guestUserOpt = userRepository.findByEmailIgnoreCase(guestEmail);
        if (guestUserOpt.isPresent()) {
            String userId = guestUserOpt.get().getId();
            
            // Query guest IDs via SQL to bypass Hibernate soft-delete filters
            List<String> guestIds = jdbcTemplate.queryForList(
                    "SELECT id FROM guests WHERE user_id = ?",
                    String.class,
                    userId
            );
            
            for (String gId : guestIds) {
                jdbcTemplate.update("DELETE FROM guest_beds WHERE guest_id = ?", gId);
                jdbcTemplate.update("DELETE FROM invoices WHERE guest_id = ?", gId);
                jdbcTemplate.update("DELETE FROM eb_bill_guests WHERE guest_id = ?", gId);
                jdbcTemplate.update("DELETE FROM daily_logs WHERE guest_id = ?", gId);
                jdbcTemplate.update("DELETE FROM maintenance_tickets WHERE raised_by_guest_id = ?", gId);
                jdbcTemplate.update("DELETE FROM notifications WHERE guest_id = ?", gId);
                jdbcTemplate.update("DELETE FROM guests WHERE id = ?", gId);
            }
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }

        // Physically delete manager user record
        Optional<User> managerUserOpt = userRepository.findByEmailIgnoreCase(managerEmail);
        if (managerUserOpt.isPresent()) {
            String managerUserId = managerUserOpt.get().getId();
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", managerUserId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", managerUserId);
        }
    }

    private String getTextFromMimeMessage(MimeMessage message) throws Exception {
        return getTextFromPart(message);
    }

    private String getTextFromPart(Part part) throws Exception {
        if (part.isMimeType("text/*")) {
            return part.getContent().toString();
        }
        if (part.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                result.append(getTextFromPart(multipart.getBodyPart(i)));
            }
            return result.toString();
        }
        return "";
    }
}

