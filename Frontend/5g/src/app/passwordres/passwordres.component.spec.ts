import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PasswordresComponent } from './passwordres.component';

describe('PasswordresComponent', () => {
  let component: PasswordresComponent;
  let fixture: ComponentFixture<PasswordresComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [PasswordresComponent]
    });
    fixture = TestBed.createComponent(PasswordresComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
