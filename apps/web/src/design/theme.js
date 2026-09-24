import { createTheme, Button, Paper, TextInput, Textarea, Select, Modal, Drawer } from '@mantine/core';

export const cssVariablesResolver = () => ({ variables: {}, light: { '--mantine-color-dimmed': '#65767a', '--mantine-color-text': '#20363c' }, dark: {} });

export const theme = createTheme({
  primaryColor: 'workspace',
  primaryShade: 7,
  colors: {
    workspace: ['#eaf7f4', '#ceeae3', '#a3d7cb', '#75c4b3', '#4ab19b', '#2c9c87', '#198675', '#146d60', '#11574d', '#0d443d'],
    ink: ['#f3f6f6', '#e4eaea', '#c5d1d2', '#a4b5b7', '#7e9599', '#627b80', '#4b6469', '#354e55', '#253d44', '#152c32'],
  },
  fontFamily: "'Segoe UI', -apple-system, BlinkMacSystemFont, Arial, sans-serif",
  headings: { fontFamily: "'Segoe UI', -apple-system, BlinkMacSystemFont, Arial, sans-serif", fontWeight: '650', sizes: { h1: { fontSize: '32px', lineHeight: '1.2' }, h2: { fontSize: '23px', lineHeight: '1.3' }, h3: { fontSize: '18px', lineHeight: '1.35' } } },
  defaultRadius: 'md',
  radius: { xs: '4px', sm: '6px', md: '10px', lg: '14px', xl: '20px' },
  spacing: { xs: '8px', sm: '12px', md: '16px', lg: '24px', xl: '32px' },
  fontSizes: { xs: '12px', sm: '13px', md: '14px', lg: '16px', xl: '20px' },
  shadows: { xs: '0 2px 8px #152c3206', sm: '0 8px 24px #152c320a', md: '0 18px 54px #152c3222' },
  components: {
    Button: Button.extend({ defaultProps: { size: 'md' }, styles: { root: { fontWeight: 600, fontSize: 13 } } }),
    Paper: Paper.extend({ defaultProps: { withBorder: true, radius: 'lg' } }),
    TextInput: TextInput.extend({ defaultProps: { size: 'md' } }),
    Textarea: Textarea.extend({ defaultProps: { size: 'md' } }),
    Select: Select.extend({ defaultProps: { size: 'md' } }),
    Modal: Modal.extend({ defaultProps: { centered: true, radius: 'lg', overlayProps: { backgroundOpacity: 0.4, blur: 3 } } }),
    Drawer: Drawer.extend({ defaultProps: { overlayProps: { backgroundOpacity: 0.35, blur: 2 } } }),
  },
});
