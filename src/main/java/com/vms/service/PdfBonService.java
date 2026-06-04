package com.vms.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Service de génération PDF des bons cadeaux VMS.
 * Un PDF par bon, contenant : en-tête, valeur, QR code, code bon, expiration.
 * Utilisé par ApprobationController après génération des vouchers.
 */
public class PdfBonService {

    // Couleurs VMS
    private static final DeviceRgb BLEU_VMS   = new DeviceRgb(30,  58, 138); // #1e3a8a
    private static final DeviceRgb BLEU_CLAIR = new DeviceRgb(219, 234, 254); // #dbeafe
    private static final DeviceRgb VERT_VMS   = new DeviceRgb(6,   95,  70);  // #065f46
    private static final DeviceRgb GRIS_TEXTE = new DeviceRgb(55,  65,  81);  // #374151
    private static final DeviceRgb GRIS_FOND  = new DeviceRgb(243, 244, 246); // #f3f4f6

    /**
     * Génère un PDF pour un bon cadeau.
     */
    public static byte[] genererBon(String code, String qrData, double valeur,
                                     String dateExpiration, String nomClient,
                                     String reference) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter   writer  = new PdfWriter(baos);
        PdfDocument pdfDoc  = new PdfDocument(writer);
        Document    document = new Document(pdfDoc, PageSize.A6);
        document.setMargins(20, 20, 20, 20);

        // ── En-tête bleu ─────────────────────────────────────
        Table header = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100));
        header.addCell(new Cell()
                .setBackgroundColor(BLEU_VMS)
                .setBorder(Border.NO_BORDER)
                .setPadding(12)
                .setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph("VoucherManager")
                        .setFontColor(ColorConstants.WHITE)
                        .setFontSize(16)
                        .setBold()
                        .setMarginBottom(2))
                .add(new Paragraph("BON CADEAU")
                        .setFontColor(BLEU_CLAIR)
                        .setFontSize(10)
                        .setMarginBottom(0)));
        document.add(header);

        // ── Valeur centrale ──────────────────────────────────
        Table valeurTable = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8);
        valeurTable.addCell(new Cell()
                .setBackgroundColor(BLEU_CLAIR)
                .setBorder(new SolidBorder(BLEU_VMS, 1))
                .setPadding(10)
                .setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph(String.format("Rs %,.0f", valeur))
                        .setFontColor(BLEU_VMS)
                        .setFontSize(28)
                        .setBold()
                        .setMarginBottom(0)));
        document.add(valeurTable);

        // ── QR Code + infos ──────────────────────────────────
        BarcodeQRCode  qrCode    = new BarcodeQRCode(qrData);
        PdfFormXObject qrXObject = qrCode.createFormXObject(ColorConstants.BLACK, pdfDoc);

        Table qrTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8);

        // Colonne gauche : QR code
        qrTable.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingRight(8)
                .add(new com.itextpdf.layout.element.Image(qrXObject)
                        .setWidth(80)
                        .setHeight(80)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER)));

        // Colonne droite : infos — SANS setFontFamily() pour éviter l'erreur FontProvider
        qrTable.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(8)
                .add(new Paragraph("CODE BON")
                        .setFontColor(GRIS_TEXTE)
                        .setFontSize(7)
                        .setBold()
                        .setMarginBottom(2))
                .add(new Paragraph(code)
                        .setFontColor(BLEU_VMS)
                        .setFontSize(9)
                        .setBold()
                        .setMarginBottom(8))
                .add(new Paragraph("CLIENT")
                        .setFontColor(GRIS_TEXTE)
                        .setFontSize(7)
                        .setBold()
                        .setMarginBottom(2))
                .add(new Paragraph(nomClient)
                        .setFontColor(GRIS_TEXTE)
                        .setFontSize(9)
                        .setMarginBottom(8))
                .add(new Paragraph("REFERENCE")
                        .setFontColor(GRIS_TEXTE)
                        .setFontSize(7)
                        .setBold()
                        .setMarginBottom(2))
                .add(new Paragraph(reference)
                        .setFontColor(GRIS_TEXTE)
                        .setFontSize(9)));
        document.add(qrTable);

        // ── Pied de page ─────────────────────────────────────
        Table footer = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginTop(8);
        footer.addCell(new Cell()
                .setBackgroundColor(GRIS_FOND)
                .setBorder(Border.NO_BORDER)
                .setPadding(8)
                .setTextAlignment(TextAlignment.CENTER)
                .add(new Paragraph("Valable jusqu'au " + dateExpiration)
                        .setFontColor(VERT_VMS)
                        .setFontSize(9)
                        .setBold()
                        .setMarginBottom(2))
                .add(new Paragraph("Usage unique · Non fractionnable · Non remboursable")
                        .setFontColor(GRIS_TEXTE)
                        .setFontSize(7)
                        .setMarginBottom(0)));
        document.add(footer);

        document.close();
        return baos.toByteArray();
    }

    /**
     * Génère tous les PDFs d'une demande. Retourne un byte[] par bon.
     */
    public static List<byte[]> genererTousLesBons(List<VoucherInfo> vouchers,
                                                    String nomClient,
                                                    String reference) {
        List<byte[]> pdfs = new java.util.ArrayList<>();
        for (VoucherInfo v : vouchers) {
            try {
                byte[] pdf = genererBon(v.code, v.qrData, v.valeur,
                        v.dateExpiration, nomClient, reference);
                pdfs.add(pdf);
                System.out.println("[PDF] Genere : " + v.code);
            } catch (Exception e) {
                System.err.println("[PDF] Erreur generation " + v.code + " : " + e.getMessage());
            }
        }
        return pdfs;
    }

    /**
     * Données d'un voucher pour la génération PDF.
     */
    public static class VoucherInfo {
        public final String code;
        public final String qrData;
        public final double valeur;
        public final String dateExpiration;

        public VoucherInfo(String code, String qrData, double valeur, String dateExpiration) {
            this.code           = code;
            this.qrData         = qrData;
            this.valeur         = valeur;
            this.dateExpiration = dateExpiration;
        }
    }
}