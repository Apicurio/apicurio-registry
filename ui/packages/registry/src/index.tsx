import React from "react";
import ReactDOM from "react-dom";
import "@patternfly/react-core/dist/styles/base.css";
import App from './app/app';
import './app/app.css';
import './config.js';
import Keycloak from 'keycloak-js';
import './version.js';

// Handle login via keycloak

debugger;
const keycloak = Keycloak();
keycloak.init({onLoad: 'login-required'}).success((authenticated: any) => {
    if (authenticated) {
        (window as any).keycloak = keycloak;
        if (process.env.NODE_ENV !== "production") {
          // tslint:disable-next-line
          const axe = require("react-axe");
          axe(React, ReactDOM, 1000);
        }
        
        ReactDOM.render(<App />, document.getElementById("root") as HTMLElement);
    }
}).error(() => {
    alert('Failed to initialize authentication subsystem.');
});


