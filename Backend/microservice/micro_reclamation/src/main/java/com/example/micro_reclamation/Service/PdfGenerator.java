package com.example.micro_reclamation.Service;


import com.example.micro_reclamation.Entity.ReportData;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.*;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PdfGenerator {

    // ── Palette couleurs ───────────────────────────────────────────────────────
    private static final DeviceRgb PRIMARY      = new DeviceRgb(21,  101, 192); // Bleu institutionnel
    private static final DeviceRgb PRIMARY_DARK = new DeviceRgb(13,  71,  161);
    private static final DeviceRgb PRIMARY_LIGHT= new DeviceRgb(227, 242, 253);
    private static final DeviceRgb ACCENT       = new DeviceRgb(0,   150, 136); // Teal
    private static final DeviceRgb DANGER       = new DeviceRgb(198, 40,  40);
    private static final DeviceRgb SUCCESS      = new DeviceRgb(46,  125, 50);
    private static final DeviceRgb WARNING      = new DeviceRgb(230, 119, 0);
    private static final DeviceRgb GRAY_LIGHT   = new DeviceRgb(245, 245, 245);
    private static final DeviceRgb GRAY_MID     = new DeviceRgb(189, 189, 189);
    private static final DeviceRgb GRAY_DARK    = new DeviceRgb(97,  97,  97);
    private static final DeviceRgb TEXT_PRIMARY  = new DeviceRgb(33,  33,  33);
    private static final DeviceRgb TEXT_SECONDARY= new DeviceRgb(117, 117, 117);

    // ── Marges ────────────────────────────────────────────────────────────────
    private static final float MARGIN_LEFT   = 45f;
    private static final float MARGIN_RIGHT  = 45f;
    private static final float MARGIN_TOP    = 80f;
    private static final float MARGIN_BOTTOM = 70f;

    // ─────────────────────────────────────────────────────────────────────────
    public static byte[] generate(ReportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(out);
            PdfDocument pdf    = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);

            // Métadonnées du document
            PdfDocumentInfo info = pdf.getDocumentInfo();
            info.setTitle("Rapport Mensuel — " + moisLabel(data.getMois()) + " " + data.getAnnee());
            info.setAuthor("Système de Réclamations");
            info.setSubject("Rapport de performance mensuel");
            info.setKeywords("tickets, chantiers, QoS, rapport");
            info.setCreator("micro_reclamation v1.0");

            // Gestionnaire d'en-tête / pied de page
            pdf.addEventHandler(PdfDocumentEvent.END_PAGE,
                    new HeaderFooterHandler(data));

            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(MARGIN_TOP, MARGIN_RIGHT, MARGIN_BOTTOM, MARGIN_LEFT);

            PdfFont fontRegular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont fontBold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontItalic  = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

            // ── PAGE DE COUVERTURE ─────────────────────────────────────────
            buildCoverPage(doc, data, fontRegular, fontBold, fontItalic, pdf);

            // ── SOMMAIRE ──────────────────────────────────────────────────
            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            buildTableOfContents(doc, fontRegular, fontBold);

            // ── SECTION 1 — VUE D'ENSEMBLE ────────────────────────────────
            doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
            addSectionTitle(doc, "1", "Vue d'ensemble", fontBold);
            addIntroText(doc, data, fontRegular, fontItalic);
            addKpiCards(doc, data, fontRegular, fontBold);

            // ── SECTION 2 — TICKETS ───────────────────────────────────────
            addSectionTitle(doc, "2", "Analyse des Tickets", fontBold);
            addTicketsTable(doc, data, fontRegular, fontBold);
            addProgressBar(doc, "Taux de résolution",
                    safeRatio(data.getTicketsClos(), data.getTotalTickets()),
                    SUCCESS, fontRegular, fontBold);
            addSpacing(doc, 12);

            // ── SECTION 3 — CHANTIERS ─────────────────────────────────────
            addSectionTitle(doc, "3", "Suivi des Chantiers", fontBold);
            addChantiersTable(doc, data, fontRegular, fontBold);
            addProgressBar(doc, "Taux d'achèvement",
                    safeRatio(data.getChantiersTermines(), data.getTotalChantiers()),
                    ACCENT, fontRegular, fontBold);
            addSpacing(doc, 12);

            // ── SECTION 4 — PERFORMANCE ───────────────────────────────────
            addSectionTitle(doc, "4", "Indicateurs de Performance", fontBold);
            addPerformanceTable(doc, data, fontRegular, fontBold);

            // ── PIED DE DOCUMENT — SIGNATURE ──────────────────────────────
            addSignatureBlock(doc, fontRegular, fontBold, fontItalic);

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF : " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // PAGE DE COUVERTURE
    // =========================================================================
    private static void buildCoverPage(Document doc, ReportData data,
                                       PdfFont reg, PdfFont bold, PdfFont italic,
                                       PdfDocument pdf) {

        // Fond plein bleu sur toute la page de couverture
        PdfCanvas canvas = new PdfCanvas(pdf.addNewPage());
        PageSize ps = PageSize.A4;

        // Bandeau supérieur plein
        canvas.setFillColor(PRIMARY_DARK)
                .rectangle(0, ps.getHeight() - 200, ps.getWidth(), 200)
                .fill();

        // Bande décorative accent
        canvas.setFillColor(ACCENT)
                .rectangle(0, ps.getHeight() - 205, ps.getWidth(), 5)
                .fill();

        // Rectangle blanc central (carte)
        canvas.setFillColor(ColorConstants.WHITE)
                .roundRectangle(40, ps.getHeight() - 560, ps.getWidth() - 80, 320, 8)
                .fill();

        // Trait coloré gauche de la carte
        canvas.setFillColor(PRIMARY)
                .rectangle(40, ps.getHeight() - 560, 6, 320)
                .fill();

        // Bandeau inférieur
        canvas.setFillColor(GRAY_LIGHT)
                .rectangle(0, 0, ps.getWidth(), 80)
                .fill();
        canvas.setFillColor(PRIMARY)
                .rectangle(0, 80, ps.getWidth(), 3)
                .fill();

        // ── Texte sur la couverture (via Document, page 1 déjà créée) ──
        // On crée un document temporaire sur cette page

        // Titre institution (sur fond bleu)
        Paragraph institution = new Paragraph("SYSTÈME DE GESTION DES RÉCLAMATIONS")
                .setFont(bold).setFontSize(11)
                .setFontColor(new DeviceRgb(179, 212, 255))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(30);
        doc.add(institution);

        Paragraph sousTitre = new Paragraph("Rapport Officiel de Performance")
                .setFont(reg).setFontSize(9)
                .setFontColor(new DeviceRgb(144, 194, 231))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(60);
        doc.add(sousTitre);

        // Titre principal (dans la carte blanche)
        doc.add(new Paragraph("RAPPORT MENSUEL")
                .setFont(bold).setFontSize(26)
                .setFontColor(PRIMARY_DARK)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(20).setMarginBottom(4));

        doc.add(new Paragraph(moisLabel(data.getMois()).toUpperCase() + " " + data.getAnnee())
                .setFont(bold).setFontSize(36)
                .setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(16));

        // Séparateur
        Table sep = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(40))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        sep.addCell(new Cell().setHeight(3).setBackgroundColor(ACCENT)
                .setBorder(Border.NO_BORDER));
        doc.add(sep);
        addSpacing(doc, 12);

        doc.add(new Paragraph("Supervision · Tickets · Chantiers · QoS")
                .setFont(italic).setFontSize(10)
                .setFontColor(GRAY_DARK)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(30));

        // Zone méta-données (bas de page)
        doc.add(new Paragraph(
                "Généré le : " + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm")))
                .setFont(reg).setFontSize(8)
                .setFontColor(TEXT_SECONDARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setFixedPosition(0, 20, PageSize.A4.getWidth()));
    }

    // =========================================================================
    // SOMMAIRE
    // =========================================================================
    private static void buildTableOfContents(Document doc, PdfFont reg, PdfFont bold) {
        addChapterBanner(doc, "Sommaire", PRIMARY, bold);
        addSpacing(doc, 10);

        String[][] entries = {
                {"1", "Vue d'ensemble",              "3"},
                {"2", "Analyse des Tickets",          "3"},
                {"3", "Suivi des Chantiers",          "4"},
                {"4", "Indicateurs de Performance",   "4"},
        };

        for (String[] e : entries) {
            Table row = new Table(UnitValue.createPercentArray(new float[]{5, 85, 10}))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(2);

            row.addCell(tocCell(e[0] + ".", bold, PRIMARY, TextAlignment.LEFT));
            row.addCell(tocCell(e[1], reg,  TEXT_PRIMARY, TextAlignment.LEFT));
            row.addCell(tocCell(e[2], bold, PRIMARY, TextAlignment.RIGHT));
            doc.add(row);

            // Ligne pointillée
            Table dots = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(8);
            dots.addCell(new Cell().setBorderBottom(new SolidBorder(GRAY_MID, 0.5f))
                    .setBorder(Border.NO_BORDER).setPadding(0));
            doc.add(dots);
        }
        addSpacing(doc, 20);
    }

    // =========================================================================
    // SECTION TITLE
    // =========================================================================
    private static void addSectionTitle(Document doc, String num, String title, PdfFont bold) {
        addSpacing(doc, 20);
        Table t = new Table(UnitValue.createPercentArray(new float[]{8, 92}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(14);

        t.addCell(new Cell().add(new Paragraph(num)
                        .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE))
                .setBackgroundColor(PRIMARY).setPadding(6)
                .setBorder(Border.NO_BORDER).setBorderRadius(new BorderRadius(4, 0)));

        t.addCell(new Cell().add(new Paragraph(title)
                        .setFont(bold).setFontSize(14).setFontColor(PRIMARY_DARK)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE))
                .setBackgroundColor(PRIMARY_LIGHT).setPaddingLeft(12).setPaddingTop(6).setPaddingBottom(6)
                .setBorder(Border.NO_BORDER).setBorderRadius(new BorderRadius(4, 4)));

        doc.add(t);
    }

    // =========================================================================
    // INTRO
    // =========================================================================
    private static void addIntroText(Document doc, ReportData data, PdfFont reg, PdfFont italic) {
        double tauxRes = safeRatio(data.getTicketsClos(), data.getTotalTickets()) * 100;
        String appreciation = tauxRes >= 80 ? "satisfaisante" : tauxRes >= 50 ? "correcte" : "à améliorer";

        doc.add(new Paragraph(
                "Ce rapport présente la synthèse des activités opérationnelles pour le mois de "
                        + moisLabel(data.getMois()) + " " + data.getAnnee()
                        + ". L'activité globale enregistre " + data.getTotalTickets()
                        + " tickets et " + data.getTotalChantiers()
                        + " chantiers. La performance générale est jugée " + appreciation
                        + " avec un taux de résolution de " + String.format("%.1f", tauxRes) + " %.")
                .setFont(reg).setFontSize(10).setFontColor(TEXT_PRIMARY)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginBottom(16).setFirstLineIndent(20));
    }

    // =========================================================================
    // CARTES KPI (2×2)
    // =========================================================================
    private static void addKpiCards(Document doc, ReportData data, PdfFont reg, PdfFont bold) {
        Table grid = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(20);

        addKpiCard(grid, "Total Tickets",    String.valueOf(data.getTotalTickets()),    "", PRIMARY, bold, reg);
        addKpiCard(grid, "Tickets Clos",     String.valueOf(data.getTicketsClos()),     "✓", SUCCESS, bold, reg);
        addKpiCard(grid, "Total Chantiers",  String.valueOf(data.getTotalChantiers()),  "", ACCENT,   bold, reg);
        addKpiCard(grid, "Chantiers Term.",  String.valueOf(data.getChantiersTermines()),"✓", SUCCESS, bold, reg);

        doc.add(grid);
    }

    private static void addKpiCard(Table grid, String label, String value, String prefix,
                                   DeviceRgb color, PdfFont bold, PdfFont reg) {
        Cell card = new Cell()
                .setBackgroundColor(GRAY_LIGHT)
                .setBorderLeft(new SolidBorder(color, 4))
                .setBorderTop(Border.NO_BORDER).setBorderRight(Border.NO_BORDER).setBorderBottom(Border.NO_BORDER)
                .setPadding(12).setMargin(4);

        card.add(new Paragraph(label)
                .setFont(reg).setFontSize(8).setFontColor(GRAY_DARK)
                .setMarginBottom(4));
        card.add(new Paragraph(prefix + value)
                .setFont(bold).setFontSize(22).setFontColor(color)
                .setMarginBottom(0));

        grid.addCell(card);
    }

    // =========================================================================
    // TABLEAU TICKETS
    // =========================================================================
    private static void addTicketsTable(Document doc, ReportData data, PdfFont reg, PdfFont bold) {
        Table table = buildStyledTable(new float[]{55, 45});
        addTableHeader(table, new String[]{"Indicateur", "Valeur"}, bold);

        addTableRow(table, "Total des tickets",         String.valueOf(data.getTotalTickets()),     false, reg, bold);
        addTableRow(table, "Tickets clôturés",          String.valueOf(data.getTicketsClos()),      true,  reg, bold);
        addTableRow(table, "Tickets en cours",          String.valueOf(data.getTicketsOuverts()),   false, reg, bold);
        addTableRow(table, "Temps moyen de résolution", fmtHours(data.getTempsMoyenResolution()),  true,  reg, bold);
        addTableRow(table, "Temps médian de résolution",fmtHours(data.getTempsMedianResolution()), false, reg, bold);

        doc.add(table);
        addSpacing(doc, 10);
    }

    // =========================================================================
    // TABLEAU CHANTIERS
    // =========================================================================
    private static void addChantiersTable(Document doc, ReportData data, PdfFont reg, PdfFont bold) {
        Table table = buildStyledTable(new float[]{55, 45});
        addTableHeader(table, new String[]{"Indicateur", "Valeur"}, bold);

        addTableRow(table, "Total des chantiers",   String.valueOf(data.getTotalChantiers()),   false, reg, bold);
        addTableRow(table, "Chantiers terminés",    String.valueOf(data.getChantiersTermines()), true,  reg, bold);
        addTableRow(table, "Chantiers en cours",
                String.valueOf(data.getTotalChantiers() - data.getChantiersTermines()),          false, reg, bold);

        doc.add(table);
        addSpacing(doc, 10);
    }

    // =========================================================================
    // TABLEAU PERFORMANCE
    // =========================================================================
    private static void addPerformanceTable(Document doc, ReportData data, PdfFont reg, PdfFont bold) {
        Table table = buildStyledTable(new float[]{55, 25, 20});
        addTableHeader(table, new String[]{"Indicateur", "Valeur", "Statut"}, bold);

        double tauxRes   = safeRatio(data.getTicketsClos(), data.getTotalTickets()) * 100;
        double tauxChant = safeRatio(data.getChantiersTermines(), data.getTotalChantiers()) * 100;

        addTableRowWithStatus(table, "Taux de résolution tickets",
                String.format("%.1f %%", tauxRes),  statusLabel(tauxRes),   statusColor(tauxRes),  false, reg, bold);
        addTableRowWithStatus(table, "Taux d'achèvement chantiers",
                String.format("%.1f %%", tauxChant), statusLabel(tauxChant), statusColor(tauxChant), true, reg, bold);
        addTableRowWithStatus(table, "Temps moyen résolution",
                fmtHours(data.getTempsMoyenResolution()), "Info", ACCENT,  false, reg, bold);
        addTableRowWithStatus(table, "Temps médian résolution",
                fmtHours(data.getTempsMedianResolution()), "Info", ACCENT, true, reg, bold);

        doc.add(table);
        addSpacing(doc, 20);
    }

    // =========================================================================
    // BARRE DE PROGRESSION
    // =========================================================================
    private static void addProgressBar(Document doc, String label, double ratio,
                                       DeviceRgb color, PdfFont reg, PdfFont bold) {
        int pct = (int) Math.round(ratio * 100);

        doc.add(new Paragraph(label + " : " + pct + " %")
                .setFont(bold).setFontSize(9).setFontColor(TEXT_PRIMARY).setMarginBottom(3));

        // Track
        Table track = new Table(UnitValue.createPercentArray(new float[]{1}))
                .setWidth(UnitValue.createPercentValue(100)).setMarginBottom(6);

        Cell bg = new Cell().setHeight(14).setBackgroundColor(GRAY_LIGHT)
                .setBorder(new SolidBorder(GRAY_MID, 0.5f)).setPadding(0);

        // Fill (inner table trick)
        Table fill = new Table(UnitValue.createPercentArray(new float[]{(float) (ratio * 100), (float) ((1 - ratio) * 100)}))
                .setWidth(UnitValue.createPercentValue(100));
        fill.addCell(new Cell().setHeight(14).setBackgroundColor(color).setBorder(Border.NO_BORDER));
        if (ratio < 1.0)
            fill.addCell(new Cell().setHeight(14).setBorder(Border.NO_BORDER));
        bg.add(fill);
        track.addCell(bg);
        doc.add(track);
    }

    // =========================================================================
    // BLOC SIGNATURE
    // =========================================================================
    private static void addSignatureBlock(Document doc, PdfFont reg, PdfFont bold, PdfFont italic) {
        addSpacing(doc, 30);

        Table sig = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100));

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(20);
        left.add(new Paragraph("Visa Responsable Technique")
                .setFont(bold).setFontSize(9).setFontColor(TEXT_PRIMARY).setMarginBottom(40));
        left.add(new Paragraph("Signature & Cachet : ________________________")
                .setFont(reg).setFontSize(8).setFontColor(GRAY_DARK));
        sig.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(20);
        right.add(new Paragraph("Visa Direction")
                .setFont(bold).setFontSize(9).setFontColor(TEXT_PRIMARY).setMarginBottom(40));
        right.add(new Paragraph("Signature & Cachet : ________________________")
                .setFont(reg).setFontSize(8).setFontColor(GRAY_DARK));
        sig.addCell(right);

        doc.add(sig);

        doc.add(new Paragraph(
                "Document généré automatiquement par le Système de Gestion des Réclamations. "
                        + "Pour toute contestation, contacter le responsable qualité.")
                .setFont(italic).setFontSize(7).setFontColor(TEXT_SECONDARY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(20));
    }

    // =========================================================================
    // EN-TÊTE / PIED DE PAGE (handler)
    // =========================================================================
    static class HeaderFooterHandler implements IEventHandler {
        private final ReportData data;
        HeaderFooterHandler(ReportData data) { this.data = data; }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument      pdf      = docEvent.getDocument();
            PdfPage          page     = docEvent.getPage();
            int              pageNum  = pdf.getPageNumber(page);

            // Pas d'en-tête/pied sur la couverture
            if (pageNum == 1) return;

            PdfCanvas canvas = new PdfCanvas(page);
            PageSize  ps     = PageSize.A4;

            try {
                PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                // ── EN-TÊTE ───────────────────────────────────────────────
                canvas.setFillColor(PRIMARY_DARK)
                        .rectangle(0, ps.getHeight() - 38, ps.getWidth(), 38)
                        .fill();

                canvas.setFillColor(ACCENT)
                        .rectangle(0, ps.getHeight() - 41, ps.getWidth(), 3)
                        .fill();

                canvas.beginText()
                        .setFontAndSize(bold, 9)
                        .setColor(ColorConstants.WHITE, true)
                        .moveText(MARGIN_LEFT, ps.getHeight() - 24)
                        .showText("RAPPORT MENSUEL — " + moisLabel(data.getMois()).toUpperCase() + " " + data.getAnnee())
                        .endText();

                canvas.beginText()
                        .setFontAndSize(reg, 8)
                        .setColor(new DeviceRgb(179, 212, 255), true)
                        .moveText(ps.getWidth() - MARGIN_RIGHT - 120, ps.getHeight() - 24)
                        .showText("Système de Gestion des Réclamations")
                        .endText();

                // ── PIED DE PAGE ──────────────────────────────────────────
                canvas.setFillColor(GRAY_LIGHT)
                        .rectangle(0, 0, ps.getWidth(), 40)
                        .fill();

                canvas.setFillColor(PRIMARY)
                        .rectangle(0, 40, ps.getWidth(), 2)
                        .fill();

                canvas.beginText()
                        .setFontAndSize(reg, 7)
                        .setColor(TEXT_SECONDARY, true)
                        .moveText(MARGIN_LEFT, 16)
                        .showText("Document confidentiel — Usage interne uniquement")
                        .endText();

                // Numéro de page
                String pageLabel = "Page " + pageNum + " / " + pdf.getNumberOfPages();
                canvas.beginText()
                        .setFontAndSize(bold, 8)
                        .setColor(PRIMARY, true)
                        .moveText(ps.getWidth() - MARGIN_RIGHT - 50, 16)
                        .showText(pageLabel)
                        .endText();

                // Trait décoratif pied
                canvas.setStrokeColor(GRAY_MID)
                        .setLineWidth(0.5f)
                        .moveTo(ps.getWidth() / 2 - 30, 28)
                        .lineTo(ps.getWidth() / 2 + 30, 28)
                        .stroke();

            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // UTILITAIRES — TABLES
    // =========================================================================
    private static Table buildStyledTable(float[] cols) {
        return new Table(UnitValue.createPercentArray(cols))
                .setWidth(UnitValue.createPercentValue(100))
                .setMarginBottom(10);
    }

    private static void addTableHeader(Table table, String[] headers, PdfFont bold) {
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(PRIMARY).setPadding(7)
                    .setBorder(Border.NO_BORDER));
        }
    }

    private static void addTableRow(Table table, String label, String value,
                                    boolean shaded, PdfFont reg, PdfFont bold) {
        DeviceRgb bg = shaded ? GRAY_LIGHT : (DeviceRgb) ColorConstants.WHITE;

        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(reg).setFontSize(9).setFontColor(TEXT_PRIMARY))
                .setBackgroundColor(bg).setPadding(7)
                .setBorderBottom(new SolidBorder(GRAY_MID, 0.5f))
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER));

        table.addCell(new Cell()
                .add(new Paragraph(value).setFont(bold).setFontSize(9).setFontColor(PRIMARY_DARK))
                .setBackgroundColor(bg).setPadding(7)
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorderBottom(new SolidBorder(GRAY_MID, 0.5f))
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER));
    }

    private static void addTableRowWithStatus(Table table, String label, String value,
                                              String status, DeviceRgb statusColor,
                                              boolean shaded, PdfFont reg, PdfFont bold) {
        DeviceRgb bg = shaded ? GRAY_LIGHT : (DeviceRgb) ColorConstants.WHITE;

        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(reg).setFontSize(9).setFontColor(TEXT_PRIMARY))
                .setBackgroundColor(bg).setPadding(7)
                .setBorderBottom(new SolidBorder(GRAY_MID, 0.5f))
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER));

        table.addCell(new Cell()
                .add(new Paragraph(value).setFont(bold).setFontSize(9).setFontColor(PRIMARY_DARK))
                .setBackgroundColor(bg).setPadding(7).setTextAlignment(TextAlignment.CENTER)
                .setBorderBottom(new SolidBorder(GRAY_MID, 0.5f))
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER));

        table.addCell(new Cell()
                .add(new Paragraph(status).setFont(bold).setFontSize(8).setFontColor(statusColor))
                .setBackgroundColor(bg).setPadding(7).setTextAlignment(TextAlignment.CENTER)
                .setBorderBottom(new SolidBorder(GRAY_MID, 0.5f))
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER));
    }

    // =========================================================================
    // UTILITAIRES — DIVERS
    // =========================================================================
    private static void addChapterBanner(Document doc, String title, DeviceRgb color, PdfFont bold) {
        doc.add(new Paragraph(title)
                .setFont(bold).setFontSize(13).setFontColor(ColorConstants.WHITE)
                .setBackgroundColor(color).setPadding(10)
                .setBorderRadius(new BorderRadius(4))
                .setMarginBottom(10));
    }

    private static Cell tocCell(String text, PdfFont font, DeviceRgb color, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10).setFontColor(color)
                        .setTextAlignment(align))
                .setBorder(Border.NO_BORDER).setPaddingTop(4).setPaddingBottom(0);
    }

    private static void addSpacing(Document doc, float height) {
        doc.add(new Paragraph().setMarginTop(height));
    }

    private static double safeRatio(long part, long total) {
        return total == 0 ? 0 : Math.min(1.0, (double) part / total);
    }

    private static String fmtHours(double hours) {
        if (hours <= 0) return "N/A";
        long h = (long) hours;
        long m = Math.round((hours - h) * 60);
        return h + "h " + String.format("%02d", m) + "min";
    }

    private static String moisLabel(int m) {
        String[] mois = {"Janvier","Février","Mars","Avril","Mai","Juin",
                "Juillet","Août","Septembre","Octobre","Novembre","Décembre"};
        return (m >= 1 && m <= 12) ? mois[m - 1] : "Mois " + m;
    }

    private static String statusLabel(double pct) {
        if (pct >= 80) return "Excellent";
        if (pct >= 60) return "Correct";
        if (pct >= 40) return "Moyen";
        return "Insuffisant";
    }

    private static DeviceRgb statusColor(double pct) {
        if (pct >= 80) return SUCCESS;
        if (pct >= 60) return WARNING;
        return DANGER;
    }
}
