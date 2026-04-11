package com.example.micro_reclamation.Service;


import com.example.micro_reclamation.Entity.ReportData;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.layout.*;
import com.itextpdf.layout.element.Paragraph;

import java.io.ByteArrayOutputStream;

public class PdfGenerator {

    public static byte[] generate(ReportData data) {

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            doc.add(new Paragraph("RAPPORT MENSUEL").setBold().setFontSize(18));

            doc.add(new Paragraph("Tickets: " + data.getTotalTickets()));
            doc.add(new Paragraph("Tickets clos: " + data.getTicketsClos()));
            doc.add(new Paragraph("Chantiers terminés: " + data.getChantiersTermines()));
            doc.add(new Paragraph("Temps moyen: " + data.getTempsMoyenResolution()));

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}