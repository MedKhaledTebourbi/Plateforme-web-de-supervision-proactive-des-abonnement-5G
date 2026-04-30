import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';
import { User } from '../utilisateur/User';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-userback',
  templateUrl: './userback.component.html',
  styleUrls: ['./userback.component.css'],
  encapsulation: ViewEncapsulation.None
})
export class UserbackComponent implements OnInit {
  currentPage = 0;
  stats: any[] = [];
pageSize = 5;
totalPages = 0;
onlineCount: number = 0;
totalConnectionsWeek: number = 0;

selectedRole: string = '';
selectedActive: any = '';

todayConnections: number = 0;
dailyAverage: number = 0;
mostActiveDay: string = '';
previousWeekConnections: number = 0;
percentageChange: number = 0;
trend: 'up' | 'down' = 'up';
  users: User[] = [];
 currentUser: any;
  constructor(private utilisateurService: UtilisateurService, private router: Router, private http: HttpClient) {}
intervalId: any;

 ngOnInit(): void {
  this.loadUsers();
  this.loadCurrentUser();
   this.loadStats();
   this.loadTotalConnectionsWeek();
    this.loadAdvancedConnectionStats();
  

  this.intervalId = setInterval(() => {
    this.loadUsers();
  }, 10000); // refresh toutes les 10 sec
}

ngOnDestroy() {
  clearInterval(this.intervalId);
}

