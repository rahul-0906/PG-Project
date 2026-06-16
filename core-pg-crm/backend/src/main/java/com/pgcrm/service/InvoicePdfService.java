package com.pgcrm.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.entity.Invoice;
import com.pgcrm.entity.InvoiceLineItem;
import com.pgcrm.exception.ResourceNotFoundException;
import com.pgcrm.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service responsible for generating styled PDF invoice documents using OpenPDF (iText fork).
 *
 * <p>Produces an A4-format invoice PDF with the following layout sections:</p>
 * <ul>
 *   <li><strong>Header:</strong> Two-column table — left: PG branding name and "TAX INVOICE" label;
 *       right: invoice number, status (colour-coded), and billing period.</li>
 *   <li><strong>Bill-To Section:</strong> Guest name, bed label, email, phone, and due date.</li>
 *   <li><strong>Line Items Table:</strong> 4-column table (Description, Qty, Rate, Amount)
 *       with an indigo {@link #ACCENT} header row and a bold TOTAL row.</li>
 *   <li><strong>Payment Info:</strong> Appended below the table if the invoice is PAID —
 *       includes payment timestamp and Razorpay payment ID.</li>
 *   <li><strong>Footer:</strong> Gratitude message and generation timestamp.</li>
 * </ul>
 *
 * <p><strong>Colour Palette:</strong></p>
 * <ul>
 *   <li>{@link #ACCENT} — Indigo ({@code #6366F1}) — used for headers and total amounts.</li>
 *   <li>{@link #DARK} — Slate-900 ({@code #0F172A}) — used for primary text.</li>
 *   <li>{@link #MUTED} — Slate-500 ({@code #64748B}) — used for secondary text.</li>
 *   <li>{@link #SUCCESS} — Emerald ({@code #10B981}) — used for PAID status indicators.</li>
 *   <li>{@link #DANGER} — Red ({@code #EF4444}) — used for non-PAID status indicators.</li>
 * </ul>
 *
 * @see InvoiceRepository
 * @see SystemConfigProperties
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final InvoiceRepository      invoiceRepository;
    private final SystemConfigProperties systemConfig;

    // ── Colour Constants ──────────────────────────────────────────────────────

    /** Indigo accent — used for table headers and the total amount label. */
    private static final Color ACCENT  = new Color(99,  102, 241);

    /** Slate-900 — used for primary text and section headings. */
    private static final Color DARK    = new Color(15,  23,  42);

    /** Slate-500 — used for secondary/muted descriptive text. */
    private static final Color MUTED   = new Color(100, 116, 139);

    /** Emerald green — used for PAID payment status badges. */
    private static final Color SUCCESS = new Color(16,  185, 129);

    /** Red — used for non-PAID (GENERATED / OVERDUE) payment status badges. */
    private static final Color DANGER  = new Color(239, 68,  68);

    /** Date formatter for human-readable date display (e.g., {@code "04 Jun 2025"}). */
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /**
     * Generates an A4 PDF invoice document for the given invoice ID and returns
     * the rendered PDF as a byte array.
     *
     * <p>The PDF is generated in-memory using a {@link ByteArrayOutputStream} and
     * does not touch the filesystem. The returned byte array is suitable for direct
     * streaming as a {@code Content-Type: application/pdf} HTTP response.</p>
     *
     * @param invoiceId the UUID of the {@link Invoice} to generate the PDF for.
     * @return the complete PDF document as a {@code byte[]}.
     * @throws ResourceNotFoundException if no invoice exists for the given {@code invoiceId}.
     * @throws RuntimeException          if OpenPDF fails to render the document.
     */
    public byte[] generateInvoicePdf(final String invoiceId) {
        final Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            final Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            final Font titleFont    = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, ACCENT);
            final Font metaFontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, DARK);
            final Font regularFont  = FontFactory.getFont(FontFactory.HELVETICA,      9,  MUTED);
            final Font boldFont     = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,  DARK);
            final Font headerFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9,  Color.WHITE);

            // ── Header Table ──────────────────────────────────────────────────
            final PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{1, 1});

            // Left cell: PG branding
            final PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("🏠 " + systemConfig.getBranding().getName(), titleFont));
            leftCell.addElement(new Paragraph("TAX INVOICE", regularFont));
            headerTable.addCell(leftCell);

            // Right cell: invoice number, status, and billing period
            final String status      = invoice.getStatus() != null ? invoice.getStatus().name() : "—";
            final Color  statusColor = "PAID".equals(status) ? SUCCESS : DANGER;
            final Font   statusFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, statusColor);

            final PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

            final Paragraph pId = new Paragraph("Invoice # " + invoiceId.substring(0, 8).toUpperCase(), metaFontBold);
            pId.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pId);

            final Paragraph pStatus = new Paragraph("Status: " + status, statusFont);
            pStatus.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pStatus);

            final Paragraph pPeriod = new Paragraph("Period: " + invoice.getMonth() + "/" + invoice.getYear(), regularFont);
            pPeriod.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(pPeriod);

            headerTable.addCell(rightCell);
            document.add(headerTable);
            document.add(new Paragraph(" "));

            // ── Divider ───────────────────────────────────────────────────────
            final Paragraph divider = new Paragraph(
                    "────────────────────────────────────────────────────────────────────────────",
                    regularFont);
            divider.setAlignment(Element.ALIGN_CENTER);
            document.add(divider);

            // ── Bill-To Section ───────────────────────────────────────────────
            document.add(new Paragraph("Bill To", regularFont));
            if (invoice.getGuest() != null) {
                document.add(new Paragraph(invoice.getGuest().getFullName(),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, DARK)));
                document.add(new Paragraph(
                        "Bed: " + (invoice.getGuest().getBed() != null
                                ? invoice.getGuest().getBed().getBedLabel() : "—"), regularFont));
                document.add(new Paragraph(invoice.getGuest().getEmail(), regularFont));
                if (invoice.getGuest().getPhone() != null) {
                    document.add(new Paragraph("Phone: " + invoice.getGuest().getPhone(), regularFont));
                }
            }
            if (invoice.getDueDate() != null) {
                document.add(new Paragraph("Due Date: " + invoice.getDueDate().format(DF), regularFont));
            }
            document.add(new Paragraph(" "));

            // ── Line Items Table ──────────────────────────────────────────────
            final PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{3, 1, 1, 1});

            // Table header row
            for (final String heading : new String[]{"Description", "Qty", "Rate (Rs)", "Amount (Rs)"}) {
                final PdfPCell hCell = new PdfPCell(new Paragraph(heading, headerFont));
                hCell.setBackgroundColor(ACCENT);
                hCell.setPadding(6);
                hCell.setBorder(Rectangle.BOX);
                itemsTable.addCell(hCell);
            }

            // Table data rows — one row per invoice line item
            if (invoice.getLineItems() != null) {
                for (final InvoiceLineItem item : invoice.getLineItems()) {
                    final String desc = formatLineType(item.getType() != null ? item.getType().name() : "");
                    itemsTable.addCell(cell(desc,              regularFont, DARK, 5));
                    itemsTable.addCell(cell("1",               regularFont, MUTED, 5));
                    itemsTable.addCell(cell(fmt(item.getAmount()), regularFont, MUTED, 5));
                    itemsTable.addCell(cell(fmt(item.getAmount()), boldFont,    DARK, 5));
                }
            }

            // Total row spanning first 3 columns
            final PdfPCell totalLabelCell = new PdfPCell(
                    new Paragraph("TOTAL", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, DARK)));
            totalLabelCell.setColspan(3);
            totalLabelCell.setBorder(Rectangle.NO_BORDER);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPaddingTop(8);
            itemsTable.addCell(totalLabelCell);

            final PdfPCell totalValCell = new PdfPCell(
                    new Paragraph("Rs " + fmt(invoice.getTotalAmount()),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ACCENT)));
            totalValCell.setBorder(Rectangle.NO_BORDER);
            totalValCell.setPaddingTop(8);
            itemsTable.addCell(totalValCell);

            document.add(itemsTable);

            // ── Payment Info (PAID invoices only) ─────────────────────────────
            if ("PAID".equals(status) && invoice.getPaidAt() != null) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph(
                        "Paid on " + invoice.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, SUCCESS)));
                if (invoice.getRazorpayPaymentId() != null) {
                    document.add(new Paragraph(
                            "Payment ID: " + invoice.getRazorpayPaymentId(),
                            FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED)));
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            document.add(new Paragraph(" "));
            final Paragraph footer1 = new Paragraph(
                    "Thank you for being a valued guest. Please retain this invoice for your records.",
                    regularFont);
            footer1.setAlignment(Element.ALIGN_CENTER);
            document.add(footer1);

            final Paragraph footer2 = new Paragraph(
                    "Generated by " + systemConfig.getBranding().getName() + " — "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")),
                    FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED));
            footer2.setAlignment(Element.ALIGN_CENTER);
            document.add(footer2);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed for invoice {}: {}", invoiceId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Creates a styled {@link PdfPCell} for the line items table.
     *
     * @param text    the cell text content.
     * @param font    the base font to use (family name, size, and style are preserved; colour is overridden).
     * @param color   the text colour to apply.
     * @param padding the cell padding in points.
     * @return a configured {@link PdfPCell}.
     */
    private PdfPCell cell(final String text, final Font font, final Color color, final float padding) {
        final Paragraph p    = new Paragraph(text,
                FontFactory.getFont(font.getFamilyname(), font.getSize(), font.getStyle(), color));
        final PdfPCell  cell = new PdfPCell(p);
        cell.setPadding(padding);
        return cell;
    }

    /**
     * Formats a {@link BigDecimal} amount as a comma-separated currency string with 2 decimal places.
     *
     * @param v the value to format; {@code null} is rendered as {@code "0.00"}.
     * @return a formatted string, e.g., {@code "1,250.00"}.
     */
    private String fmt(final BigDecimal v) {
        return v != null ? String.format("%,.2f", v) : "0.00";
    }

    /**
     * Maps an {@link com.pgcrm.entity.enums.InvoiceLineType} name string to a human-readable label
     * for display in the PDF line items table.
     *
     * @param type the {@code InvoiceLineType.name()} string (e.g., {@code "RENT"}).
     * @return a user-friendly label (e.g., {@code "Room Rent"});
     *         the raw {@code type} string is returned unchanged if unrecognised.
     */
    private String formatLineType(final String type) {
        return switch (type) {
            case "RENT"    -> "Room Rent";
            case "EB"      -> "Electricity (EB)";
            case "FOOD"    -> "Food & Meals";
            case "LAUNDRY" -> "Laundry / Washing Machine";
            default        -> type;
        };
    }
}
