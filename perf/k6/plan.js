/**
 * k6 load script approximating docs/design.md §30 (500 tasks, tunable conflict).
 *
 * Usage (against docker compose):
 *   k6 run -e BASE_URL=http://localhost:8080 -e TASKS=500 -e POOL=20 perf/k6/plan.js
 *
 * Smaller POOL => higher conflict probability (shared project ids).
 */
import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 10,
  iterations: Number(__ENV.TASKS || 500),
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

const BASE = __ENV.BASE_URL || "http://localhost:8080";
const POOL = Number(__ENV.POOL || 20);

export default function () {
  const projectId = String(Math.floor(Math.random() * POOL));
  const payload = JSON.stringify({
    topResourceClass: "project",
    topResourceId: projectId,
    searchDsl: {},
    payload: { i: __ITER },
  });
  const res = http.post(`${BASE}/api/v1/tasks`, payload, {
    headers: { "Content-Type": "application/json" },
  });
  check(res, { "ingest 201": (r) => r.status === 201 });
  sleep(0.01);
}
