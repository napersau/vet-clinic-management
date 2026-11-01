import { useEffect, useMemo, useRef } from 'react';
import { appointmentsApi } from '../../api/endpoints';
import useAsync from '../../hooks/useAsync';
import { Field, Input } from '../ui';
import { formatTime, todayIso } from '../../lib/format';

/**
 * Chọn ngày + khung giờ (14 khung 30 phút). Sức chứa mỗi khung = số bác sĩ đang hoạt động (backend tính).
 * `value` là giờ bắt đầu "HH:mm"; đổi ngày thì bỏ chọn nếu khung đó không còn chỗ.
 */
export default function SlotPicker({ date, onDateChange, value, onChange, error }) {
  const state = useAsync(() => (date ? appointmentsApi.slots(date) : Promise.resolve([])), [date]);
  const slots = useMemo(() => state.data || [], [state.data]);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  useEffect(() => {
    if (value && slots.length && !slots.some((slot) => formatTime(slot.startTime) === value && slot.available && !slot.past)) onChangeRef.current('');
  }, [slots, value]);
  const full = slots.length > 0 && slots.every((slot) => !slot.available || slot.past);
  return <div className="ui-stack" style={{ gap: 14 }}>
    <Field label="Ngày khám" error={error?.date}><Input type="date" min={todayIso()} value={date} onChange={(event) => onDateChange(event.target.value)} /></Field>
    {state.loading && <p className="ui-muted">Đang tải khung giờ…</p>}
    {state.error && <div className="ui-alert error" role="alert"><span>{state.error}</span><button type="button" onClick={state.reload}>Thử lại</button></div>}
    {!state.loading && slots.length > 0 && <div role="group" aria-label="Khung giờ khám" className="ui-slots">
      {slots.map((slot) => {
        const time = formatTime(slot.startTime);
        const disabled = !slot.available || slot.past;
        return <button type="button" key={time} className="ui-slot" aria-pressed={value === time} disabled={disabled} onClick={() => onChange(time)}>
          {time}<small>{slot.past ? 'Đã qua' : slot.available ? `Còn ${slot.remaining}/${slot.capacity}` : 'Hết chỗ'}</small>
        </button>;
      })}
    </div>}
    {full && <div className="ui-alert warning" role="status"><span>Ngày này đã hết khung giờ trống. Vui lòng chọn ngày khác.</span></div>}
    {error?.startTime && <small className="ui-field"><span className="error" style={{ color: 'var(--ui-error)' }}>{error.startTime}</span></small>}
  </div>;
}
