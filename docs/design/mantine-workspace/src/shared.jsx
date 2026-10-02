import React from 'react';
import { Badge, Group, Text, Title } from '@mantine/core';
import { IconFileText, IconHeadphones, IconPhoto, IconMovie, IconBrandYoutube, IconWorld, IconCheck, IconClock, IconAlertCircle, IconSparkles } from '@tabler/icons-react';
import s from './workspace.module.css';

const types = { Document: IconFileText, Audio: IconHeadphones, Image: IconPhoto, Video: IconMovie, YouTube: IconBrandYoutube, 'Web page': IconWorld };
export function SourceIcon({ type, large = false }) {
  const Icon = types[type] || IconFileText;
  return <span className={`${s.sourceIcon} ${large ? s.sourceIconLarge : ''}`} data-kind={type}><Icon size={large ? 26 : 20} stroke={1.6}/></span>;
}
export function Status({ value }) {
  const ready = value === 'Ready' || value === 'Completed';
  const waiting = value === 'Processing' || value === 'In progress';
  const Icon = ready ? IconCheck : waiting ? IconClock : IconAlertCircle;
  return <Badge variant="light" color={ready ? 'workspace' : waiting ? 'blue' : 'orange'} radius="sm" leftSection={<Icon size={12}/>}>{value}</Badge>;
}
export function PageTitle({ eyebrow, title, description, children }) {
  return <div className={s.pageHeading}><div><Text className={s.eyebrow}>{eyebrow}</Text><Title order={1}>{title}</Title><Text c="dimmed" mt={8}>{description}</Text></div>{children}</div>;
}
export function Brand({ light = false }) {
  return <Group gap={10} wrap="nowrap"><span className={s.brandMark}><IconSparkles size={23} stroke={1.6}/></span><Text fw={650} size="lg" c={light ? 'white' : undefined}>AI Workspace<span className={s.brandPeriod}>.</span></Text></Group>;
}
