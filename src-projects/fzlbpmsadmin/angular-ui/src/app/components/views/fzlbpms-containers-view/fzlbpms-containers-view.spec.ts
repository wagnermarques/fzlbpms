import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FzlbpmsContainersView } from './fzlbpms-containers-view';

describe('FzlbpmsContainersView', () => {
  let component: FzlbpmsContainersView;
  let fixture: ComponentFixture<FzlbpmsContainersView>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FzlbpmsContainersView]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FzlbpmsContainersView);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
