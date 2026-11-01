// Nơi dựng hộp thoại. Biến màu (--admin-*, --ui-*) khai báo trên khung .admin-shell / .customer-shell và hộp thoại kế thừa qua cây DOM,
// nên phải dựng bên trong khung đó (không phải document.body). Đồng thời vẫn nằm ngoài <form> của trang, vì <form> không được lồng nhau.
export const portalRoot = () => document.querySelector('.admin-shell, .customer-shell') || document.body;
