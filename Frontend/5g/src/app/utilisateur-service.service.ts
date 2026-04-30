import { Injectable } from '@angular/core';
import{HttpClient, HttpClientModule} from  '@angular/common/http';

import { Observable, tap } from 'rxjs';
import { User } from './utilisateur/User';
import{LoginHistory} from './loginhistory/History';
@Injectable({
  providedIn: 'root'
})
export class UtilisateurService {
  private baseURL="http://localhost:8084/auth";
  router: any;
  constructor(private httpClient:HttpClient) { }
  login(user: { username: string; password: string }): Observable<any> {
    return this.httpClient.post<any>(`${this.baseURL}/login`, user).pipe(
      tap(res => {
        // Stocker le token et le rôle séparément
        localStorage.setItem('token', res.token);
        localStorage.setItem('role', res.role);
        localStorage.setItem('username',   res.username);
        localStorage.setItem('firstLogin', res.firstLogin ? 'true' : 'false');
      })
    );;
  }

  register(user: User): Observable<any> {
    
    return this.httpClient.post(`${this.baseURL}/register`, user, {
      headers: { 'Content-Type': 'application/json' }
    });
  }
/*************  ✨ Windsurf Command ⭐  *************/
  /**
   * Sends a request to the server to initiate the password reset process for the specified email.
   * 
   * @param email - The email address of the user who wants to reset their password.
   * @returns An Observable that emits the server's response as a text string.
   */

/*******  693f7eb4-504e-4ed5-802b-7fbe476b4b5f  *******/
forgotPassword(username: string): Observable<{username: string, token: string}> {
  return this.httpClient.post<{username: string, token: string}>(`${this.baseURL}/forgot-password`, { username });
}


  // Valider nouveau mot de passe via token
  resetPassword(token: string, password: string): Observable<any> {
    const body = { password };
    console.log("TOKEN ENVOYE AU BACK :", token);
    return this.httpClient.post(`${this.baseURL}/reset-password?token=${token}`, body);
  }
  sendResetCode(username: string) {
  return this.httpClient.post(
    `http://localhost:8084/auth/send-reset-code?username=${username}`,
    {}
  );
}
deleteUser(id: number): Observable<any> {
  return this.httpClient.delete(`${this.baseURL}/usersd/${id}`);
}

getEmailFromUsername(username: string) {
  return this.httpClient.get<{ email: string }>(`${this.baseURL}/get-email?username=${username}`);
}
getAllUsers(): Observable<User[]> {
  return this.httpClient.get<User[]>(`${this.baseURL}/users`);
}


  // 🔥 2️⃣ Vérifier le code
 verifyResetCode(username: string, code: string) {
  return this.httpClient.post(
    `http://localhost:8084/auth/verify-code`,
    { username, code },
    { responseType: 'text' } // 🔹 important pour recevoir du texte brut
  );
}

getUserRole(): string | null {
  const role = localStorage.getItem('role'); // récupère directement le rôle
  return role ? role : null;
}





  checkEmailAvailability(email: string): Observable<any> {
    return this.httpClient.get<any>(`${this.baseURL}/check-email?email=${email}`);
  }



// Récupérer l'utilisateur courant
getCurrentUser(): Observable<User> {
  // Pour test, on ne met pas d'Authorization
  return this.httpClient.get<User>(`${this.baseURL}/me`);
}



toggleUserActive(username: string) {
  return this.httpClient.patch(
    `http://localhost:8084/auth/toggle-active/${username}`,
    {},
    { responseType: 'text' }
  );
}

getUserStatus(username: string) {
  return this.httpClient.get<any>(
    `http://localhost:8084/auth/user-status/${username}`
  );
}
getAllStatus() {
  return this.httpClient.get<any>('http://localhost:8084/auth/all-status');
}
searchUsers(role?: string, active?: boolean, page: number = 0, size: number = 5) {

  let params: any = {
    page: page,
    size: size
  };

  if (role) params.role = role;
  if (active !== undefined) params.active = active;

  return this.httpClient.get<any>(
    'http://localhost:8084/auth/users/search',
    { params }
  );
}
searchUsers1(role?: string, active?: boolean) {
  let params: any = {};
  if (role) params.role = role;
  if (active !== null && active !== undefined) params.active = active;

  return this.httpClient.get<any>('/auth/users/search', { params });
}
  getUsers() {
    return this.httpClient.get<User[]>(this.baseURL);
  }

  toggle(username: string) {
    return this.httpClient.put(`${this.baseURL}/toggle/${username}`, {});
  }
   getHistory(username: string) {
    return this.httpClient.get<LoginHistory[]>(`${this.baseURL}/history/${username}`);
  }
  getUserByUsername(username: string) {
  return this.httpClient.get<any>(
    `http://localhost:8084/auth/usersa/${username}`
  );
}
getConnectionsPerDay() {
  return this.httpClient.get<any[]>('http://localhost:8084/auth/stats/connections');
}

getOnlineCount() {
  return this.httpClient.get<any>('http://localhost:8084/auth/stats/online');
}
logout() {
  const username = localStorage.getItem('username');

  if (!username) return;

  this.httpClient.post(`${this.baseURL}/logout`, {
    username: username
  }).subscribe(() => {

    localStorage.removeItem('token');
    localStorage.removeItem('username');
    this.router.navigate(['/login']);
  });
}

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }
    getProfile(username: string){
    return this.httpClient.get(this.baseURL + "/profile/" + username);
  }

  updateProfile(username: string, data: any){
    return this.httpClient.put(this.baseURL + "/update-profile/" + username, data);
  }
  getTechniciensByRegion(region: string) {
  return this.httpClient.get<any[]>(
    `http://localhost:8084/auth/techniciens?region=${region}`
  );
}
}