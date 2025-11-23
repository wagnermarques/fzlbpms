import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AppsHomeView } from './apps-home-view';

describe('AppsHomeView', () => {
  let component: AppsHomeView;
  let fixture: ComponentFixture<AppsHomeView>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppsHomeView]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AppsHomeView);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
