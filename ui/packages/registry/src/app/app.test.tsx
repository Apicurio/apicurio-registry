import React from 'react';
import App from './app';
import { mount, shallow } from 'enzyme';
import { Alert, Button } from '@patternfly/react-core';

describe('App tests', () => {
  test('should render default App component', () => {
    const view = shallow(<App />);
    expect(view).toMatchSnapshot();
  });

  it('should render a dismiss button', () => {
    const wrapper = mount(<App />);
    const button = wrapper.find(Button);
    expect(button.exists()).toBe(true)
  });

  it('should hide the alert when clicking the dismiss button', () => {
    const wrapper = mount(<App />);
    const button = wrapper.find(Button);
    expect(wrapper.find(Alert).exists()).toBe(true);
    button.simulate('click');
    expect(wrapper.find(Alert).exists()).toBe(false);
  });
});
