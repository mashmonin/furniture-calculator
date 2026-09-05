import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ConfigProvider } from 'antd'
import './index.css'
import App from './App.tsx'

// Ant Design по умолчанию красит текст полупрозрачным чёрным (rgba(0,0,0,0.88) и
// светлее для приглушённого текста) — заменяем на непрозрачные цвета той же иерархии
// контраста (основной темнее приглушённого), см. change increase-ui-text-contrast.
const theme = {
  token: {
    colorText: '#000000',
    colorTextSecondary: '#404040',
    colorTextTertiary: '#737373',
    colorTextDescription: '#404040',
  },
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider theme={theme}>
      <App />
    </ConfigProvider>
  </StrictMode>,
)
