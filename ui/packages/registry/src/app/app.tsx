import React, { Component, ReactNode } from "react";
import {
  Page,
  PageSectionVariants,
  PageSection
} from "@patternfly/react-core";
import AppHeader from "./appHeader";
import AppSidebar from "./appSidebar";
import {BrowserRouter as Router, Route} from 'react-router-dom';
import * as Pages from './pages';
export default class App extends Component {
  public state = {
    activeMenuGroup: "",
    activeMenuGroupItem: ""
  };

  public render() {
    const { activeMenuGroup, activeMenuGroupItem } = this.state;

    const section = (
      <PageSection variant={PageSectionVariants.light}>
         <Route path='/' exact={true} component={Pages.Dashboard}/>
         <Route path='/dashboard' exact={true} component={Pages.Dashboard}/>
         <Route path='/apis' exact={true}  component={Pages.ViewApis}/>
         <Route path='/apis/create' exact={true} component={Pages.CreateAPI}/>
         <Route path='/apis/import' exact={true} component={Pages.ImportAPI}/>
         <Route path='/settings/profile' exact={true} component={Pages.UserProfile}/>
         <Route path='/settings/accounts' exact={true} component={Pages.LinkedAccounts}/>
         <Route path='/settings/validation' exact={true} component={Pages.Validations}/>
      </PageSection>
    );
    return (
       <Router>
        <Page
          isManagedSidebar={true}
          header={<AppHeader />}
          sidebar={
            <AppSidebar
              activeMenuGroup={activeMenuGroup}
              activeMenuGroupItem={activeMenuGroupItem}
              onSelect={this.onNavSelect}
            />
          }
        >
          {section}
        </Page>
        </Router>
    );
  }

  private onNavSelect = ({ groupId, itemId }: any) => {
    this.setState({
      activeMenuGroup: groupId,
      activeMenuGroupItem: itemId
    });
  };
}
