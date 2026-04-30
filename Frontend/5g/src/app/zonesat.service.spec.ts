import { TestBed } from '@angular/core/testing';

import { ZonesatService } from './zonesat.service';

describe('ZonesatService', () => {
  let service: ZonesatService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ZonesatService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
