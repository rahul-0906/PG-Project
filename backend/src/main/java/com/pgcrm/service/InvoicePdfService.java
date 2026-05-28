package com.pgcrm.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.InvoiceLineItem;
import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final SystemConfigProperties systemConfig;

    private static final DeviceRgb ACCENT   = new DeviceRgb(99, 102, 241);   // Indigo
    private static final DeviceRgb DARK     = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb MUTED    = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb SUCCESS  = new DeviceRgb(16, 185, 129);
    private static final DeviceRgb DANGER   = new DeviceRgb(239, 68, 68);
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateInvoicePdf(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf  = new PdfDocument(writer);
            Document doc     = new Document(pdf);
            doc.setMargins(36, 48, 36, 48);

            PdfFont bold    = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

            // ── Header ───────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();

            // Left — PG name + invoice title
            Cell left = new Cell()
                .add(new Paragraph("🏠 " + systemConfig.getBranding().getName()).setFont(bold).setFontSize(20).setFontColor(ACCENT))
                .add(new Paragraph("TAX INVOICE").setFont(regular).setFontSize(9).setFontColor(MUTED))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

            // Right — invoice meta
            String status = invoice.getStatus() != null ? invoice.getStatus().name() : "—";
            DeviceRgb statusColor = "PAID".equals(status) ? SUCCESS : DANGER;
            Cell right = new Cell()
                .add(new Paragraph("Invoice # " + invoiceId.substring(0, 8).toUpperCase())
                    .setFont(bold).setFontSize(10).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Status: " + status)
                    .setFont(bold).setFontSize(10).setFontColor(statusColor).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Period: " + invoice.getMonth() + "/" + invoice.getYear())
                    .setFont(regular).setFontSize(9).setFontColor(MUTED).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

            header.addCell(left).addCell(right);
            doc.add(header);
            // Replace AreaBreak with some spacing since AreaBreak(Rectangle) is invalid in this context
            doc.add(new Paragraph(" ").setMarginBottom(12));

            // ── Divider ───────────────────────────────────────────────
            doc.add(new Paragraph("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setFont(regular).setFontSize(6).setFontColor(MUTED));

            // ── Guest Details ─────────────────────────────────────────
            doc.add(new Paragraph("Bill To").setFont(bold).setFontSize(10).setFontColor(MUTED).setMarginTop(8));
            if (invoice.getGuest() != null) {
                doc.add(new Paragraph(invoice.getGuest().getFullName())
                    .setFont(bold).setFontSize(13).setFontColor(DARK).setMarginBottom(2));
                doc.add(new Paragraph("Bed: " + (invoice.getGuest().getBed() != null ?
                    invoice.getGuest().getBed().getBedLabel() : "—"))
                    .setFont(regular).setFontSize(9).setFontColor(MUTED));
                doc.add(new Paragraph(invoice.getGuest().getEmail())
                    .setFont(regular).setFontSize(9).setFontColor(MUTED));
                if (invoice.getGuest().getPhone() != null) {
                    doc.add(new Paragraph("Phone: " + invoice.getGuest().getPhone())
                        .setFont(regular).setFontSize(9).setFontColor(MUTED));
                }
            }
            if (invoice.getDueDate() != null) {
                doc.add(new Paragraph("Due Date: " + invoice.getDueDate().format(DF))
                    .setFont(regular).setFontSize(9).setFontColor(MUTED));
            }

            // ── Line Items Table ──────────────────────────────────────
            doc.add(new Paragraph(" "));
            Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1})).useAllAvailableWidth();

            // Column headers
            for (String h : new String[]{"Description", "Qty", "Rate (₹)", "Amount (₹)"}) {
                table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(ACCENT)
                    .setPadding(6));
            }

            if (invoice.getLineItems() != null) {
                for (InvoiceLineItem item : invoice.getLineItems()) {
                    String desc = formatLineType(item.getType() != null ? item.getType().name() : "");
                    table.addCell(cell(desc, regular, DARK));
                    table.addCell(cell("1", regular, MUTED));
                    table.addCell(cell(fmt(item.getAmount()), regular, MUTED));
                    table.addCell(cell(fmt(item.getAmount()), bold, DARK));
                }
            }

            // Total row
            table.addCell(new Cell(1, 3)
                .add(new Paragraph("TOTAL").setFont(bold).setFontSize(11).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingTop(8));
            table.addCell(new Cell()
                .add(new Paragraph("₹" + fmt(invoice.getTotalAmount()))
                    .setFont(bold).setFontSize(14).setFontColor(ACCENT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPaddingTop(8));

            doc.add(table);

            // ── Payment Info ──────────────────────────────────────────
            if ("PAID".equals(status) && invoice.getPaidAt() != null) {
                doc.add(new Paragraph(" "));
                doc.add(new Paragraph("✅ Paid on " + invoice.getPaidAt().format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                    .setFont(bold).setFontSize(10).setFontColor(SUCCESS));
                if (invoice.getRazorpayPaymentId() != null) {
                    doc.add(new Paragraph("Payment ID: " + invoice.getRazorpayPaymentId())
                        .setFont(regular).setFontSize(8).setFontColor(MUTED));
                }
            }

            // ── Footer ────────────────────────────────────────────────
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Thank you for being a valued guest. Please retain this invoice for your records.")
                .setFont(regular).setFontSize(8).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Generated by " + systemConfig.getBranding().getName() + " — " + java.time.LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")))
                .setFont(regular).setFontSize(7).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed for invoice {}: {}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    private Cell cell(String text, PdfFont font, DeviceRgb color) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(color)).setPadding(5);
    }

    private String fmt(java.math.BigDecimal v) {
        return v != null ? String.format("%,.2f", v) : "0.00";
    }

    private String formatLineType(String type) {
        return switch (type) {
            case "RENT"    -> "Room Rent";
            case "EB"      -> "Electricity (EB)";
            case "FOOD"    -> "Food & Meals";
            case "LAUNDRY" -> "Laundry / Washing Machine";
            default        -> type;
        };
    }
}
