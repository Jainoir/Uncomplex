import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In dev, /api is proxied to the Spring Boot backend so no CORS is involved.
// In production, set VITE_API_BASE_URL to the deployed API origin instead.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
