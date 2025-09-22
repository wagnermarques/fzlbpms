import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FzlLayoutMain } from './fzlcomponents/components/fzl-layout-main/fzl-layout-main';


@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    FzlLayoutMain 
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected title = 'guiaregional';
}
