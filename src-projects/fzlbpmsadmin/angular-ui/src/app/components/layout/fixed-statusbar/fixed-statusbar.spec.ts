import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FixedStatusbar } from './fixed-statusbar';

describe('FixedStatusbar', () => {
  let component: FixedStatusbar;
  let fixture: ComponentFixture<FixedStatusbar>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedStatusbar]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FixedStatusbar);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
