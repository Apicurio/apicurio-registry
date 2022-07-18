import React from "react";
import ReactDOM from "react-dom";
import App from "./app/app";
import { Services } from "./services";

const renderApp = () =>
  ReactDOM.render(<App />, document.getElementById("root") as HTMLElement);
Services.getAuthService().authenticateAndRender(renderApp);
