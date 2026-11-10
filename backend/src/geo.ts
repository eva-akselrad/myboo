const R = 6371000;
const rad = (d: number) => (d * Math.PI) / 180;

/** Great-circle distance in metres. */
export function distanceM(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

export interface Place { id: string; name: string; lat: number; lng: number; radius_m: number }

/** Leaving requires going this much past the radius, to avoid GPS-jitter flapping. */
export const EXIT_HYSTERESIS = 1.25;

export function computeInside(lat: number, lng: number, places: Place[], prev: string[]): string[] {
  const was = new Set(prev);
  return places
    .filter((p) => {
      const d = distanceM(lat, lng, p.lat, p.lng);
      return d <= (was.has(p.id) ? p.radius_m * EXIT_HYSTERESIS : p.radius_m);
    })
    .map((p) => p.id);
}

export const DRIVE_START_MPS = 4.5; // ~10 mph
export const DRIVE_STOP_MPS = 1.0;
export const DRIVE_START_SAMPLES = 2;

export interface DriveState { driving: boolean; fastCount: number }

/** Debounced driving detector. `started` is true only on the transition into driving. */
export function nextDriveState(prev: DriveState, speed: number | null): DriveState & { started: boolean } {
  if (speed == null || speed < 0) return { ...prev, started: false };
  if (prev.driving) {
    return speed < DRIVE_STOP_MPS
      ? { driving: false, fastCount: 0, started: false }
      : { driving: true, fastCount: prev.fastCount, started: false };
  }
  const fastCount = speed >= DRIVE_START_MPS ? prev.fastCount + 1 : 0;
  const started = fastCount >= DRIVE_START_SAMPLES;
  return { driving: started, fastCount: started ? 0 : fastCount, started };
}
