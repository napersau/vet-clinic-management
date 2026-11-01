// Thu phóng ảnh trong hộp xem ảnh CĐHA.
export const MIN_SCALE = 0.25;
export const MAX_SCALE = 8;
const STEP = 1.25;

export const clampScale = (scale) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));

/** direction > 0 phóng to, < 0 thu nhỏ; làm tròn để bấm + rồi − quay về đúng mức cũ. */
export const stepScale = (scale, direction) => clampScale(Math.round(scale * (direction > 0 ? STEP : 1 / STEP) * 1000) / 1000);

/** Bước theo con lăn chuột: cuộn lên (deltaY < 0) là phóng to. */
export const wheelScale = (scale, deltaY) => stepScale(scale, deltaY < 0 ? 1 : -1);

export const formatScale = (scale) => `${Math.round(scale * 100)}%`;
