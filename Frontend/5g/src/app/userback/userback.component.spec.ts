import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserbackComponent } from './userback.component';

describe('UserbackComponent', () => {
  let component: UserbackComponent;
  let fixture: ComponentFixture<UserbackComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [UserbackComponent]
    });
    fixture = TestBed.createComponent(UserbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
