/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--color-primary)',
          hover: 'var(--color-primary-hover, var(--color-primary))',
          glow: 'var(--color-primary-glow, rgba(37, 99, 235, 0.08))',
        },
        surface: 'var(--bg-surface)',
        app: 'var(--bg-app)',
      },
      boxShadow: {
        card: '0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05)',
      }
    },
  },
  plugins: [],
}
