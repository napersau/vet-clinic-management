// Chỉ chấp nhận đường dẫn nội bộ để tránh chuyển hướng ra ngoài sau khi đăng nhập.
export function getSafeReturnPath(path, fallback = '/') {
  const isInternal = typeof path === 'string' && path.startsWith('/') && !path.startsWith('//');
  return isInternal ? path : fallback;
}
