import { TestBed } from '@angular/core/testing';

import { ChantierBlockInterceptor } from './chantier-block.interceptor';

describe('ChantierBlockInterceptor', () => {
  beforeEach(() => TestBed.configureTestingModule({
    providers: [
      ChantierBlockInterceptor
      ]
  }));

  it('should be created', () => {
    const interceptor: ChantierBlockInterceptor = TestBed.inject(ChantierBlockInterceptor);
    expect(interceptor).toBeTruthy();
  });
});
