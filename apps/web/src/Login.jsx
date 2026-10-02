import React, { useState } from 'react';
import { Button, Group, Paper, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { IconArrowRight } from '@tabler/icons-react';
import { basicAuth, createApi } from './api.js';
import { Notice, useAction } from './components.jsx';
import { Brand, SourceIcon } from './design/shared.jsx';
import s from './design/workspace.module.css';

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

  return <div className={s.login}><section className={s.loginStory}><Brand light/><div><Text className={s.loginEyebrow}>YOUR IDEAS HAVE COMPANY.</Text><h1>Bring the pieces.<br/><em>Find the picture.</em></h1><Text c="#bed0cc" size="lg" maw={430} lh={1.8}>Documents, conversations, images, and videos. One place to understand what they mean together.</Text><div className={s.loginCards}>{['Document','Audio','Image'].map((type,index) => <div key={type} style={{transform:'rotate(' + (index === 1 ? 4 : -4) + 'deg) translateY(' + (index === 1 ? -12 : 0) + 'px)'}}><SourceIcon type={type}/><Text c="white" mt="lg" fw={600}>{['A useful idea','A new perspective','A different angle'][index]}</Text><div className={s.loginCardLines}><i/><i/><i/></div></div>)}</div></div><Text size="sm" c="#bed0cc">Gather. Understand. Create.</Text></section>
    <main className={s.loginForm}><Paper withBorder={false} bg="transparent" p={0}><Text className={s.eyebrow}>AI WORKSPACE</Text><Title order={1} mt="sm">{register ? 'Make room for your ideas.' : 'Good to have you back.'}</Title><Text c="dimmed" mt="sm" mb={30}>{register ? 'Create your personal workspace.' : 'Sign in and pick up where you left off.'}</Text>
      <form onSubmit={submit}><Stack gap="md">{register && <TextInput name="displayName" label="Your name" placeholder="Alex Chen" autoComplete="name" disabled={action.busy}/>}<TextInput name="email" type="email" label="Email address" placeholder="you@example.com" autoComplete="username" required disabled={action.busy}/><PasswordInput name="password" label="Password" placeholder={register ? 'At least 12 characters' : 'Enter your password'} autoComplete={register ? 'new-password' : 'current-password'} required minLength={register ? 12 : undefined} maxLength={72} disabled={action.busy}/>{register && <Text size="xs" c="dimmed">Use at least 12 characters, up to 72 UTF-8 bytes.</Text>}<Notice error={action.error}/><Button type="submit" rightSection={<IconArrowRight size={17}/>} loading={action.busy}>{register ? 'Create account' : 'Sign in'}</Button></Stack></form>
      <Group gap={5} mt="lg"><Text size="sm" c="dimmed">{register ? 'Already have an account?' : 'New to AI Workspace?'}</Text><Button size="compact-sm" variant="subtle" onClick={() => setRegister(current => !current)}>{register ? 'Sign in' : 'Create an account'}</Button></Group><Text size="xs" c="dimmed" mt={35} lh={1.8}>Credentials stay in memory and are cleared when you sign out or reload.</Text>
    </Paper><Text className={s.loginFooter}>A little context. A lot more possibility.</Text></main></div>;
}
