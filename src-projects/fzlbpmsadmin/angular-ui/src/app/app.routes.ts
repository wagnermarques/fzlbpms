import { Routes } from '@angular/router';
import { ViewHome } from './components/views/view-home/view-home';
import { ViewConfig } from './components/views/view-config/view-config';
import { ViewBase }   from './components/views/view-base/view-base';

import { FzlbpmsHomeView } from './components/views/fzlbpms-home-view/fzlbpms-home-view';
import { FzlbpmsContainersView } from './components/views/fzlbpms-containers-view/fzlbpms-containers-view';
import { FzlbpmsContainersStacksView } from './components/views/fzlbpms-containers-stacks-view/fzlbpms-containers-stacks-view';
import { DesktopHomeView } from './components/views/desktop-home-view/desktop-home-view'
import { AppsHomeView } from './components/views/apps-home-view/apps-home-view';
import { MoodleInstallView } from './components/views/moodle-install-view/moodle-install-view';
import { KeycloakView } from './components/views/keycloak-view/keycloak-view';
import { ContainerDetailsView } from './components/views/container-details-view/container-details-view';
import { LoginView } from './components/views/login-view/login-view';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [

    { path: 'login', component: LoginView },

    { path: '',                            component: ViewHome,                    canActivate: [authGuard] },
    { path: 'baseview',                    component: ViewBase,                    canActivate: [authGuard] },
    { path: 'configs',                     component: ViewConfig,                  canActivate: [authGuard] },
    { path: 'fzlbpmshomevew',             component: FzlbpmsHomeView,             canActivate: [authGuard] },
    { path: 'appshomeview',               component: AppsHomeView,                canActivate: [authGuard] },
    { path: 'moodle-install',             component: MoodleInstallView,           canActivate: [authGuard] },
    { path: 'desktophomeview',            component: DesktopHomeView,             canActivate: [authGuard] },
    { path: 'fzlbpms-containers-vew',    component: FzlbpmsContainersView,       canActivate: [authGuard] },
    { path: 'fzlbpms-containers-stacks-view', component: FzlbpmsContainersStacksView, canActivate: [authGuard] },
    { path: 'keycloak-view',              component: KeycloakView,                canActivate: [authGuard] },
    { path: 'container-details/:id',      component: ContainerDetailsView,        canActivate: [authGuard] },
];
