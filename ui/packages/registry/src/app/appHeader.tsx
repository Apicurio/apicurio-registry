import React from 'react';
import {Avatar, Brand, PageHeader } from '@patternfly/react-core';
import {AppToolbar} from './appToolbar';
import brandImg from '../../assets/images/apicurio_logo_darkbkg_200px.png';

const showNavToogle: boolean = true;

export const AppHeader: React.FunctionComponent<any> = (props) => {
    return (<PageHeader
        logo={<Brand src={ brandImg } alt="Apicurio" />}
        toolbar={AppToolbar}
        showNavToggle = {showNavToogle}
      />);
}

export default AppHeader;
