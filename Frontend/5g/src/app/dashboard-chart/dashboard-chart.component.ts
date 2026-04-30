import { Component, OnInit } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';
import { Chart, registerables } from 'chart.js';
Chart.register(...registerables);

@Component({
  selector: 'app-dashboard-chart',
  templateUrl: './dashboard-chart.component.html',
  styleUrls: ['./dashboard-chart.component.css'],
  standalone: true
})
export class DashboardChartComponent implements OnInit {

  constructor(private userService: UtilisateurService) {}

  ngOnInit(): void {
    this.loadConnectionsChart();
    this.loadOnlineUsersChart();
  }

 loadConnectionsChart() {
  this.userService.getConnectionsPerDay().subscribe(data => {

    const existingChart = Chart.getChart('connectionsChart');
    if (existingChart) existingChart.destroy();

    const weekDays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

    // 🔹 Initialiser à 0
    const weekData: any = {
      'Lun': 0, 'Mar': 0, 'Mer': 0,
      'Jeu': 0, 'Ven': 0, 'Sam': 0, 'Dim': 0
    };

    const today = new Date();

    // 🔹 Trouver le lundi de cette semaine
    const currentDay = today.getDay(); // 0=Dim, 1=Lun...
    const diffToMonday = (currentDay === 0 ? -6 : 1 - currentDay);
    const monday = new Date(today);
    monday.setDate(today.getDate() + diffToMonday);
    monday.setHours(0, 0, 0, 0);

    // 🔥 Filtrer uniquement les connexions >= lundi
    data.forEach((d: any) => {

      const date = new Date(d.day);
      date.setHours(0, 0, 0, 0);

      if (date >= monday) {

        const dayMap = ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'];
        const dayName = dayMap[date.getDay()];

        if (weekData.hasOwnProperty(dayName)) {
          weekData[dayName] += d.count;
        }
      }
    });

    const values = weekDays.map(day => weekData[day]);

    new Chart('connectionsChart', {
      type: 'bar',
      data: {
        labels: weekDays,
        datasets: [{
          label: 'Connexions semaine en cours',
          data: values,
          maxBarThickness: 48,
          backgroundColor: 'rgba(0, 229, 160, 0.15)',
          borderColor: '#00e5a0',
          borderWidth: 2,
          borderRadius: 8,
          borderSkipped: false,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          duration: 1000,
          easing: 'easeOutQuart'
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#111318',
            borderColor: 'rgba(255,255,255,0.07)',
            borderWidth: 1,
            bodyColor: '#00e5a0',
            displayColors: false,
            callbacks: {
              label: (item) => `${item.raw} connexions`
            }
          }
        },
        scales: {
          x: {
            categoryPercentage: 0.7,
            barPercentage: 0.8,
            ticks: {
              color: '#e8eaf0',
              font: { family: 'DM Mono', size: 12 }
            }
          }as any,
          y: {
            beginAtZero: true,
            ticks: {
              color: '#5a6070',
              stepSize: 1
            }
          }
        }
      }
    });
  });
}

  loadOnlineUsersChart() {
    this.userService.getOnlineCount().subscribe(data => {

      const existingChart = Chart.getChart('onlineChart');
      if (existingChart) existingChart.destroy();

      new Chart('onlineChart', {
        type: 'doughnut',
        data: {
          labels: ['En ligne', 'Hors ligne'],
          datasets: [{
            data: [data.online, data.offline],
            backgroundColor: [
              'rgba(0, 229, 160, 0.8)',
              'rgba(255, 77, 109, 0.6)'
            ],
            borderColor: ['#00e5a0', '#ff4d6d'],
            borderWidth: 2,
            hoverOffset: 8
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '72%',
          plugins: {
            legend: { display: false },
            tooltip: {
              backgroundColor: '#111318',
              borderColor: 'rgba(255,255,255,0.07)',
              borderWidth: 1,
              titleColor: '#e8eaf0',
              bodyColor: '#5a6070',
              titleFont: { family: 'DM Mono', size: 11 },
              bodyFont:  { family: 'DM Mono', size: 12 },
              padding: 12,
              displayColors: true,
              callbacks: {
                label: (item) => `  ${item.raw} utilisateurs`
              }
            }
          }
        }
      });
    });
  }
}