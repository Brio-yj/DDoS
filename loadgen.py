#!/usr/bin/env python3
"""Simple load generator for auth-session vs auth-jwt services."""

from __future__ import annotations

import argparse
import math
import os
import random
import statistics
import threading
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from typing import Dict, List, Optional

import requests


@dataclass
class EndpointConfig:
    auth_session_url: str
    auth_jwt_url: str
    api_a_url: str
    api_b_url: str


class MetricsCollector:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.latencies: List[float] = []
        self.status_counts: Counter[int] = Counter()

    def record(self, status: int, latency_seconds: Optional[float]) -> None:
        with self._lock:
            self.status_counts[status] += 1
            if latency_seconds is not None:
                self.latencies.append(latency_seconds)

    def aggregate(self) -> Dict[str, float | int]:
        with self._lock:
            total = sum(self.status_counts.values())
            error_count = sum(count for status, count in self.status_counts.items() if status == 0 or status >= 400)
            avg_ms = statistics.mean(self.latencies) * 1000 if self.latencies else 0.0
            p95_ms = self._percentile(self.latencies, 95) * 1000 if self.latencies else 0.0
            p99_ms = self._percentile(self.latencies, 99) * 1000 if self.latencies else 0.0
            return {
                "count": total,
                "errors": error_count,
                "error_rate": (error_count / total * 100) if total else 0.0,
                "avg_ms": avg_ms,
                "p95_ms": p95_ms,
                "p99_ms": p99_ms,
                "status_counts": dict(self.status_counts),
            }

    @staticmethod
    def _percentile(data: List[float], percentile: float) -> float:
        if not data:
            return 0.0
        sorted_data = sorted(data)
        k = max(0, min(len(sorted_data) - 1, math.ceil((percentile / 100.0) * len(sorted_data)) - 1))
        return sorted_data[k]


@dataclass
class LoadConfig:
    target: str
    threads: int
    duration: int
    rps: float
    signup: bool
    prelogin: bool
    reuse_token: bool
    endpoints: EndpointConfig
    seed: Optional[int]


def parse_args() -> LoadConfig:
    parser = argparse.ArgumentParser(description="Generate load against auth-session or auth-jwt stack")
    parser.add_argument("--target", choices=["auth-session", "auth-jwt"], required=True, help="Service to stress")
    parser.add_argument("--threads", type=int, default=100, help="Number of concurrent worker threads")
    parser.add_argument("--duration", type=int, default=60, help="Test duration in seconds")
    parser.add_argument("--rps", type=float, default=200.0, help="Approximate total requests per second")
    parser.add_argument("--signup", action="store_true", help="Create user accounts before starting the test")
    parser.add_argument("--no-prelogin", dest="prelogin", action="store_false", help="Skip pre-login/token warmup")
    parser.add_argument("--no-reuse-token", dest="reuse_token", action="store_false", help="Force login on every JWT iteration")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for deterministic payloads")
    parser.set_defaults(prelogin=True, reuse_token=True)

    parser.add_argument("--auth-session-url", default=os.environ.get("AUTH_SESSION_URL", "http://localhost:8081"))
    parser.add_argument("--auth-jwt-url", default=os.environ.get("AUTH_JWT_URL", "http://localhost:8082"))
    parser.add_argument("--api-a-url", default=os.environ.get("API_A_URL", "http://localhost:8083"))
    parser.add_argument("--api-b-url", default=os.environ.get("API_B_URL", "http://localhost:8084"))

    args = parser.parse_args()
    endpoints = EndpointConfig(
        auth_session_url=args.auth_session_url.rstrip("/"),
        auth_jwt_url=args.auth_jwt_url.rstrip("/"),
        api_a_url=args.api_a_url.rstrip("/"),
        api_b_url=args.api_b_url.rstrip("/"),
    )
    return LoadConfig(
        target=args.target,
        threads=args.threads,
        duration=args.duration,
        rps=args.rps,
        signup=args.signup,
        prelogin=args.prelogin,
        reuse_token=args.reuse_token,
        endpoints=endpoints,
        seed=args.seed,
    )


