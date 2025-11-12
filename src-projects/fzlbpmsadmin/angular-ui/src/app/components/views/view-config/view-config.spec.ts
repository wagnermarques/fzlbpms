import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewConfig } from './view-config';

describe('ViewConfig', () => {
  let component: ViewConfig;
  let fixture: ComponentFixture<ViewConfig>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewConfig]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewConfig);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
