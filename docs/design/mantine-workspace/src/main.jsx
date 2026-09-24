import React from 'react';
import { createRoot } from 'react-dom/client';
import { MantineProvider } from '@mantine/core';
import '@mantine/core/styles.css';
import './base.css';
import { theme, cssVariablesResolver } from './theme.js';
import App from './App.jsx';

createRoot(document.getElementById('root')).render(
  <React.StrictMode><MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver} forceColorScheme="light"><App /></MantineProvider></React.StrictMode>,
);
