import { TestBed } from '@angular/core/testing';

import { TicketPrioriteService } from './ticket-priorite.service';

describe('TicketPrioriteService', () => {
  let service: TicketPrioriteService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TicketPrioriteService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
