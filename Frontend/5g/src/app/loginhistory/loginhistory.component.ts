import { Component, OnInit } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-loginhistory',
  templateUrl: './loginhistory.component.html',
  styleUrls: ['./loginhistory.component.css'],
  providers: [ { provide: DatePipe, useClass: DatePipe } ]
})
export class LoginhistoryComponent implements OnInit{
   users: any[] = [];
  selectedUser: string | null = null;
 username!: string;
  history: any[] = [];

    constructor(private authService: UtilisateurService, private router: Router, private http: HttpClient,private route: ActivatedRoute,) {
  
      
    }
  ngOnInit(): void {

  this.route.queryParams.subscribe(params => {
    this.username = params['username'];

    if (this.username) {
      // 🔥 Charger uniquement cet utilisateur
      this.loadSingleUser(this.username);
      this.loadHistory();
    } else {
      // Sinon afficher tous
      
    }
  });

}
loadSingleUser(username: string) {
  this.authService.getUserByUsername(username)
    .subscribe(user => {
      this.users = [user]; // 🔥 tableau avec UN SEUL user
    });
}
  loadHistory() {
    this.authService.getHistory(this.username)
      .subscribe(data => {
        this.history = data;
      });
  }

  loadUsers() {
    this.authService.getUsers().subscribe(res => this.users = res);
  }

  toggle(user: any) {
    this.authService.toggle(user.username)
      .subscribe(() => this.loadUsers());
  }

  showHistory(username: string) {
    this.selectedUser = username;

    this.authService.getHistory(username)
      .subscribe(res => this.history = res);
  }
}
