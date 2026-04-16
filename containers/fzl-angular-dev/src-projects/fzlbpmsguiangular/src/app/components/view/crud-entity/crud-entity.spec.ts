import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CrudEntity } from './crud-entity';

describe('CrudEntity', () => {
  let component: CrudEntity;
  let fixture: ComponentFixture<CrudEntity>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CrudEntity]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CrudEntity);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
