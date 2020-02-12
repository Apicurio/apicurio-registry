import React, {Component} from 'react';
import {
  Grid, GridItem, GutterSize
} from "@patternfly/react-core";

export default class ApiEditor extends Component {

  public render() {
      return (<Grid gutter={GutterSize.md}>
          <GridItem span={12}>API Title</GridItem>
          <GridItem span={4}>
              Left Pane
          </GridItem>
          <GridItem span={8}>
              Right Pane
          </GridItem>
      </Grid>);
  }
}