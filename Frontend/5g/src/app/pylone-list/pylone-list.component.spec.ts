import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PyloneListComponent } from './pylone-list.component';

describe('PyloneListComponent', () => {
  let component: PyloneListComponent;
  let fixture: ComponentFixture<PyloneListComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [PyloneListComponent]
    });
    fixture = TestBed.createComponent(PyloneListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
