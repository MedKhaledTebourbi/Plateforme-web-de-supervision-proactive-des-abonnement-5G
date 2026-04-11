package com.example.micro_reclamation.Service;


import com.example.micro_reclamation.Entity.ReportData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;

public class ExcelGenerator {

    public static byte[] generate(ReportData data) {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Report");

            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Total Tickets");
            row.createCell(1).setCellValue(data.getTotalTickets());

            wb.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}