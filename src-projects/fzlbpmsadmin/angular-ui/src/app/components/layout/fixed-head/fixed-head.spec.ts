import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FixedHead } from './fixed-head';

describe('FixedHead', () => {
  let component: FixedHead;
  let fixture: ComponentFixture<FixedHead>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedHead]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FixedHead);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
