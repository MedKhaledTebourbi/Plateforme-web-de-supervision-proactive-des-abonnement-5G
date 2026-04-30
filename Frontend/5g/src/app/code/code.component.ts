import { Component } from '@angular/core';
import { UtilisateurService } from '../utilisateur-service.service';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-code',
  templateUrl: './code.component.html',
  styleUrls: ['./code.component.css']
})
export class CodeComponent {

  verificationCode: string = '';
  username: string = '';
  token: string = '';
  newPassword: string = '';
  resetError: string = '';
  resetMessage: string = '';

  constructor(
    private authService: UtilisateurService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.username = params['username'];
      this.token = params['token'];
      this.newPassword = params['newPassword'];

      console.log("USERNAME:", this.username);
      console.log("TOKEN:", this.token);
    });
  }

  handleResetButton() {

    if (!this.verificationCode) {
      this.resetError = "Veuillez saisir le code de vérification";
      return;
    }

    // 1️⃣ Vérifier le code
    this.authService.verifyResetCode(this.username, this.verificationCode)
      .subscribe({
        next: (res: any) => {

          console.log("Code valide");

          // 2️⃣ Si code OK → reset password
          this.authService.resetPassword(this.token, this.newPassword)
            .subscribe({
              next: () => {
                alert("Mot de passe réinitialisé avec succès !");
                this.router.navigate(['/login']);
              },
              error: (err) => {
                console.error(err);
                this.resetError = "Erreur lors de la réinitialisation";
              }
            });

        },
        error: (err) => {
          console.error(err);
          this.resetError = err.error || "Code invalide ou expiré";
        }
      });
  }
}
