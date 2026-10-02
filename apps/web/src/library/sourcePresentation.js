export const typeLabel = value => ({ WEB_PAGE: 'Web page', YOUTUBE: 'YouTube' }[value] || (value ? value[0] + value.slice(1).toLowerCase() : 'Document'));
export const sourceName = source => source.originalFilename || source.sourceUrl || 'Untitled source';
