import React, { useState } from 'react';
import { basicAuth, createApi } from './api.js';
import { Icon, Notice, Submit, useAction } from './components.jsx';

export default function Login({ onLogin }) {
  const [register, setRegister] = useState(false);
  const action = useAction();

  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = form.get('email').trim();
    const password = form.get('password');

    await action.run(async () => {
      if (register) {
        await createApi()('/auth/register', {
          method: 'POST',
          json: { email, password, displayName: form.get('displayName') },
        });
      }
      const authorization = basicAuth(email, password);
      const user = await createApi(authorization)('/auth/me');
      onLogin({ user, authorization });
    });
  }

  return <div className="login-shell">
    <section className="login-story">
      <a className="brand" href="#"><span className="brand-symbol"><Icon name="studio"/></span>AI Workspace</a>
      <div>
        <p className="eyebrow">A HOME FOR YOUR KNOWLEDGE</p>
        <h1>Bring it all together.<br/><em>Make something new.</em></h1>
        <p>Documents, conversations, images, and ideas.<br/>One thoughtful space to work with them.</p>
        <div className="orbit" aria-hidden="true">
          <span>DOCUMENTS</span><span>IMAGES</span><span>AUDIO</span><span>VIDEO</span><Icon name="studio" size={58}/>
        </div>
      </div>
      <small>Organize · Explore · Create</small>
    </section>
    <main className="auth">
      <p className="eyebrow">YOUR NEXT IDEA STARTS HERE</p>
      <h2>{register ? 'Create your account' : 'Welcome back'}</h2>
      <p>{register ? 'Set up your personal workspace.' : 'Sign in to continue your work.'}</p>
      <form onSubmit={submit}>
        <fieldset disabled={action.busy}>
          {register && <label>Your name<input name="displayName" autoComplete="name" placeholder="Alex Morgan"/></label>}
          <label>Email address<input name="email" type="email" required autoComplete="username" placeholder="you@example.com"/></label>
          <label>Password<input name="password" type="password" required minLength={register ? 12 : undefined} maxLength={72} autoComplete={register ? 'new-password' : 'current-password'}/></label>
          <Notice error={action.error}/>
          <Submit busy={action.busy}>{register ? 'Create account' : 'Sign in'}<Icon name="arrow"/></Submit>
        </fieldset>
      </form>
      <button className="text-button" onClick={() => setRegister(!register)}>
        {register ? 'Already have an account? Sign in' : 'New here? Create an account'}
      </button>
      <small className="muted">Credentials stay in memory and are cleared when you sign out or reload.</small>
    </main>
  </div>;
}
