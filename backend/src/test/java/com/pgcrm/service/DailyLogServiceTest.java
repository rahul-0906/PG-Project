package com.pgcrm.service;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.DailyLog;
import com.pgcrm.entity.Guest;
import com.pgcrm.repository.DailyLogRepository;
import com.pgcrm.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DailyLogServiceTest {

    @Mock
    private DailyLogRepository dailyLogRepository;

    @Mock
    private GuestRepository guestRepository;

    @Mock
    private SystemConfigProperties systemConfig;

    @InjectMocks
    private DailyLogService dailyLogService;

    private SystemConfigProperties.Rules rules;

    @BeforeEach
    void setUp() {
        rules = new SystemConfigProperties.Rules();
        rules.setAllowMealCancellations(true);
        rules.setBreakfastEnabled(true);
        rules.setLunchEnabled(true);
        rules.setDinnerEnabled(true);
        rules.setBreakfastLockoutTime(LocalTime.of(22, 0));
        rules.setDinnerLockoutTime(LocalTime.of(14, 0));

        when(systemConfig.getRules()).thenReturn(rules);
    }

    @Test
    void testUpsertLog_Success() {
        String guestId = "g1";
        LocalDate today = LocalDate.now().plusDays(2); // Use a future date to avoid lockout
        Guest guest = new Guest();
        guest.setId(guestId);
        guest.setBreakfastPreference(true);
        guest.setLunchPreference(true);

        DailyLog incoming = DailyLog.builder()
                .breakfastOpted(true)
                .lunchOpted(true)
                .dinnerOpted(false)
                .isVeg(true)
                .build();

        when(guestRepository.findById(guestId)).thenReturn(Optional.of(guest));
        when(dailyLogRepository.findByGuestIdAndLogDate(guestId, today)).thenReturn(Optional.empty());
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyLog result = dailyLogService.upsertLog(guestId, today, incoming);

        assertNotNull(result);
        assertEquals(guest, result.getGuest());
        assertEquals(today, result.getLogDate());
        assertTrue(result.isBreakfastOpted());
        assertTrue(result.isLunchOpted());
        assertFalse(result.isDinnerOpted());
        assertTrue(result.isVeg());
    }

    @Test
    void testUpsertLog_DinnerLockout_ThrowsException() {
        String guestId = "g1";
        LocalDate logDate = LocalDate.now().minusDays(1);
        Guest guest = new Guest();
        guest.setId(guestId);

        DailyLog incoming = DailyLog.builder()
                .dinnerOpted(true)
                .build();

        when(guestRepository.findById(guestId)).thenReturn(Optional.of(guest));
        when(dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            dailyLogService.upsertLog(guestId, logDate, incoming);
        });

        assertTrue(exception.getMessage().contains("Dinner selection is locked"));
    }

    @Test
    void testUpsertLog_NoCancellationsAllowed() {
        rules.setAllowMealCancellations(false);
        String guestId = "g1";
        LocalDate logDate = LocalDate.now().plusDays(2); // Use a future date to avoid lockout
        Guest guest = new Guest();
        guest.setId(guestId);

        DailyLog existing = DailyLog.builder()
                .id("log1")
                .guest(guest)
                .logDate(logDate)
                .breakfastOpted(true)
                .lunchOpted(false)
                .dinnerOpted(true)
                .build();

        DailyLog incoming = DailyLog.builder()
                .breakfastOpted(false)
                .lunchOpted(true)
                .dinnerOpted(false)
                .build();

        when(guestRepository.findById(guestId)).thenReturn(Optional.of(guest));
        when(dailyLogRepository.findByGuestIdAndLogDate(guestId, logDate)).thenReturn(Optional.of(existing));
        when(dailyLogRepository.save(any(DailyLog.class))).thenAnswer(inv -> inv.getArgument(0));

        DailyLog result = dailyLogService.upsertLog(guestId, logDate, incoming);

        assertTrue(result.isBreakfastOpted());
        assertFalse(result.isLunchOpted());
        assertTrue(result.isDinnerOpted());
    }
}
