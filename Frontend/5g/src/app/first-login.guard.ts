import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class FirstLoginGuard implements CanActivate {
  constructor(private router: Router) {}

  canActivate(): boolean {
    if (localStorage.getItem('firstLogin') === 'true') {
      this.router.navigate(['/reset'], {
        queryParams: { username: localStorage.getItem('username') }
      });
      return false;
    }
    return true;
  }
}