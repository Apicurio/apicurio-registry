import React from "react";
import ReactDOM from "react-dom";
import "@patternfly/react-core/dist/styles/base.css";
import App from "./app/app";
import "./app/app.css";

ReactDOM.render(<App />, document.getElementById("root") as HTMLElement);
