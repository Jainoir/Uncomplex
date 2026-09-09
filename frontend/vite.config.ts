import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In dev, API requests and the wake-up probe are proxied to the Spring Boot backend.
// In production, set VITE_API_BASE_URL to the deployed API origin instead.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator/health': 'http://localhost:8080',
    },
  },
})
