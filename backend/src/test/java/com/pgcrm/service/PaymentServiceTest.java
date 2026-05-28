package com.pgcrm.service;

import com.pgcrm.entity.Guest;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.enums.InvoiceStatus;
import com.pgcrm.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "globalKeyId", "rzp_test_placeholder");
        ReflectionTestUtils.setField(paymentService, "globalKeySecret", "placeholder_secret");
        ReflectionTestUtils.setField(paymentService, "razorpayEnabled", false);
    }

    @Test
    void testCreateOrder_MockFlow() {
        String invoiceId = "inv1";
        Guest guest = new Guest();
        guest.setId("g1");
        guest.setFullName("John Doe");
        guest.setEmail("john@example.com");

        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .status(InvoiceStatus.GENERATED)
                .totalAmount(BigDecimal.valueOf(1500.00))
                .guest(guest)
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        Map<String, Object> order = paymentService.createOrder(invoiceId);

        assertNotNull(order);
        assertEquals("order_mock_inv1", order.get("orderId"));
        assertEquals(150000, order.get("amount")); // 1500 * 100
        assertEquals("INR", order.get("currency"));
        assertTrue((Boolean) order.get("mock"));
    }

    @Test
    void testVerifyAndCapture_MockFlow() {
        String invoiceId = "inv1";
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .status(InvoiceStatus.GENERATED)
                .totalAmount(BigDecimal.valueOf(1500.00))
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = paymentService.verifyAndCapture(invoiceId, "order1", "pay1", "sig1");

        assertNotNull(result);
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        assertEquals("order1", result.getRazorpayOrderId());
        assertEquals("pay1", result.getRazorpayPaymentId());
        assertNotNull(result.getPaidAt());
    }

    @Test
    void testVerifyAndCapture_WithRealSignatureVerification_Success() throws Exception {
        ReflectionTestUtils.setField(paymentService, "globalKeySecret", "my_secret_key");
        ReflectionTestUtils.setField(paymentService, "razorpayEnabled", true);

        String invoiceId = "inv1";
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .status(InvoiceStatus.GENERATED)
                .totalAmount(BigDecimal.valueOf(1500.00))
                .build();

        String orderId = "order_abc123";
        String paymentId = "pay_xyz789";

        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("my_secret_key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = paymentService.verifyAndCapture(invoiceId, orderId, paymentId, signature);

        assertNotNull(result);
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        assertEquals(paymentId, result.getRazorpayPaymentId());
    }

    @Test
    void testVerifyAndCapture_WithRealSignatureVerification_Failure() {
        ReflectionTestUtils.setField(paymentService, "globalKeySecret", "my_secret_key");
        ReflectionTestUtils.setField(paymentService, "razorpayEnabled", true);

        String invoiceId = "inv1";
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .status(InvoiceStatus.GENERATED)
                .totalAmount(BigDecimal.valueOf(1500.00))
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            paymentService.verifyAndCapture(invoiceId, "order1", "pay1", "invalid_signature");
        });

        assertTrue(exception.getMessage().contains("Signature verification"));
    }

    @Test
    void testRecordManualPayment() {
        String invoiceId = "inv1";
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .status(InvoiceStatus.GENERATED)
                .totalAmount(BigDecimal.valueOf(1200.00))
                .build();

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = paymentService.recordManualPayment(invoiceId, BigDecimal.valueOf(1200.00), "UPI");

        assertNotNull(result);
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        assertEquals("UPI", result.getPaymentMethod());
        assertNotNull(result.getPaidAt());
    }
}
