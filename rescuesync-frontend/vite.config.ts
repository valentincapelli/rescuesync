import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite corre en :5173 (default). El backend (:8081) tiene CORS habilitado
// para ese origen (ver CORS_ORIGINS en rescuesync-backend/.env.example).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
})
