import React, { useEffect, useState } from 'react';
import { Icon, useAction, size } from './components.jsx';
import DeleteSourceConfirmation from './library/DeleteSourceConfirmation.jsx';
import SourceDetails from './library/SourceDetails.jsx';
import SourceList from './library/SourceList.jsx';
import SourceResult from './library/SourceResult.jsx';
import SourceUploadPanel from './library/SourceUploadPanel.jsx';
import WebPageImport from './library/WebPageImport.jsx';

export default function Library({ api, workspace, addJob }) {
  const [files, setFiles] = useState([]);
  const [showUpload, setShowUpload] = useState(false);
  const [result, setResult] = useState(null);
  const [detail, setDetail] = useState(null);
  const [recovery, setRecovery] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const load = useAction();
  const sourceAction = useAction();

  const refresh = () => load.run(async () => setFiles(await api(`/workspaces/${workspace.id}/sources`)));

  useEffect(() => {
    const controller = new AbortController();
    api(`/workspaces/${workspace.id}/sources`, { signal: controller.signal })
      .then(setFiles)
      .catch(error => {
        if (error.name !== 'AbortError') load.run(() => { throw error; });
      });
    return () => controller.abort();
  }, [api, workspace.id]);

  async function processed(response) {
    setResult(response);
    await refresh();
  }

  function openSource(file) {
    return sourceAction.run(async () => {
      const [sourceDetail, recoveryDetail] = await Promise.all([
        api(`/workspaces/${workspace.id}/sources/${file.id}`),
        api(`/workspaces/${workspace.id}/sources/${file.id}/recovery`),
      ]);
      setDetail(sourceDetail);
      setRecovery(recoveryDetail);
    });
  }

  function reprocessSource(file) {
    return sourceAction.run(async () => {
      const job = await api(`/workspaces/${workspace.id}/sources/${file.id}/reprocess`, { method: 'POST' });
      addJob(job);
      await refresh();
    });
  }

  function deleteSource() {
    return sourceAction.run(async () => {
      await api(`/workspaces/${workspace.id}/sources/${confirm.id}`, { method: 'DELETE' });
      setConfirm(null);
      setDetail(null);
      setRecovery(null);
      await refresh();
    });
  }

  function closeDetails() {
    setDetail(null);
    setRecovery(null);
  }

  return <>
    <section className="hero">
      <div>
        <span className="tag">A GOOD PLACE TO START</span>
        <h2>Give your ideas<br/>something to build on.</h2>
        <p>Add documents, media, or a web page.<br/>Your workspace makes the connections.</p>
        <button className="primary" onClick={() => setShowUpload(current => !current)}><Icon name="plus" size={18}/>Add sources</button>
      </div>
      <div className="paper-stack" aria-hidden="true">
        <div className="paper back"/><div className="paper"><span className="paper-kicker">YOUR KNOWLEDGE</span><Icon name="file" size={35}/><div className="paper-lines"><i/><i/><i/></div><span className="paper-label">Everything, in context.</span></div><span className="floating-star"><Icon name="studio" size={32}/></span>
      </div>
    </section>
    <div className="stats">
      <div><span>Sources</span><strong>{files.length.toString().padStart(2, '0')}</strong></div>
      <div><span>Ready to explore</span><strong>{files.filter(file => file.status === 'PROCESSED').length.toString().padStart(2, '0')}</strong></div>
      <div><span>Needs attention</span><strong>{files.filter(file => file.status === 'FAILED').length.toString().padStart(2, '0')}</strong></div>
      <div><span>Space used</span><strong>{size(files.reduce((total, file) => total + file.sizeBytes, 0))}</strong></div>
    </div>
    {showUpload && <SourceUploadPanel api={api} workspaceId={workspace.id} addJob={addJob} onProcessed={processed}/>}
    <WebPageImport api={api} workspaceId={workspace.id} onProcessed={processed}/>
    <SourceResult result={result} onDismiss={() => setResult(null)}/>
    <SourceList
      files={files}
      busy={load.busy || sourceAction.busy}
      error={load.error || sourceAction.error}
      onRefresh={refresh}
      onOpen={openSource}
      onReprocess={reprocessSource}
      onDelete={setConfirm}
    />
    <DeleteSourceConfirmation
      source={confirm}
      busy={sourceAction.busy}
      error={sourceAction.error}
      onCancel={() => setConfirm(null)}
      onConfirm={deleteSource}
    />
    <SourceDetails source={detail} recovery={recovery} onClose={closeDetails}/>
  </>;
}
