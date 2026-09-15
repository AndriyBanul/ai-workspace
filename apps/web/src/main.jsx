import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import './styles.css';
import './overrides.css';
import './transcript.css';

class ErrorBoundary extends React.Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? <main className="auth"><h1>Something went wrong.</h1><p>Reload to reconnect to your workspace.</p><button onClick={() => location.reload()}>Reload</button></main> : this.props.children; }
}

createRoot(document.getElementById('root')).render(<React.StrictMode><ErrorBoundary><App/></ErrorBoundary></React.StrictMode>);
