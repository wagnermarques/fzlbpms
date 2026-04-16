import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FzlbpmsContainersStacksView } from './fzlbpms-containers-stacks-view';

describe('FzlbpmsContainersStacksView', () => {
  let component: FzlbpmsContainersStacksView;
  let fixture: ComponentFixture<FzlbpmsContainersStacksView>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FzlbpmsContainersStacksView]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FzlbpmsContainersStacksView);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
