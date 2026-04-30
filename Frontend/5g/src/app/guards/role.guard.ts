import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { UtilisateurService } from '../utilisateur-service.service';

@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {

  constructor(
    private authService: UtilisateurService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {

    const expectedRoles: string[] = route.data['roles'];
    const userRole = this.authService.getUserRole();

    // Si pas connecté
    if (!userRole) {
      this.router.navigate(['/login']);
      return false;
    }

    // Vérification du rôle
    if (expectedRoles && expectedRoles.includes(userRole)) {
      return true;
    }
    console.log("Expected Roles:", expectedRoles);
    console.log("User Role:", userRole);


    // Accès interdit
    this.router.navigate(['/login']);
    return false;
  }
}
