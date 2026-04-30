import { TestBed } from '@angular/core/testing';

import { GuideTechnicienService } from './guide-technicien.service';

describe('GuideTechnicienService', () => {
  let service: GuideTechnicienService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(GuideTechnicienService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
