package com.example.micro_reclamation.Service;


import com.example.micro_reclamation.Entity.*;
import com.example.micro_reclamation.Repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j


@Service
@RequiredArgsConstructor
public class ReportService {

    private final TicketRepository ticketRepository;
    private final ChantierRepository chantierRepository;

    // =========================
    // 📅 RAPPORT MENSUEL
    // =========================
    public ReportData generateMonthlyReport(int mois, int annee) {

        List<Ticket> tickets = ticketRepository.findByMonth(mois, annee);
        List<Chantier> chantiers = chantierRepository.findByMonth(mois, annee);

        long ticketsClos = tickets.stream()
                .filter(t -> "CLOS".equalsIgnoreCase(t.getStatut().name()))
                .count();

        long chantiersTermines = chantiers.stream()
                .filter(c -> "TERMINE".equalsIgnoreCase(c.getStatut()))
                .count();

        List<Long> durations = tickets.stream()
                .filter(t -> t.getDateFinTraitement() != null && t.getDateDebutTraitement() != null)
                .map(t -> ChronoUnit.HOURS.between(
                        t.getDateDebutTraitement(),
                        t.getDateFinTraitement()))
                .sorted()
                .toList();

        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        double median = durations.isEmpty() ? 0 : durations.get(durations.size() / 2);

        return ReportData.builder()
                .mois(mois)
                .annee(annee)
                .totalTickets(tickets.size())
                .ticketsClos(ticketsClos)
                .ticketsOuverts(tickets.size() - ticketsClos)
                .totalChantiers(chantiers.size())
                .chantiersTermines(chantiersTermines)
                .tempsMoyenResolution(avg)
                .tempsMedianResolution(median)
                .build();
    }

    // =========================
    // 👷 TECHNICIEN
    // =========================
    public TechnicienReportDTO getTechnicienReport(Long technicienId) {

        List<Ticket> tickets = ticketRepository.findAll().stream()
                .filter(t -> technicienId.equals(t.getUpdatedBy()))
                .toList();

        List<Chantier> chantiers = chantierRepository.findByTechnicienId(technicienId);

        long ticketsClos = tickets.stream()
                .filter(t -> "CLOS".equalsIgnoreCase(t.getStatut().name()))
                .count();

        long chantiersTermines = chantiers.stream()
                .filter(c -> "TERMINE".equalsIgnoreCase(c.getStatut()))
                .count();

        double avgTime = tickets.stream()
                .filter(t -> t.getDateFinTraitement() != null)
                .mapToLong(t -> ChronoUnit.HOURS.between(
                        t.getDateDebutTraitement(),
                        t.getDateFinTraitement()))
                .average().orElse(0);

        int workload = tickets.size() + chantiers.size();

        return TechnicienReportDTO.builder()
                .technicienId(technicienId)
                .ticketsTraites(tickets.size())
                .ticketsClos(ticketsClos)
                .chantiersRealises(chantiers.size())
                .chantiersTermines(chantiersTermines)
                .performance((ticketsClos + chantiersTermines) * 100.0 / (workload + 1))
                .tempsMoyenResolution(avgTime)
                .efficacite(ticketsClos * 100.0 / (tickets.size() + 1))
                .chargeTravail(workload)
                .build();
    }

    // =========================
    // 📡 QoS
    // =========================
    public QoSReportDTO getQoSReport() {

        List<Ticket> tickets = ticketRepository.findAll();

        long resolved = tickets.stream()
                .filter(t -> "CLOS".equalsIgnoreCase(t.getStatut().name()))
                .count();

        long critical = tickets.stream()
                .filter(t -> "PANNE CRITIQUE".equalsIgnoreCase(t.getTypePanne()))
                .count();

        double avg = tickets.stream()
                .filter(t -> t.getDateFinTraitement() != null)
                .mapToLong(t -> ChronoUnit.HOURS.between(
                        t.getDateCreation(),
                        t.getDateFinTraitement()))
                .average().orElse(0);

        long slaOk = tickets.stream()
                .filter(t -> t.getDateFinTraitement() != null)
                .filter(t -> ChronoUnit.HOURS.between(
                        t.getDateCreation(),
                        t.getDateFinTraitement()) <= 24)
                .count();

        return QoSReportDTO.builder()
                .tempsMoyenResolution(avg)
                .tauxSatisfaction(resolved * 100.0 / (tickets.size() + 1))
                .disponibiliteReseau(100 - (critical * 2))
                .totalIncidents(tickets.size())
                .incidentsResolus(resolved)
                .tauxSLA(slaOk * 100.0 / (resolved + 1))
                .pannesCritiques(critical)
                .build();
    }
}