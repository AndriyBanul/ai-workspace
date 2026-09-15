import React from 'react';
import { Notice } from '../components.jsx';

export default function DeleteSourceConfirmation({ source, busy, error, onCancel, onConfirm }) {
  if (!source) return null;
  return <div className="confirmation" role="alert">
    <h3>Delete {source.originalFilename}?</h3>
    <p>This removes the source, its linked knowledge, and stored bytes when present.</p>
    <button onClick={onCancel}>Cancel</button>
    <button className="danger" disabled={busy} onClick={onConfirm}>Delete source</button>
    <Notice error={error}/>
  </div>;
}
