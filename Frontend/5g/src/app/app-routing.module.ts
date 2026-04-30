import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UtilisateurComponent } from './utilisateur/utilisateur.component';
import { RegisteComponent } from './registe/registe.component';
import { ResetpassComponent } from './resetpass/resetpass.component';
import { CodeComponent } from './code/code.component';
import { UserbackComponent } from './userback/userback.component';
import { RoleGuard } from './guards/role.guard';
import { MapsComponent } from './maps/maps.component';
import { LoginhistoryComponent } from './loginhistory/loginhistory.component';
import { ZoneListComponent } from './zone-list/zone-list.component';
import { PyloneListComponent } from './pylone-list/pylone-list.component';
import { ProfileComponent } from './profile/profile.component';
import { PasswordresComponent } from './passwordres/passwordres.component';
import { TicketComponent } from './ticket/ticket.component';
import { ChantierListComponent } from './chantier-list/chantier-list.component';
import { ChantierFormComponent } from './chantier-form/chantier-form.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { StatCardComponent } from './stat-card/stat-card.component';
import { ZoneDetailComponent } from './zone-detail/zone-detail.component';
import { ReportComponent } from './report/report.component';
import { authGuard } from './auth/auth.guard';
import { GaugeComponent } from './gauge/gauge.component';
import { FirstLoginGuard } from './first-login.guard';

const routes: Routes = [

  // PUBLIC
  { path: 'login', component: UtilisateurComponent },
  { path: 'reset-password', component: ResetpassComponent },
  { path: 'code', component: CodeComponent },
  { path: 'reset', component: ResetpassComponent },
 

  // PROTECTED (TOUT LE RESTE)
  {
    path: '',
    canActivate: [authGuard, FirstLoginGuard],
    children: [
      { path: 'map', component: MapsComponent },
      { path: 'his', component: LoginhistoryComponent },
      { path: 'zone', component: ZoneListComponent },
      { path: 'pylone', component: PyloneListComponent },
      { path: 'profile', component: ProfileComponent },
      { path: 'pass', component: PasswordresComponent },
      { path: 'ticket', component: TicketComponent },
      { path: 'chantiers', component: ChantierFormComponent },
      { path: 'dashboard', component: DashboardComponent },
      { path: 'dashboard/stats', component: StatCardComponent },
      { path: 'zones/:id', component: ZoneDetailComponent },
      { path: 'Report', component: ReportComponent },
      { path: 'liste', component: ChantierListComponent },

      // ROLE BASED
      
      {
        path: 'register',
        component: RegisteComponent,
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] }
      },
       {
        path: 'history',
        component: LoginhistoryComponent,
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'userback',
        component: UserbackComponent,
        canActivate: [authGuard],
        data: { roles: ['ADMIN'] }
      }
    ]
  },

  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
