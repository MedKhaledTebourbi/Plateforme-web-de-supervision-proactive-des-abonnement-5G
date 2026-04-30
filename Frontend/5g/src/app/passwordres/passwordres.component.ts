import { Component, OnInit } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-passwordres',
  templateUrl: './passwordres.component.html',
  styleUrls: ['./passwordres.component.css']
})
export class PasswordresComponent implements OnInit {

  username: string = '';

  newPassword: string = '';
  confirmPassword: string = '';

  verificationCode: string = '';
token: string = '';
  resetMessage: string = '';
  resetError: string = '';
showPass: boolean = false;
  showConfirm: boolean = false;
  constructor(
    private userService: UtilisateurService,
    private router: Router
  ) {}

  ngOnInit(): void {

    // récupérer user connecté
    this.userService.getCurrentUser().subscribe({
      next: (user:any) => {
        this.username = user.username;
         this.generateToken();
        console.log("USER CONNECTE :", this.username);
      },
      error: () => {
        this.resetError = "Impossible de récupérer l'utilisateur";
      }
      
    });
    

  }
   getStrength(): number {
    const p = this.newPassword;
    if (!p) return 0;
    let score = 0;
    if (p.length >= 6)  score++;
    if (p.length >= 10) score++;
    if (/[A-Z]/.test(p) && /[0-9]/.test(p)) score++;
    if (/[^A-Za-z0-9]/.test(p)) score++;
    return Math.max(1, score);
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
  this. userService.sendResetCode(this.username).subscribe({
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
 generateToken() {
    console.log("Appel de forgotPassword pour générer token");
    this. userService.forgotPassword(this.username).subscribe({
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
  // envoyer code email
  sendCode() {

    if(!this.newPassword || !this.confirmPassword){
      this.resetError = "Veuillez remplir les champs";
      return;
    }

    if(this.newPassword !== this.confirmPassword){
      this.resetError = "Les mots de passe ne correspondent pas";
      return;
    }

    this.userService.sendResetCode(this.username).subscribe({
      next: () => {
        this.resetMessage = "Code envoyé par email";
        this.resetError = "";
      },
      error: () => {
        this.resetError = "Erreur lors de l'envoi du code";
      }
    });

  }

  // verifier code
  verifyCode(){

    this.userService.verifyResetCode(this.username,this.verificationCode)
    .subscribe({
      next:(res:any)=>{

        this.resetMessage="Code valide";

        this.userService.forgotPassword(this.username).subscribe((tokenRes:any)=>{

          const token = tokenRes.token;

          this.userService.resetPassword(token,this.newPassword)
          .subscribe(()=>{

            alert("Mot de passe modifié avec succès");

            this.router.navigate(['/profile']);

          });

        });

      },
      error:()=>{
        this.resetError="Code invalide";
      }
    });

  }

}