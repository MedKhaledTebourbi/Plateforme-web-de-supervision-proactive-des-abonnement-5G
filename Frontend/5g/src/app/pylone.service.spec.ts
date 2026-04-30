import { TestBed } from '@angular/core/testing';

import { PyloneService } from './pylone.service';

describe('PyloneService', () => {
  let service: PyloneService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PyloneService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
