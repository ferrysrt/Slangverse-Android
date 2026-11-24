import './styles.css';

export const metadata = {
  title: 'Slangverse | Admin Penelitian',
  description: 'Pemantauan penelitian Slangverse',
  robots: { index: false, follow: false }
};

export default function RootLayout({ children }) {
  return <html lang="id"><body>{children}</body></html>;
}
