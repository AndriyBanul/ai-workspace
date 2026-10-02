import React, { useState } from 'react';
import { Alert, Paper, Text, Title } from '@mantine/core';
import { IconInfoCircle } from '@tabler/icons-react';

export function Notice({ error }) {
  return error ? <Alert role="alert" color="red" icon={<IconInfoCircle size={18}/>} mt="md">
    {error.message || error}
    {error.code && <Text size="xs">{error.code}</Text>}
  </Alert> : null;
}

export function Empty({ title, children }) {
  return <Paper p="xl" ta="center"><Title order={3}>{title}</Title><Text c="dimmed" mt="sm">{children}</Text></Paper>;
}

export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const run = async action => {
    setBusy(true);
    setError(null);
    try { return await action(); }
    catch (caught) { if (caught.name !== 'AbortError') setError(caught); }
    finally { setBusy(false); }
  };
  return { busy, error, run };
}

export const size = bytes => bytes < 1024 ? bytes + ' B' : bytes < 1024 ** 2 ? (bytes / 1024).toFixed(1) + ' KB' : (bytes / 1024 ** 2).toFixed(1) + ' MB';
export const date = value => value ? new Date(value).toLocaleString() : '—';