def generate_users(target: str, count: int) -> List[Dict[str, str]]:
    prefix = "authjwt" if target == "auth-jwt" else "authsession"
    users = []
    for idx in range(count):
        email = f"{prefix}_user_{idx}@example.com"
        password = f"TestPassword!{idx:03d}"
        users.append({"email": email, "password": password})
    return users


def signup_users(config: LoadConfig, users: List[Dict[str, str]]) -> None:
    if not config.signup:
        return
    base_url = config.endpoints.auth_jwt_url if config.target == "auth-jwt" else config.endpoints.auth_session_url
    print(f"[bootstrap] Signing up {len(users)} users against {base_url}")
    session = requests.Session()
    for user in users:
        payload = {"email": user["email"], "password": user["password"]}
        try:
            start = time.perf_counter()
            resp = session.post(f"{base_url}/auth/signup", json=payload, timeout=10)
            elapsed = time.perf_counter() - start
            if resp.status_code in (200, 201, 409):
                print(f"  -> signup {user['email']} status={resp.status_code} ({elapsed*1000:.1f} ms)")
            else:
                print(f"  -> signup {user['email']} failed status={resp.status_code}: {resp.text}")
        except requests.RequestException as exc:
            print(f"  -> signup {user['email']} exception: {exc}")
    print("[bootstrap] Signup phase complete")


def auth_session_worker(config: LoadConfig, users: List[Dict[str, str]], metrics: Dict[str, MetricsCollector], idx: int) -> None:
    session = requests.Session()
    creds = users[idx % len(users)]
    interval = config.threads / config.rps if config.rps > 0 else 0.0
    stop_at = time.perf_counter() + config.duration
    while time.perf_counter() < stop_at:
        iteration_start = time.perf_counter()
        payload = {"email": creds["email"], "password": creds["password"]}
        try:
            start = time.perf_counter()
            resp = session.post(f"{config.endpoints.auth_session_url}/auth/login", json=payload, timeout=10)
            latency = time.perf_counter() - start
            metrics["auth_login"].record(resp.status_code, latency)
        except requests.RequestException:
            metrics["auth_login"].record(0, None)
        sleep_time = max(0.0, interval - (time.perf_counter() - iteration_start))
        if sleep_time:
            time.sleep(sleep_time)


def login_jwt(session: requests.Session, config: LoadConfig, creds: Dict[str, str], metrics: Dict[str, MetricsCollector]) -> tuple[Optional[str], Optional[str]]:
    payload = {"email": creds["email"], "password": creds["password"]}
    try:
        start = time.perf_counter()
        resp = session.post(f"{config.endpoints.auth_jwt_url}/auth/login", json=payload, timeout=10)
        latency = time.perf_counter() - start
        metrics["auth_login"].record(resp.status_code, latency)
        if resp.ok:
            data = resp.json()
            return data.get("accessToken"), data.get("refreshToken")
    except requests.RequestException:
        metrics["auth_login"].record(0, None)
    return None, None


def refresh_jwt(session: requests.Session, config: LoadConfig, refresh_token: str,
                metrics: Dict[str, MetricsCollector]) -> Optional[str]:
    try:
        start = time.perf_counter()
        resp = session.post(f"{config.endpoints.auth_jwt_url}/auth/refresh", json={"refreshToken": refresh_token}, timeout=10)
        latency = time.perf_counter() - start
        metrics["auth_refresh"].record(resp.status_code, latency)
        if resp.ok:
            data = resp.json()
            return data.get("accessToken")
    except requests.RequestException:
        metrics["auth_refresh"].record(0, None)
    return None


def build_order_payload() -> Dict[str, List[Dict[str, int]]]:
    product_ids = [1, 2, 3]
    items = []
    count = random.randint(1, min(2, len(product_ids)))
    for _ in range(count):
        product_id = random.choice(product_ids)
        quantity = random.randint(1, 3)
        items.append({"productId": product_id, "quantity": quantity})
    return {"items": items}


