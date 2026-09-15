import React from 'react';
import { Notice, Submit, useAction } from '../components.jsx';

export default function WebPageImport({ api, workspaceId, onProcessed }) {
  const action = useAction();

  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const url = new FormData(form).get('url');
    await action.run(async () => {
      const response = await api('/documents/web-page', { method: 'POST', json: { workspaceId, url } });
      form.reset();
      await onProcessed(response);
    });
  }

  return <section className="card web-import">
    <div><h3>Bring the web into your workspace</h3><p>Import an article or text page by URL. It will appear below with the same lifecycle as uploaded media.</p></div>
    <form onSubmit={submit}>
      <label className="sr-only" htmlFor="web-url">Web page URL</label>
      <input id="web-url" name="url" type="url" pattern="https?://.*" placeholder="https://example.com/article" required disabled={action.busy}/>
      <Submit busy={action.busy}>Import</Submit>
    </form>
    <Notice error={action.error}/>
  </section>;
}
