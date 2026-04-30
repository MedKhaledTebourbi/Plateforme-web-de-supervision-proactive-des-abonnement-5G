import { Component, OnInit } from '@angular/core';
import { User } from '../utilisateur/User';
import { Subject } from 'rxjs';
import { UtilisateurService } from '../utilisateur-service.service';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-registe',
  templateUrl: './registe.component.html',
  styleUrls: ['./registe.component.css']
})
export class RegisteComponent  implements OnInit {
 isSignIn: boolean = true;
  isForgotPassword: boolean = false;
  username: string = '';
  password: string = '';
  errorMessage: string = '';
  resetEmail: string = '';
  resetSuccess: boolean = false;
  resetError: string = '';
  email: string = '';
  message: string = '';
  // Validation error messages
  signupErrors = {
    username: '',
    email: '',
    password: '',
    role: '',
     region: '' 
  };

  user: User = new User();

  resultMessage: string = '';
  showWebcam: boolean = false;  // Track webcam activation status
  // Subject for triggering webcam snapshot
  trigger: Subject<void> = new Subject<void>();

  constructor(private authService: UtilisateurService, private router: Router, private http: HttpClient) {

    
  }

  ngOnInit(): void {
    localStorage.clear(); // ⬅️ Add this to reset old login sessions
    setTimeout(() => {
      this.isSignIn = true;
    }, 200);
  } 
  
  
  
  

  // Trigger webcam snapshot

  
  // Handle forgot password
  onForgotPassword() {
    console.log('Reset email:', this.resetEmail);

    this.authService.forgotPassword(this.resetEmail).subscribe({
      next: (response) => {
        console.log('Response received:', response);
        this.message = "Email de réinitialisation envoyé !";
      },
      error: (err) => {
        console.error('Error received:', err);
        this.message = "Erreur : " + (err.error?.message || err.message || 'Unknown error');
      }
    });
  }

  // Toggle between sign-in and sign-up views
  toggle(): void {
    this.isSignIn = !this.isSignIn;
    this.isForgotPassword = false;
    this.resetErrors();
    this.resetError = '';
  }

  // Back to login from forgot password view
  backToLogin(): void {
    this.isForgotPassword = false;
    this.resetEmail = '';
    this.resetError = '';
  }

  // Validation function for signup
  validateSignup(): boolean {
    this.resetErrors();
    let isValid = true;
    if (!this.user.role) {
  this.signupErrors.role = "Veuillez sélectionner un rôle";
  return false;
}


    // Validate username
   /*if (!this.user.username || this.user.username.trim().length === 0) {
      this.signupErrors.username = 'Username is required';
      isValid = false;
    } else if (this.user.username.length < 3) {
      this.signupErrors.username = 'Username must be at least 3 characters';
      isValid = false;
    }*/

    // Validate email
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!this.user.email) {
      this.signupErrors.email = 'Email is required';
      isValid = false;
    } else if (!emailPattern.test(this.user.email)) {
      this.signupErrors.email = 'Invalid email format';
      isValid = false;
    }

    // Validate password
    if (!this.user.password) {
      this.signupErrors.password = 'Password is required';
      isValid = false;
    } else if (this.user.password.length < 6) {
      this.signupErrors.password = 'Password must be at least 6 characters';
      isValid = false;
    }

    // Validate role
    if (!this.user.role) {
      this.signupErrors.role = 'Please select a role';
      isValid = false;
    }

    return isValid;
  }

  // Reset error messages
  resetErrors(): void {
    this.signupErrors = {
      username: '',
      email: '',
      password: '',
      role: '',
      region: '' 
    };
  }

  // Show forgot password view
  showForgotPassword() {
    this.isForgotPassword = true;
    this.isSignIn = true;
  }

  // Hide forgot password view
  hideForgotPassword() {
    this.isForgotPassword = false;
    this.isSignIn = true;
  }

  // Register new user
  onRegister(): void {
  // Valide les champs du formulaire
  if (!this.validateSignup()) {
    return;
  }

  // Génère un username automatique pour tous les rôles
  this.user.username = this.generateUsername();

  console.log('User Data à envoyer au backend :', this.user);

  // Appel du service pour enregistrer l'utilisateur
  this.authService.register(this.user).subscribe({
    next: (response) => {
      console.log('User registered successfully', response);
      alert(`Compte créé avec succès ! Username: ${this.user.username}`);
      this.router.navigate(['/userback']);
    },
    error: (error) => {
      console.error('Registration failed:', error);
       this.router.navigate(['/userback']);

      // Si le username est déjà utilisé côté backend
      if (error.status === 409) {
        alert('Erreur : ce username existe déjà. Réessayez.');
      } else {
        alert('Erreur lors de la création du compte');
      }
    }
  });
}


  // Login user
  onLogin(): void {
    if (!this.username || !this.password) {
      this.errorMessage = 'Veuillez remplir tous les champs';
      return;
    }
  
    const user = { username: this.username, password: this.password };
  
    this.authService.login(user).subscribe(
      (response) => {
        console.log('Réponse du serveur :', response);
  
        if (response && response.username) {
          console.log('Utilisateur connecté:', response.username);
        }
  
        // Store data in localStorage
        localStorage.setItem('token', response.token);
        localStorage.setItem('username', response.username);
        localStorage.setItem('id', response.id);
        localStorage.setItem('role', response.role);
         localStorage.setItem('region', response.region);  // Store role
       // this.router.navigate(['/employe'])
 
        // 🔀 Redirect based on role
        if (response.role === 'ADMIN') {
          this.router.navigate(['/projets']); // Route for admin/backoffice
        } else {
          this.router.navigate(['/terrefront']); // Route for standard users
        }
      },
      (error) => {
        console.error('Erreur de connexion :', error);
        this.errorMessage = 'Nom d’utilisateur ou mot de passe incorrect';
      }
    );
  }
  
  

  // Reset password logic
  onResetPassword(): void {
    if (!this.resetEmail) {
      this.resetError = 'Email is required';
      return;
    }

    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailPattern.test(this.resetEmail)) {
      this.resetError = 'Invalid email format';
      return;
    }

    // Call your password reset service here
  }
  generateUsername(): string {

  const prefix =
    this.user.role === 'TECHNICIEN' ? 'TECH':
    this.user.role === 'CHEF' ? 'CHEF'
    : 'ADMIN';

  // 👇 prendre les 3 premières lettres de la région
  let regionCode = '';

  if (this.user.region) {
    regionCode = this.user.region
      .substring(0, 3)     // 3 premières lettres
      .toUpperCase()       // majuscule
      .normalize("NFD")    // enlever accents
      .replace(/[\u0300-\u036f]/g, "");
  }

  const randomNumber = Math.floor(100 + Math.random() * 900);

  // 👇 logique spéciale pour TECHNICIEN
  if (this.user.role === 'TECHNICIEN' && regionCode) {
    return `${prefix}_${regionCode}_${randomNumber}`;
  }
   if (this.user.role === 'CHEF' && regionCode) {
    return `${prefix}_${regionCode}_${randomNumber}`;
  }

  return `${prefix}_${randomNumber}`;
}

}
