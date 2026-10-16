import './AuthField.css';

// Ô nhập của biểu mẫu xác thực: nhãn, ô input, thông báo lỗi (gắn aria) và phần phụ đặt cạnh input (`trailing`).
export default function AuthField({ id, label, error, trailing, ...inputProps }) {
  const errorId = error ? `${id}-error` : undefined;

  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      <div className="auth-field-control">
        <input id={id} className={trailing ? 'has-trailing' : undefined} aria-invalid={Boolean(error)} aria-describedby={errorId} {...inputProps} />
        {trailing}
      </div>
      {error && <small className="field-error" id={errorId}>{error}</small>}
    </div>
  );
}
