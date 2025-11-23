import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ViewBase } from './view-base';

describe('ViewBase', () => {
  let component: ViewBase;
  let fixture: ComponentFixture<ViewBase>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ViewBase]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ViewBase);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
