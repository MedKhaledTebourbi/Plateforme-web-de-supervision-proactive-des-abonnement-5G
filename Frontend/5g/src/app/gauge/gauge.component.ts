import { Component, Input, OnChanges } from '@angular/core';
import { DecimalPipe } from '@angular/common';
@Component({
  selector: 'app-gauge',
  templateUrl: './gauge.component.html',
  styleUrls: ['./gauge.component.css']
})
export class GaugeComponent implements OnChanges {
constructor(private decimalPipe: DecimalPipe) {
 
}
@Input() value = 0;   // 0-100
  @Input() label = '';

  arcPath = '';
  color = '#1D9E75';

  ngOnChanges(): void {
    this.arcPath = this.buildArc(Math.min(100, Math.max(0, this.value)));
    this.color = this.value >= 95 ? '#E24B4A'
               : this.value >= 80 ? '#EF9F27'
               : this.value >= 60 ? '#BA7517'
               : '#1D9E75';
  }

  private buildArc(pct: number): string {
    // Arc de 0° à 180° (demi-cercle)
    const angle = (pct / 100) * Math.PI;
    const cx = 60, cy = 65, r = 55;
    const x = cx + r * Math.cos(Math.PI - angle);
    const y = cy - r * Math.sin(Math.PI - angle);
    const large = angle > Math.PI / 2 ? 1 : 0;
    return `M10,65 A55,55 0 ${large},1 ${x.toFixed(2)},${y.toFixed(2)}`;
  }
}
