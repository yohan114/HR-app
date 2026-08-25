import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
import './tokens.css'
import './styles.css'
import './shell.css'

const container = document.getElementById('root')
if (container === null) {
  throw new Error('Root element not found')
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
