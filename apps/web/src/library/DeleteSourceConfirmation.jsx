import React from 'react';
import { Button, Group, Modal, Text } from '@mantine/core';
import { sourceName } from './sourcePresentation.js';

export default function DeleteSourceConfirmation({ source, busy, onClose, onConfirm }) {
  return <Modal opened={Boolean(source)} onClose={onClose} title={'Delete ' + sourceName(source || {}) + '?'}><Text mb="lg">This source and its searchable knowledge will be removed from the workspace.</Text><Group justify="flex-end"><Button variant="default" onClick={onClose}>Keep source</Button><Button color="red" loading={busy} onClick={onConfirm}>Delete source</Button></Group></Modal>;
}
