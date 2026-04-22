/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./src/**/*.{html,vue,ts,tsx}",
    "./src/popup/**/*.{html,vue,ts}",
    "./src/options/**/*.{html,vue,ts}"
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
        quartile: {
          q1: '#10b981',
          q2: '#3b82f6',
          q3: '#f59e0b',
          q4: '#6b7280',
        }
      },
      width: {
        'popup': '380px',
      },
      maxHeight: {
        'popup': '600px',
      }
    },
  },
  plugins: [],
}
