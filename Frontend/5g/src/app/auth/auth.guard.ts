import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';

export const authGuard: CanActivateFn = (route, state) => {

  const router = inject(Router);

  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');

  // ❌ pas connecté
  if (!token) {
    router.navigate(['/login']);
    return false;
  }

  // 🔐 check role si demandé
  const requiredRoles = route.data['roles'] as string[];

  if (requiredRoles && requiredRoles.length > 0) {

    if (!role || !requiredRoles.includes(role)) {
      router.navigate(['/login']); // ou /access-denied
      return false;
    }
  }

  return true;
};