import { Component, OnInit } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-reset',
  templateUrl: './reset.component.html',
  styleUrls: ['./reset.component.css']
})
export class ResetComponent implements OnInit{
verificationCode: string = '';
showVerificationInput: boolean = false; // pour afficher le champ code

showNew: boolean = false;
showConfirm: boolean = false;

hasUpper(): boolean { return /[A-Z]/.test(this.newPassword); }
hasNumber(): boolean { return /[0-9]/.test(this.newPassword); }
hasSpecial(): boolean { return /[^A-Za-z0-9]/.test(this.newPassword); }
  username: string = ''; 
  newPassword: string = '';
  confirmPassword: string = '';
  resetError: string = '';
  resetMessage: string = '';
  token: string = ''; // token généré automatiquement

  constructor(private authService: UtilisateurService,
              private router: Router,
              private route: ActivatedRoute) {}

  ngOnInit(): void {
    // 1️⃣ Récupérer le username depuis query params
    this.route.queryParams.subscribe(params => {
      this.username = params['username'];
      console.log("USERNAME RECUPERE :", this.username);

      // 2️⃣ Générer automatiquement le token dès que la page s'ouvre
      if (this.username) {
        this.generateToken();
      }
    });
  }

  // Génération automatique du token
  generateToken() {
    console.log("Appel de forgotPassword pour générer token");
    this.authService.forgotPassword(this.username).subscribe({
      next: (res: any) => {
        console.log("TOKEN RECUPERE :", res.token);
        if (!res.token) {
          this.resetError = "Impossible de générer le token";
          return;
        }
        this.token = res.token;
        this.resetMessage = `Token généré pour ${res.username}. Entrez votre nouveau mot de passe.`;
      },
      error: (err) => {
        console.error(err);
        this.resetError = err.error?.error || "Erreur serveur lors de la génération du token";
      }
    });
  }

  // 3️⃣ Valider le nouveau mot de passe
  onResetPassword() {
    console.log("TOKEN UTILISE :", this.token);
    if (!this.token) {
      this.resetError = "Token invalide ou expiré";
      return;
    }
    if (!this.newPassword || !this.confirmPassword) {
      this.resetError = "Veuillez remplir tous les champs";
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.resetError = "Les mots de passe ne correspondent pas";
      return;
    }

    this.authService.resetPassword(this.token, this.newPassword).subscribe({
      next: (res: any) => {
        localStorage.setItem('firstLogin', 'false');
        alert("Mot de passe réinitialisé avec succès !");
        this.router.navigate(['/map']);
      },
      error: (err) => {
        console.error(err);
        this.resetError = err.error || "Erreur serveur lors de la réinitialisation";
      }
    });
  }
 

sendVerificationCode() {
  // vérification des champs avant envoi
  if (!this.newPassword || !this.confirmPassword) {
    this.resetError = "Veuillez remplir les champs";
    return;
  }
  if (this.newPassword !== this.confirmPassword) {
    this.resetError = "Les mots de passe ne correspondent pas";
    return;
  }

  // Appel backend pour envoyer le code
  this.authService.sendResetCode(this.username).subscribe({
    next: () => {
      this.resetMessage = "Code envoyé par email.";
      this.resetError = '';
     this.router.navigate(['/code']);
    },
    error: () => {
      this.resetError = "Erreur lors de l'envoi du code";
    }
  });
}
isCodeVerified: boolean = false;
handleResetButton() {
  // Si le code n'est pas encore validé
  if (!this.isCodeVerified) {
    if (!this.verificationCode) {
      this.resetError = "Veuillez saisir le code de vérification";
      return;
    }

    // Vérifier le code
    this.authService.verifyResetCode(this.username, this.verificationCode)
      .subscribe({
        next: (res: any) => {
          this.resetMessage = res.message || "Code valide ! Vous pouvez maintenant saisir votre mot de passe.";
          this.resetError = '';
          this.isCodeVerified = true; // code validé
        },
        error: (err) => {
          this.resetError = err.error || "Code invalide ou expiré";
        }
      });
  } else {
    // Code déjà validé → reset password
    if (!this.newPassword || !this.confirmPassword) {
      this.resetError = "Veuillez remplir tous les champs pour le nouveau mot de passe";
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.resetError = "Les mots de passe ne correspondent pas";
      return;
    }

    this.authService.resetPassword(this.token, this.newPassword).subscribe({
      next: (res: any) => {
        alert("Mot de passe réinitialisé avec succès !");
        this.router.navigate(['/login']); // Redirection après reset
      },
      error: (err) => {
        console.error("Erreur resetPassword :", err);
        this.resetError = err.error || "Erreur lors de la réinitialisation";
      }
    });
  }
}

}
