import React, { useState } from 'react';
import { Group, SegmentedControl } from '@mantine/core';
import { IconMovie, IconPhoto, IconSearch, IconVolume } from '@tabler/icons-react';
import AnalyzeMedia from './studio/AnalyzeMedia.jsx';
import GenerateTool from './studio/GenerateTool.jsx';
import { studioTools } from './studio/tools.js';
import { PageTitle } from './design/shared.jsx';
import s from './design/workspace.module.css';

const choices = [['image', 'Image', IconPhoto], ['video', 'Video', IconMovie], ['speech', 'Speech', IconVolume], ['analyze', 'Analyze media', IconSearch]];

export default function Studio({ api, workspace }) {
  const [tool, setTool] = useState('image');
  return <>
    <PageTitle eyebrow="MAKE ROOM FOR AN IDEA" title="Your creative studio." description="Create something new, or uncover what’s already there."/>
    <SegmentedControl className={s.studioSwitch} aria-label="Studio tools" value={tool} onChange={setTool} data={choices.map(([value, label, Icon]) => ({value, label:<Group gap={8} justify="center"><Icon size={17}/><span>{label}</span></Group>}))}/>
    {tool === 'analyze'
      ? <AnalyzeMedia key={workspace?.id} api={api} workspace={workspace}/>
      : <GenerateTool key={tool} api={api} config={studioTools[tool]}/>}
  </>;
}
