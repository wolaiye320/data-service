import React from 'react'
import ReactDOM from 'react-dom/client'
import { App as AntdApp, ConfigProvider } from 'antd'
import { BrowserRouter } from 'react-router-dom'
import zhCN from 'antd/locale/zh_CN'
import App from './App'
import './styles/global.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#0f766e',
          colorInfo: '#0f766e',
          borderRadius: 12,
          fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
        },
      }}
    >
      <AntdApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>,
)
