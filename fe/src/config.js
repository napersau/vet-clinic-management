// Cấu hình kết nối tới backend. Chỉ cần đặt REACT_APP_API_URL (xem fe/.env.example), không có dấu / ở cuối.
// API Gateway là cổng duy nhất mà backend công bố ra ngoài; mọi đường dẫn bắt đầu bằng /api/v1.
export const API_URL = (process.env.REACT_APP_API_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const SESSION_KEY = 'vetclinic_session';