  loadUsers(): void {
  this.utilisateurService.getAllUsers().subscribe({
    next: (data) => {

      this.users = data;

      // 🔥 On récupère tous les statuts en une seule requête
      this.utilisateurService.getAllStatus().subscribe({
        next: (statusMap) => {

          this.users.forEach(user => {
            user.status = statusMap[user.username] ? 'ONLINE' : 'OFFLINE';
          });

        },
        error: (err) => {
          console.error("Erreur récupération status", err);
        }
      });

    },
    error: (err) => {
      console.error("Erreur lors du chargement des users", err);
    }
  });
}
  deleteUser(id: number): void {
  if (!confirm("Voulez-vous vraiment supprimer cet utilisateur ?")) return;

  this.utilisateurService.deleteUser(id).subscribe({
    next: () => {
      console.log("Utilisateur supprimé:", id);
      this.users = this.users.filter(user => user.id !== id); // met à jour la liste
    },
    error: (err) => {
      console.error("Erreur lors de la suppression", err);
    }
  });
}
loadCurrentUser() {
  this.utilisateurService.getCurrentUser().subscribe({
    next: (user) => {
      this.currentUser = user;
      console.log('Utilisateur connecté:', user);
    },
    error: (err) => {
      console.error('Erreur lors de la récupération de l’utilisateur', err);
    }
  });
}

logout() {
  this.utilisateurService.logout();
  // Redirige vers la page login
  this.router.navigate(['/login']);
}
toggleActive(username: string) {
  this.utilisateurService.toggleUserActive(username).subscribe({
    next: (res: any) => {
      alert(res);
      // Optionnel : rafraîchir la liste des utilisateurs
      this.loadUsers();
    },
    error: (err) => {
      console.error(err);
      alert("Erreur lors de la modification de l'état du compte");
    }
  });
}
searchUsers() {
  this.utilisateurService.searchUsers(
    this.selectedRole || undefined,
    this.selectedActive !== '' ? this.selectedActive : undefined,
    this.currentPage,
    this.pageSize
  ).subscribe({
    next: (res) => {
      this.users = res.content;
      this.totalPages = res.totalPages;
    },
    error: (err) => {
      console.error(err);
    }
  });
}
nextPage() {
  if (this.currentPage < this.totalPages - 1) {
    this.currentPage++;
    this.searchUsers();
  }
}

previousPage() {
  if (this.currentPage > 0) {
    this.currentPage--;
    this.searchUsers();
  }
}
// Variables pour les filtres
filterRole: string = '';
filterActive: string | null = null; // null = pas de filtre
totalUsers: number = 0;
filterUsers(page: number = 0) {
  this.currentPage = page;

  // Convertir filterActive en boolean si nécessaire
  let activeValue: boolean | undefined = undefined;
  if (this.filterActive === 'true') activeValue = true;
  else if (this.filterActive === 'false') activeValue = false;

  this.utilisateurService.searchUsers(this.filterRole, activeValue, this.currentPage, this.pageSize)
    .subscribe({
      next: (data) => {
        this.users = data.content; // ton endpoint doit renvoyer Page<User>
        this.totalUsers = data.totalElements;
        console.log("Users filtrés:", this.users);
      },
      error: (err) => {
        console.error("Erreur lors du filtrage", err);
      }
    });
}
goToHistory(username: string) {
  this.router.navigate(['/his'], {
    queryParams: { username: username }
  });}
loadStats() {

  this.stats = [
    {
      label: 'Total Utilisateurs',
      value: this.users.length,
      percent: 12,
      trend: 'up',
      period: 'ce mois',
      color: 'var(--accent)'
    },
    {
      label: 'Utilisateurs en ligne',
      value: this.onlineCount,
      percent: 5,
      trend: 'up',
      period: "aujourd'hui",
      color: '#00e5a0'
    },
    {
      label: 'Connexions semaine',
      value: this.totalConnectionsWeek,
      percent: 8,
      trend: 'up',
      period: 'vs semaine passée',
      color: '#60a5fa'
    }
  ];
}
loadTotalConnectionsWeek() {
  this.utilisateurService.getConnectionsPerDay().subscribe(data => {

    const today = new Date();
    const currentDay = today.getDay();
    const diffToMonday = (currentDay === 0 ? -6 : 1 - currentDay);
    const monday = new Date(today);
    monday.setDate(today.getDate() + diffToMonday);
    monday.setHours(0, 0, 0, 0);

    let total = 0;

    data.forEach((d: any) => {
      const date = new Date(d.day);
      date.setHours(0, 0, 0, 0);

      if (date >= monday) {
        total += d.count;
      }
    });

    this.totalConnectionsWeek = total;
  });
}
loadAdvancedConnectionStats() {
  this.utilisateurService.getConnectionsPerDay().subscribe(data => {

    const weekDays = ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'];

    const today = new Date();
    const currentDay = today.getDay();
    const diffToMonday = (currentDay === 0 ? -6 : 1 - currentDay);

    const monday = new Date(today);
    monday.setDate(today.getDate() + diffToMonday);
    monday.setHours(0, 0, 0, 0);

    let total = 0;
    let todayTotal = 0;

    let dayTotals: any = {
      'Dim': 0, 'Lun': 0, 'Mar': 0,
      'Mer': 0, 'Jeu': 0, 'Ven': 0, 'Sam': 0
    };

    data.forEach((d: any) => {

      const date = new Date(d.day);
      date.setHours(0, 0, 0, 0);

      if (date >= monday) {

        total += d.count;

        const dayName = weekDays[date.getDay()];
        dayTotals[dayName] += d.count;

        // Connexions aujourd'hui
        if (date.getTime() === new Date().setHours(0,0,0,0)) {
          todayTotal += d.count;
        }
      }
    });

    this.totalConnectionsWeek = total;
    this.todayConnections = todayTotal;

    // Moyenne quotidienne
    this.dailyAverage = Math.round(total / 7);

    // Jour le plus actif
    let maxDay = '';
    let maxValue = 0;

    Object.keys(dayTotals).forEach(day => {
      if (dayTotals[day] > maxValue) {
        maxValue = dayTotals[day];
        maxDay = day;
      }
    });

    const frenchDays: any = {
      'Dim': 'Dimanche',
      'Lun': 'Lundi',
      'Mar': 'Mardi',
      'Mer': 'Mercredi',
      'Jeu': 'Jeudi',
      'Ven': 'Vendredi',
      'Sam': 'Samedi'
    };

    this.mostActiveDay = frenchDays[maxDay];
  });
}
}
