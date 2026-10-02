import React from 'react';
import { MantineProvider } from '@mantine/core';
import { render as testingRender } from '@testing-library/react';
import { cssVariablesResolver, theme } from './design/theme.js';

export function render(ui, options) {
  return testingRender(ui, {
    wrapper: ({ children }) => <MantineProvider env="test" theme={theme} cssVariablesResolver={cssVariablesResolver}>{children}</MantineProvider>,
    ...options,
  });
}
