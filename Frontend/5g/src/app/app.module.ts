import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { UtilisateurComponent } from './utilisateur/utilisateur.component';
import { UnauthorizedComponent } from './auth/unauthorized/unauthorized.component';
import { RegisteComponent } from './registe/registe.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { ResetpassComponent } from './resetpass/resetpass.component';
import { CodeComponent } from './code/code.component';
import { UserbackComponent } from './userback/userback.component';
import { AuthInterceptor } from './auth.interceptor';
import { MapsComponent } from './maps/maps.component';
import { LoginhistoryComponent } from './loginhistory/loginhistory.component';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { DashboardChartComponent } from './dashboard-chart/dashboard-chart.component';
import { ZoneListComponent } from './zone-list/zone-list.component';
import { PyloneListComponent } from './pylone-list/pylone-list.component';
import { ProfileComponent } from './profile/profile.component';
import { PasswordresComponent } from './passwordres/passwordres.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

// NgRx imports
import { StoreModule } from '@ngrx/store';
import { EffectsModule } from '@ngrx/effects';
import { StoreDevtoolsModule } from '@ngrx/store-devtools';
import { StoreRouterConnectingModule } from '@ngrx/router-store';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TicketComponent } from './ticket/ticket.component';
import { ChantierFormComponent } from './chantier-form/chantier-form.component';
import { ToastrModule } from 'ngx-toastr';
import { ChantierListComponent } from './chantier-list/chantier-list.component';
import { StatusBadgeComponent } from './status-badge/status-badge.component';
import { GaugeComponent } from './gauge/gauge.component';
import { StatCardComponent } from './stat-card/stat-card.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { ZoneDetailComponent } from './zone-detail/zone-detail.component';
import { PredictionsComponent } from './predictions/predictions.component';
import { ReportComponent } from './report/report.component';
import { ResetComponent } from './reset/reset.component';


@NgModule({
  declarations: [
    AppComponent,
    UtilisateurComponent,
    UnauthorizedComponent,
    RegisteComponent,
    ResetpassComponent,
    CodeComponent,
    UserbackComponent,
    MapsComponent,
    LoginhistoryComponent,
    ZoneListComponent,
    PyloneListComponent,
    ProfileComponent,
    PasswordresComponent,
    TicketComponent,
    ChantierFormComponent,
    ChantierListComponent,
    StatusBadgeComponent,
    GaugeComponent,
    StatCardComponent,
    DashboardComponent,
    ZoneDetailComponent,
    PredictionsComponent,
    ReportComponent,
    ResetComponent,
    
    
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    AppRoutingModule,
    FormsModule,           // ✅ Nécessaire pour [(ngModel)]
    ReactiveFormsModule,    // ✅ Optionnel mais recommandé pour les formulaires complexes
    HttpClientModule ,
    CommonModule,
    DashboardChartComponent,
    NgbModule,
    ToastrModule.forRoot({
      timeOut: 3000,
      positionClass: 'toast-top-right',
      preventDuplicates: true,
      progressBar: true,
      closeButton: true
    }),
    DecimalPipe
    
    
    
  ],
  providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }, DatePipe, DecimalPipe],
  bootstrap: [AppComponent]
})
export class AppModule { }
