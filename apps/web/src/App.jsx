import React, { useState } from 'react';
import Login from './Login.jsx';
import WorkspaceApp from './WorkspaceApp.jsx';

export default function App() {
  const [session, setSession] = useState(null);

  return session
    ? <WorkspaceApp session={session} logout={() => setSession(null)}/>
    : <Login onLogin={setSession}/>;
}
