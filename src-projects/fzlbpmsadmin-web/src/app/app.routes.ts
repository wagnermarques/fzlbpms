import { Routes } from '@angular/router';
import { ViewHome } from './components/views/view-home/view-home';
import { AppsHomeView } from './components/views/apps-home-view/apps-home-view';
import { AuthCallbackView } from './components/views/auth-callback-view/auth-callback-view';
import { ProcessDiagramView } from './components/views/process-diagram-view/process-diagram-view';
import { UsersView } from './components/views/users-view/users-view';
import { adminGuard } from './services/admin-guard';

export const routes: Routes = [
  { path: '', component: ViewHome },
  { path: 'appshomeview', component: AppsHomeView },
  { path: 'auth-callback', component: AuthCallbackView },
  { path: 'process-diagram', component: ProcessDiagramView },
  { path: 'process-diagram/:key', component: ProcessDiagramView },
  { path: 'process-instances', component: ProcessDiagramView },
  { path: 'users', component: UsersView, canActivate: [adminGuard] },
];
