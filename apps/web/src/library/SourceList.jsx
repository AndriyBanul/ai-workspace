import React, { useState } from 'react';
import { formats } from '../api.js';
import { Badge, Empty, Icon, Notice, size } from '../components.jsx';

export default function SourceList({ files, busy, error, onRefresh, onOpen, onReprocess, onDelete }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('ALL');
  const shown = files.filter(file =>
    (filter === 'ALL' || file.sourceType === filter)
    && file.originalFilename.toLowerCase().includes(query.toLowerCase()));

  return <section className="card">
    <div className="section-heading"><h2>Your sources <span className="count">{files.length}</span></h2><button className="text-button" onClick={onRefresh} disabled={busy}>Refresh sources</button></div>
    <div className="toolbar">
      <label className="search"><Icon name="search"/><input aria-label="Search sources" placeholder="Find a source…" value={query} onChange={event => setQuery(event.target.value)}/></label>
      <select aria-label="Filter sources" value={filter} onChange={event => setFilter(event.target.value)}>
        <option value="ALL">All types</option>
        {Object.keys(formats).map(type => <option key={type} value={type.toUpperCase()}>{formats[type].title}</option>)}
        <option value="WEB_PAGE">Web page</option><option value="YOUTUBE">YouTube</option>
      </select>
    </div>
    <Notice error={error}/>
    {!shown.length
      ? <Empty title={files.length ? 'No matching sources' : 'Your library is ready for its first source'}>Upload a file or import a web page to start exploring.</Empty>
      : <div className="table-scroll"><table>
        <thead><tr><th>Name</th><th>Type</th><th>Size</th><th>Status</th><th>Actions</th></tr></thead>
        <tbody>{shown.map(file => <tr key={file.id}>
          <td><button className="file-name" onClick={() => onOpen(file)}><span className="file-icon"><Icon name="file"/></span><span>{file.originalFilename}<small>{new Date(file.createdAt).toLocaleDateString()}</small></span></button></td>
          <td>{file.sourceType.toLowerCase().replace('_', ' ')}</td>
          <td>{file.sourceUrl ? 'Remote' : size(file.sizeBytes)}</td>
          <td><Badge status={file.status}/></td>
          <td><button className="text-button" disabled={busy || file.status === 'PROCESSING'} onClick={() => onReprocess(file)}>Reprocess</button> <button className="text-button danger" onClick={() => onDelete(file)}>Delete</button></td>
        </tr>)}</tbody>
      </table></div>}
  </section>;
}
