import { Component, OnInit } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {

  user: any = {};
  username: any;
  activeSection: 'info' | 'security' | 'activity' = 'info';
  showToast: boolean = false;
  loginHistory: any[] = [];

  constructor(private authService: UtilisateurService) {}

  ngOnInit(): void {
    this.username = localStorage.getItem('username');

    this.authService.getProfile(this.username).subscribe((data: any) => {
      this.user = data;
    });

    this.authService.getHistory(this.username).subscribe({
      next: (data: any) => this.loginHistory = data,
      error: () => this.loginHistory = []
    });
  }

  updateProfile(): void {
    this.authService.updateProfile(this.username, this.user).subscribe(() => {
      this.showToast = true;
      setTimeout(() => this.showToast = false, 3000);
    });
  }
}