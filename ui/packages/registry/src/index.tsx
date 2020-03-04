import React from "react";
import ReactDOM from "react-dom";
import "@patternfly/react-core/dist/styles/base.css";
import App from './app/app';
import './app/app.css';
import './config.js';
import './version.js';

if (process.env.NODE_ENV !== "production") {
    // tslint:disable-next-line
    const axe = require("react-axe");
    axe(React, ReactDOM, 1000);
}

ReactDOM.render(<App />, document.getElementById("root") as HTMLElement);
