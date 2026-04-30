import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = '5g';
  timeout: any;
SESSION_TIMEOUT = 15 * 60 * 1000;

ngOnInit() {
  this.startWatching();
}

startWatching() {
  this.resetTimer();

  window.addEventListener('mousemove', () => this.resetTimer());
  window.addEventListener('keydown', () => this.resetTimer());
  window.addEventListener('click', () => this.resetTimer());
}

resetTimer() {
  clearTimeout(this.timeout);

  this.timeout = setTimeout(() => {
    this.logout();
  }, this.SESSION_TIMEOUT);
}

logout() {
  localStorage.clear();
  window.location.href = '/login';
}
}