def auth_jwt_worker(config: LoadConfig, users: List[Dict[str, str]], metrics: Dict[str, MetricsCollector], idx: int) -> None:
    session = requests.Session()
    creds = users[idx % len(users)]
    interval = config.threads / config.rps if config.rps > 0 else 0.0
    stop_at = time.perf_counter() + config.duration
    access_token: Optional[str] = None
    refresh_token: Optional[str] = None

    if config.prelogin:
        access_token, refresh_token = login_jwt(session, config, creds, metrics)

    while time.perf_counter() < stop_at:
        iteration_start = time.perf_counter()
        if not access_token or not config.reuse_token:
            access_token, refresh_token = login_jwt(session, config, creds, metrics)

        headers = {"Authorization": f"Bearer {access_token}"} if access_token else {}
        if access_token:
            try:
                start = time.perf_counter()
                resp = session.get(f"{config.endpoints.api_a_url}/api-a/items", headers=headers, timeout=10)
                latency = time.perf_counter() - start
                metrics["api_a_items"].record(resp.status_code, latency)
                if resp.status_code == 401 and refresh_token:
                    new_access = refresh_jwt(session, config, refresh_token, metrics)
                    if new_access:
                        access_token = new_access
                        headers = {"Authorization": f"Bearer {access_token}"}
                        continue
            except requests.RequestException:
                metrics["api_a_items"].record(0, None)

            order_payload = build_order_payload()
            try:
                start = time.perf_counter()
                resp = session.post(f"{config.endpoints.api_b_url}/api-b/orders", headers=headers, json=order_payload, timeout=10)
                latency = time.perf_counter() - start
                metrics["api_b_orders"].record(resp.status_code, latency)
                if resp.status_code == 401 and refresh_token:
                    new_access = refresh_jwt(session, config, refresh_token, metrics)
                    if new_access:
                        access_token = new_access
            except requests.RequestException:
                metrics["api_b_orders"].record(0, None)
        else:
            time.sleep(0.1)

        sleep_time = max(0.0, interval - (time.perf_counter() - iteration_start))
        if sleep_time:
            time.sleep(sleep_time)


def run_load(config: LoadConfig) -> Dict[str, MetricsCollector]:
    if config.seed is not None:
        random.seed(config.seed)

    users = generate_users(config.target, config.threads)
    signup_users(config, users)

    metrics: Dict[str, MetricsCollector] = defaultdict(MetricsCollector)
    workers: List[threading.Thread] = []

    worker_fn = auth_session_worker if config.target == "auth-session" else auth_jwt_worker

    for idx in range(config.threads):
        thread = threading.Thread(target=worker_fn, args=(config, users, metrics, idx), daemon=True)
        workers.append(thread)
        thread.start()

    for thread in workers:
        thread.join()

    return metrics


def print_summary(metrics: Dict[str, MetricsCollector]) -> None:
    print("\n=== Load Test Summary ===")
    for name, collector in metrics.items():
        summary = collector.aggregate()
        print(f"Endpoint: {name}")
        print(f"  Requests : {summary['count']}")
        print(f"  Errors   : {summary['errors']} ({summary['error_rate']:.2f}% error rate)")
        print(f"  Avg ms   : {summary['avg_ms']:.2f}")
        print(f"  P95 ms   : {summary['p95_ms']:.2f}")
        print(f"  P99 ms   : {summary['p99_ms']:.2f}")
        print(f"  Statuses : {summary['status_counts']}")
        print()


def main() -> None:
    config = parse_args()
    print(f"[config] target={config.target} threads={config.threads} duration={config.duration}s rps={config.rps}")
    print(f"[config] reuse_token={config.reuse_token} prelogin={config.prelogin} signup={config.signup}")
    metrics = run_load(config)
    print_summary(metrics)


if __name__ == "__main__":
    main()
