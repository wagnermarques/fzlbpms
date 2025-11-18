import { Routes } from '@angular/router';
import { ViewHome } from './components/views/view-home/view-home';
import { ViewConfig } from './components/views/view-config/view-config';
import { ViewBase }   from './components/views/view-base/view-base';

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
    }

];
