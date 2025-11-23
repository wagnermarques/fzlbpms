import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DesktopHomeView } from './desktop-home-view';

describe('DesktopHomeView', () => {
  let component: DesktopHomeView;
  let fixture: ComponentFixture<DesktopHomeView>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DesktopHomeView]
    })
    .compileComponents();

    fixture = TestBed.createComponent(DesktopHomeView);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
