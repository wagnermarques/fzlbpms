import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatExpansionModule } from '@angular/material/expansion';

@Component({
  selector: 'app-view-base',
  imports: [
    MatTabsModule,
    MatExpansionModule
  ],
  templateUrl: './view-base.html',
  styleUrl: './view-base.css',
})
export class ViewBase {

}
