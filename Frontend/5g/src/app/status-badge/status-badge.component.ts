import { Component, Input } from '@angular/core';
import { SaturationStatus } from './saturation-report.model';

@Component({
  selector: 'app-status-badge',
  templateUrl: './status-badge.component.html',
  styleUrls: ['./status-badge.component.css']
})
export class StatusBadgeComponent {
 @Input() status: SaturationStatus = 'NORMAL';

  get label(): string {
    const map: Record<SaturationStatus, string> = {
      NORMAL: 'Normal',
      ATTENTION: 'Attention',
      SATURE: 'Saturé',
      CRITIQUE: 'Critique',
    };
    return map[this.status];
  }
}
