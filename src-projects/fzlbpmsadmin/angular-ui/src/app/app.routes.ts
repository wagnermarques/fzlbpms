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


export const routes: Routes = [

    {
        path:'',
        component: ViewHome
    },

    {
        path:'baseview',
        component: ViewBase
    },
    {
        path:'configs',
        component: ViewConfig
    },
    {
        path:'fzlbpmshomevew',
        component: FzlbpmsHomeView
    },
    {
        path:'appshomeview',
        component: AppsHomeView
    },
    {
        path: 'moodle-install',
        component: MoodleInstallView
    },
    {
        path:'desktophomeview',
        component: DesktopHomeView

    },
    {
        path:'fzlbpms-containers-vew',
        component: FzlbpmsContainersView
    },
    {
        path:'fzlbpms-containers-stacks-view',
        component: FzlbpmsContainersStacksView                   
    }
];
