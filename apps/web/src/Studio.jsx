import React, { useState } from 'react';
import { Icon } from './components.jsx';
import AnalyzeMedia from './studio/AnalyzeMedia.jsx';
import GenerateTool from './studio/GenerateTool.jsx';
import { studioTools } from './studio/tools.js';

export default function Studio({ api, workspace }) {
  const [tool, setTool] = useState('image');

  return <>
    <div className="studio-tabs" role="tablist" aria-label="Studio tools">
      {Object.entries(studioTools).map(([key, value]) => <button role="tab" aria-selected={tool === key} key={key} className={tool === key ? 'active' : ''} onClick={() => setTool(key)}><Icon name="studio"/>{value.title}</button>)}
      <button role="tab" aria-selected={tool === 'analyze'} className={tool === 'analyze' ? 'active' : ''} onClick={() => setTool('analyze')}><Icon name="search"/>Analyze media</button>
    </div>
    {tool === 'analyze'
      ? <AnalyzeMedia key={workspace?.id} api={api} workspace={workspace}/>
      : <GenerateTool key={tool} api={api} config={studioTools[tool]}/>}
  </>;
}
