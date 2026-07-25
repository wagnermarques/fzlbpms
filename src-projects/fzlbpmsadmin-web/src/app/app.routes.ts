import { Routes } from '@angular/router';
import { ViewHome } from './components/views/view-home/view-home';
import { AppsHomeView } from './components/views/apps-home-view/apps-home-view';

export const routes: Routes = [
  { path: '', component: ViewHome },
  { path: 'appshomeview', component: AppsHomeView },
];
