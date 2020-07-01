import React from "react";
import ReactDOM from "react-dom";
import "@patternfly/react-core/dist/styles/base.css";
import App from "./app/app";
import "./app/app.css";
import * as Keycloak from 'keycloak-js'

//keycloak init options
let initOptions = {
    url: 'http://127.0.0.1:8090/auth', realm: 'registry', clientId: 'registry-ui', onLoad: 'login-required'
}

let keycloak = Keycloak(initOptions);


keycloak.init({ onLoad: initOptions.onLoad }).success((auth) => {

    if (!auth) {
        window.location.reload();
    } else {
        console.info("Authenticated");
    }

    //React Render
    ReactDOM.render(<App />, document.getElementById('root'));

    localStorage.setItem("react-token", keycloak.token);
    localStorage.setItem("react-refresh-token", keycloak.refreshToken);

    setTimeout(() => {
        keycloak.updateToken(70).success((refreshed) => {
            if (refreshed) {
                console.debug('Token refreshed' + refreshed);
            } else {
                console.warn('Token not refreshed, valid for '
                    + Math.round(keycloak.tokenParsed.exp + keycloak.timeSkew - new Date().getTime() / 1000) + ' seconds');
            }
        }).error(() => {
            console.error('Failed to refresh token');
        });


    }, 60000)

}).error(() => {
    console.error("Authenticated Failed");
});