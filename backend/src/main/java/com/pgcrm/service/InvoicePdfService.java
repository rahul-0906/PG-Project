package com.pgcrm.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.InvoiceLineItem;
import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.repository.InvoiceRepository;
import com.pgcrm.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final SystemConfigProperties systemConfig;

    private static final Color ACCENT = new Color(99, 102, 241);   // Indigo
    private static final Color DARK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color SUCCESS = new Color(16, 185, 129);
    private static final Color DANGER = new Color(239, 68, 68);
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateInvoicePdf(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, ACCENT);
            Font metaFontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DARK);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, DARK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

            // ── Header Table ──────────────────────────────────────────
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 1});

            // Left
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("🏠 " + systemConfig.getBranding().getName(), titleFont));
            leftCell.addElement(new Paragraph("TAX INVOICE", regularFont));
            headerTable.addCell(leftCell);

            // Right
            String status = invoice.getStatus() != null ? invoice.getStatus().name() : "—";
            Color statusColor = "PAID".equals(status) ? SUCCESS : DANGER;
            Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, statusColor);

            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            
            Paragraph pId = new Paragraph("Invoice # " + invoiceId.substring(0, 8).toUpperCase(), metaFontBold);
            pId.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pId);
            
            Paragraph pStatus = new Paragraph("Status: " + status, statusFont);
            pStatus.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pStatus);
            
            Paragraph pPeriod = new Paragraph("Period: " + invoice.getMonth() + "/" + invoice.getYear(), regularFont);
            pPeriod.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pPeriod);
            
            headerTable.addCell(rightCell);
            document.add(headerTable);

            document.add(new Paragraph(" "));

            // ── Divider ───────────────────────────────────────────────
            Paragraph divider = new Paragraph("────────────────────────────────────────────────────────────────────────────", regularFont);
            divider.setAlignment(Element.ALIGN_CENTER);
            document.add(divider);

            // ── Guest Details ─────────────────────────────────────────
            document.add(new Paragraph("Bill To", regularFont));
            if (invoice.getGuest() != null) {
                document.add(new Paragraph(invoice.getGuest().getFullName(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, DARK)));
                document.add(new Paragraph("Bed: " + (invoice.getGuest().getBed() != null ? invoice.getGuest().getBed().getBedLabel() : "—"), regularFont));
                document.add(new Paragraph(invoice.getGuest().getEmail(), regularFont));
                if (invoice.getGuest().getPhone() != null) {
                    document.add(new Paragraph("Phone: " + invoice.getGuest().getPhone(), regularFont));
                }
            }
            if (invoice.getDueDate() != null) {
                document.add(new Paragraph("Due Date: " + invoice.getDueDate().format(DF), regularFont));
            }

            document.add(new Paragraph(" "));

            // ── Line Items Table ──────────────────────────────────────
            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{3, 1, 1, 1});

            // Headers
            for (String h : new String[]{"Description", "Qty", "Rate (Rs)", "Amount (Rs)"}) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headerFont));
                cell.setBackgroundColor(ACCENT);
                cell.setPadding(6);
                cell.setBorder(Rectangle.BOX);
                itemsTable.addCell(cell);
            }

            if (invoice.getLineItems() != null) {
                for (InvoiceLineItem item : invoice.getLineItems()) {
                    String desc = formatLineType(item.getType() != null ? item.getType().name() : "");
                    
                    itemsTable.addCell(cell(desc, regularFont, DARK, 5));
                    itemsTable.addCell(cell("1", regularFont, MUTED, 5));
                    itemsTable.addCell(cell(fmt(item.getAmount()), regularFont, MUTED, 5));
                    itemsTable.addCell(cell(fmt(item.getAmount()), boldFont, DARK, 5));
                }
            }

            // Total row
            PdfPCell totalLabelCell = new PdfPCell(new Paragraph("TOTAL", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, DARK)));
            totalLabelCell.setColspan(3);
            totalLabelCell.setBorder(Rectangle.NO_BORDER);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPaddingTop(8);
            itemsTable.addCell(totalLabelCell);

            PdfPCell totalValCell = new PdfPCell(new Paragraph("Rs " + fmt(invoice.getTotalAmount()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ACCENT)));
            totalValCell.setBorder(Rectangle.NO_BORDER);
            totalValCell.setPaddingTop(8);
            itemsTable.addCell(totalValCell);

            document.add(itemsTable);

            // ── Payment Info ──────────────────────────────────────────
            if ("PAID".equals(status) && invoice.getPaidAt() != null) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Paid on " + invoice.getPaidAt().format(
                        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, SUCCESS)));
                if (invoice.getRazorpayPaymentId() != null) {
                    document.add(new Paragraph("Payment ID: " + invoice.getRazorpayPaymentId(), FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED)));
                }
            }

            // ── Footer ────────────────────────────────────────────────
            document.add(new Paragraph(" "));
            Paragraph f1 = new Paragraph("Thank you for being a valued guest. Please retain this invoice for your records.", regularFont);
            f1.setAlignment(Element.ALIGN_CENTER);
            document.add(f1);

            Paragraph f2 = new Paragraph("Generated by " + systemConfig.getBranding().getName() + " — " + java.time.LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")), FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED));
            f2.setAlignment(Element.ALIGN_CENTER);
            document.add(f2);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed for invoice {}: {}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    private PdfPCell cell(String text, Font font, Color color, float padding) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(font.getFamilyname(), font.getSize(), font.getStyle(), color));
        PdfPCell cell = new PdfPCell(p);
        cell.setPadding(padding);
        return cell;
    }

    private String fmt(java.math.BigDecimal v) {
        return v != null ? String.format("%,.2f", v) : "0.00";
    }

    private String formatLineType(String type) {
        return switch (type) {
            case "RENT" -> "Room Rent";
            case "EB" -> "Electricity (EB)";
            case "FOOD" -> "Food & Meals";
            case "LAUNDRY" -> "Laundry / Washing Machine";
            default -> type;
        };
    }
}
