import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MaterialModule } from './../../../material.module'; 

@Component({
  selector: 'app-fzl-layout-main',
  imports: [
    MaterialModule,
    RouterOutlet 
  ],
  templateUrl: './fzl-layout-main.html',
  styleUrl: './fzl-layout-main.css'
})
export class FzlLayoutMain {

}
