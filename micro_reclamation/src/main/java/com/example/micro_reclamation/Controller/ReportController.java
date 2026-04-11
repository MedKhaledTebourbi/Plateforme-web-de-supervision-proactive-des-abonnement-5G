package com.example.micro_reclamation.Controller;


import com.example.micro_reclamation.Entity.QoSReportDTO;
import com.example.micro_reclamation.Entity.ReportData;
import com.example.micro_reclamation.Service.ExcelGenerator;
import com.example.micro_reclamation.Service.PdfGenerator;
import com.example.micro_reclamation.Service.ReportService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/monthly")
    public ReportData monthly(@RequestParam int mois, @RequestParam int annee) {
        return reportService.generateMonthlyReport(mois, annee);
    }

    @GetMapping("/monthly/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam int mois, @RequestParam int annee) {
        return ResponseEntity.ok(
                PdfGenerator.generate(reportService.generateMonthlyReport(mois, annee))
        );
    }

    @GetMapping("/monthly/excel")
    public ResponseEntity<byte[]> excel(@RequestParam int mois, @RequestParam int annee) {
        return ResponseEntity.ok(
                ExcelGenerator.generate(reportService.generateMonthlyReport(mois, annee))
        );
    }

    @GetMapping("/qos")
    public QoSReportDTO qos() {
        return reportService.getQoSReport();
    }
}