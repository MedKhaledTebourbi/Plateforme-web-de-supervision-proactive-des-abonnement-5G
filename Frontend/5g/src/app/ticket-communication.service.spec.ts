import { TestBed } from '@angular/core/testing';

import { TicketCommunicationService } from './ticket-communication.service';

describe('TicketCommunicationService', () => {
  let service: TicketCommunicationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TicketCommunicationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
