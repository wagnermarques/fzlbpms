import { Routes } from '@angular/router';
import { ViewHome } from './components/views/view-home/view-home';
import { ViewConfig } from './components/views/view-config/view-config';

export const routes: Routes = [

    {
        path:'',
        component: ViewHome
    },
    {
        path:'config',
        component: ViewConfig
    }

];
