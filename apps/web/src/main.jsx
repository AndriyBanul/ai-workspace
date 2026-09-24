import React from 'react';
import { createRoot } from 'react-dom/client';
import { MantineProvider } from '@mantine/core';
import { Button, Paper, Text, Title } from '@mantine/core';
import App from './App.jsx';
import { cssVariablesResolver, theme } from './design/theme.js';
import '@mantine/core/styles.css';
import './design/base.css';

class ErrorBoundary extends React.Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <main style={{ maxWidth: 480, margin: '15vh auto', padding: 24 }}><Paper p="xl"><Title order={1}>Something went wrong.</Title><Text c="dimmed" my="lg">Reload to reconnect to your workspace.</Text><Button onClick={() => location.reload()}>Reload</Button></Paper></main> : this.props.children; }
}

createRoot(document.getElementById('root')).render(<React.StrictMode><MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver}><ErrorBoundary><App/></ErrorBoundary></MantineProvider></React.StrictMode>);
