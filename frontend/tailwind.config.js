/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: '#F97362', light: '#FF9F68', dark: '#E85D4A' },
        cream: { DEFAULT: '#FFF8F2', card: '#FFFFFF', soft: '#FFF1E6' },
        ink: { DEFAULT: '#2B2118', sub: '#6B5B4E' },
        rented: { DEFAULT: '#2FAE7E', soft: '#E7F6EF' },
        vacant: { DEFAULT: '#F5A623', soft: '#FEF3DE' },
        danger: '#E5484D',
        info: '#4A90D9'
      },
      borderRadius: {
        xl: '28px', lg: '22px', md: '16px', sm: '12px'
      },
      boxShadow: {
        soft: '0 8px 30px rgba(43,33,24,.08)',
        lg: '0 20px 55px rgba(43,33,24,.16)',
        hover: '0 18px 46px rgba(249,115,98,.22)'
      },
      fontFamily: {
        sans: ['PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Source Han Sans SC', 'system-ui', 'sans-serif']
      }
    }
  },
  plugins: [require('tailwindcss-animate')]
}
