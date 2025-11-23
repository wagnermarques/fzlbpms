import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FzlbpmsHomeView } from './fzlbpms-home-view';

describe('FzlbpmsHomeView', () => {
  let component: FzlbpmsHomeView;
  let fixture: ComponentFixture<FzlbpmsHomeView>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FzlbpmsHomeView]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FzlbpmsHomeView);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
