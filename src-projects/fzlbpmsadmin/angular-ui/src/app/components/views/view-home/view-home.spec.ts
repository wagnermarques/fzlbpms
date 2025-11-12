import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewHome } from './view-home';

describe('ViewHome', () => {
  let component: ViewHome;
  let fixture: ComponentFixture<ViewHome>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewHome]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewHome);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
