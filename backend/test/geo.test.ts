import test from "node:test";
import assert from "node:assert/strict";
import { computeInside, distanceM, nextDriveState } from "../src/geo.ts";

const home = { id: "h", name: "Home", lat: 40.0, lng: -75.0, radius_m: 150 };

test("distance ~111m per 0.001 deg latitude", () => {
  const d = distanceM(40, -75, 40.001, -75);
  assert.ok(d > 105 && d < 117, String(d));
});

test("arrive inside radius, hysteresis keeps you inside just past it", () => {
  assert.deepEqual(computeInside(40.0, -75.0, [home], []), ["h"]);
  // ~180m away: outside 150 but within 150*1.25 => still inside if previously inside
  const lat = 40.0 + 180 / 111195;
  assert.deepEqual(computeInside(lat, -75.0, [home], ["h"]), ["h"]);
  assert.deepEqual(computeInside(lat, -75.0, [home], []), []);
  // ~250m away: left
  assert.deepEqual(computeInside(40.0 + 250 / 111195, -75.0, [home], ["h"]), []);
});

test("driving needs two consecutive fast samples, then stops when slow", () => {
  let s = { driving: false, fastCount: 0 };
  let r = nextDriveState(s, 6);
  assert.equal(r.started, false);
  r = nextDriveState(r, 7);
  assert.equal(r.started, true);
  assert.equal(r.driving, true);
  r = nextDriveState(r, 8);
  assert.equal(r.started, false);
  r = nextDriveState(r, 0.2);
  assert.equal(r.driving, false);
  // a single blip resets
  r = nextDriveState(nextDriveState({ driving: false, fastCount: 0 }, 6), 1);
  assert.equal(r.fastCount, 0);
});
