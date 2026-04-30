import { TestBed } from '@angular/core/testing';

import { SaturationService } from './saturation.service';

describe('SaturationService', () => {
  let service: SaturationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SaturationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
