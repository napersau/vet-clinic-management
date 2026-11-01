const paths = {
  heart: 'M20 5a5 5 0 0 0-8 1 5 5 0 0 0-8-1c-5 6 8 15 8 15S25 11 20 5z',
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M22 21v-2a4 4 0 0 0-3-3.87 M15 3.13a4 4 0 0 1 0 7.75 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  shield: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7z M8 12l3 3 5-5',
  search: 'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  logout: 'M9 21H4V3h5 M14 8l5 4-5 4 M8 12h11',
  arrow: 'M8 5l7 7-7 7',
  plus: 'M12 5v14 M5 12h14',
  lock: 'M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4',
  check: 'M20 6 9 17l-5-5',
  service: 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6z',
  medicine: 'M9 3a6 6 0 0 1 8.5 8.5L11.5 18A6 6 0 0 1 3 9.5z M7 7l10 10',
  box: 'M12 3 3 8v10l9 4 9-4V8z M3 8l9 5 9-5 M12 13v9 M7 5.5l9 5',
  calendar: 'M3 5h18v16H3z M7 3v4 M17 3v4 M3 11h18 M8 15h2 M14 15h2',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  sun: 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.5 1.5 M17.5 17.5 19 19 M5 19l1.5-1.5 M17.5 6.5 19 5',
  moon: 'M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13z',
  activity: 'M3 12h4l3-8 4 16 3-8h4',
  document: 'M6 3h9l4 4v14H6z M15 3v5h4 M9 12h7 M9 16h7',
  chart: 'M4 3v18h17 M8 17v-5 M13 17V7 M18 17v-8',
  refresh: 'M20 7v5h-5 M4 17v-5h5 M6 6a8 8 0 0 1 13 2l1 4 M4 12l1 4a8 8 0 0 0 13 2',
};

export default function AdminIcon({ name, ...props }) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}><path d={paths[name] || paths.users} /></svg>;
}
