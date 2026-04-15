import { createRoot } from 'react-dom/client'
import 'cesium/Build/Cesium/Widgets/widgets.css'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(<App />)
