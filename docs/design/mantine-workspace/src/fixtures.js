// Fictional fixtures for design review only. No requests are sent to the application.
export const sources = [
  { id: 'brief', name: 'Atlas launch brief.pdf', type: 'Document', detail: 'PDF · 2.4 MB', status: 'Ready', date: 'Today, 10:42', context: 'Positioning, audience, and launch milestones', location: 'Page 4' },
  { id: 'interview', name: 'Customer interview — Maya', type: 'Audio', detail: 'MP3 · 8.1 MB', status: 'Ready', date: 'Today, 09:18', context: 'Customer needs and onboarding friction', location: '02:14–02:38' },
  { id: 'demo', name: 'Product walkthrough', type: 'YouTube', detail: 'YouTube · remote', status: 'Ready', date: 'Yesterday', context: 'A guided tour of the Atlas experience', location: '01:32–01:58' },
  { id: 'brand', name: 'Brand direction.png', type: 'Image', detail: 'PNG · 1.6 MB', status: 'Ready', date: 'Yesterday', context: 'Colour palette and visual references' },
  { id: 'market', name: 'Market research notes', type: 'Web page', detail: 'Web page · remote', status: 'Needs attention', date: 'Yesterday', context: 'The website could not be reached. Automatic retry is scheduled.' },
  { id: 'recording', name: 'Team kickoff.mp4', type: 'Video', detail: 'MP4 · 12.8 MB', status: 'Processing', date: 'Today, 10:46', context: 'Understanding the video and its conversation' },
];
export const passages = [
  { time: '01:58', text: 'The first session needs to feel useful immediately. People should see what they can ask before they write their own question.' },
  { time: '02:14', text: 'What would help me most is three or four suggested questions based on what I just uploaded. I do not want to start with a blank page.', active: true },
  { time: '02:38', text: 'And when I get an answer, let me see where it came from. If it is a recording, tell me which part of the conversation supports it.' },
  { time: '03:06', text: 'A clear status matters too. I need to know whether my source is ready before I ask about it.' },
];
