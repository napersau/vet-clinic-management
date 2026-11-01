# Giao diện PawCare (React)

Kết nối tới backend qua API Gateway. Kiến trúc và quy ước: mục "Frontend" trong `../CLAUDE.md`.

```bash
cp .env.example .env     # REACT_APP_API_URL=http://localhost:8080, PORT=3000
npm install
npm start                # http://localhost:3000
npm test                 # test đơn vị (lớp API, phiên đăng nhập, đăng nhập/đăng ký, phân quyền)
npm run build            # phải "Compiled successfully", không cảnh báo lint
```

Tài khoản mẫu: `admin` / `Admin@123`. Chủ nuôi tự đăng ký ở `/auth/register`; các vai trò nhân sự do Admin tạo.

## Cấu trúc `src/`

| Thư mục | Nội dung |
|---|---|
| `config.js` | Địa chỉ Gateway (`REACT_APP_API_URL`) và khóa lưu phiên |
| `api/` | `client.js` (gắn Bearer, refresh một lần dùng chung, xử lý 401), `session.js`, `endpoints.js` (một hàm cho mỗi endpoint) |
| `contexts/AuthContext.js` | Phiên đăng nhập: `login`, `register`, `logout`, `updateProfile` |
| `components/auth/` | `RequireAuth`, `RequireRole` (chỉ là lớp tiện lợi, quyền thật nằm ở backend) |
| `components/layout/` | `Header`, `StaffLayout` (thanh bên + thanh trên), `CustomerLayout` |
| `components/ui/` | Bộ thành phần dùng chung: `PageHead`, `Panel`, `Table`, `Pager`, `FormDialog`... |
| `constants/` | `roles.js` (mã vai trò, trang đầu), `navigation.js` (menu theo vai trò), `routes.js` |
| `hooks/` | `useAsync` (tải dữ liệu), `useAction` (thao tác ghi, chặn bấm đúp), `usePolling`, `useTheme` |
| `lib/` | `format.js` (tiền VND, ngày giờ theo giờ VN), `labels.js` (nhãn và màu cho enum) |

Thêm màn hình mới: viết trang, rồi đăng ký vào `IMPLEMENTED` trong `routes/index.js`. Đường dẫn nào đã có trong menu mà chưa đăng ký thì hiện trang "Đang xây dựng" trong đúng khung của vai trò.
