#!/usr/bin/env python3
import urllib.request
import urllib.error
import json
import time
import threading
import sys
import random
import uuid

# Configuration
TOXIPROXY_URL = "http://localhost:8474"
GATEWAY_URL = "http://localhost:8080/api"
PROXY_NAME = "postgres_proxy"

class CloudPoolChaosTester:
    def __init__(self):
        self.server_active = False
        self.toxiproxy_active = False
        self.tenant_a_token = None
        self.tenant_b_token = None
        self.isolation_failures = 0
        self.total_requests = 0
        self.success_count = 0
        self.error_count = 0
        self._lock = threading.Lock()

    def check_services(self):
        """Check if Gateway and Toxiproxy are reachable."""
        print("[*] Checking backend and chaos infrastructure status...")
        
        # Check Gateway
        try:
            req = urllib.request.Request(f"{GATEWAY_URL}/health")
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                if resp.status == 200:
                    self.server_active = True
                    print("[+] Gateway is ONLINE.")
        except Exception as e:
            print(f"[-] Gateway check failed: {e}")
            print("[-] Gateway server is required. Exiting.")
            sys.exit(1)

        # Check Toxiproxy
        try:
            req = urllib.request.Request(TOXIPROXY_URL)
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                pass
            self.toxiproxy_active = True
            print("[+] Toxiproxy is ONLINE.")
        except urllib.error.HTTPError as e:
            if e.code in (200, 404):
                self.toxiproxy_active = True
                print("[+] Toxiproxy is ONLINE.")
            else:
                print(f"[-] Toxiproxy responded with error code: {e.code}")
                print("[-] Toxiproxy is required. Exiting.")
                sys.exit(1)
        except Exception as e:
            print(f"[-] Toxiproxy is OFFLINE: {e}")
            print("[-] Toxiproxy is required. Exiting.")
            sys.exit(1)

    def call_api(self, url, method="GET", headers=None, data=None):
        """Helper to make HTTP calls."""
        if headers is None:
            headers = {}
        headers["Content-Type"] = "application/json"

        
        body = json.dumps(data).encode("utf-8") if data else None
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        
        try:
            with urllib.request.urlopen(req, timeout=5.0) as resp:
                return resp.status, json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            try:
                err_body = json.loads(e.read().decode("utf-8"))
            except Exception:
                err_body = None
            return e.code, err_body
        except Exception as e:
            return 500, {"error": str(e)}

    def configure_toxiproxy(self):
        """Setup or reset postgres proxy in Toxiproxy."""
        if not self.toxiproxy_active:
            return
            
        print("[*] Configuring Toxiproxy postgres_proxy...")
        # Check if proxy already exists
        status, resp = self.call_api(f"{TOXIPROXY_URL}/proxies/{PROXY_NAME}", "GET")
        if status == 200:
            print("[+] postgres_proxy already exists. Clearing all active toxics to preserve connection pool...")
            self.clear_toxics()
            return

        # Create new proxy if it doesn't exist
        proxy_data = {
            "name": PROXY_NAME,
            "listen": "0.0.0.0:5433",
            "upstream": "postgres:5432",
            "enabled": True
        }
        status, resp = self.call_api(f"{TOXIPROXY_URL}/proxies", "POST", data=proxy_data)
        if status == 201 or status == 200:
            print("[+] postgres_proxy created successfully.")
        else:
            print(f"[-] Failed to create postgres_proxy: {resp}")

    def set_toxic(self, type_name, attributes):
        """Apply a network toxic to postgres_proxy."""
        if not self.toxiproxy_active:
            raise RuntimeError("Toxiproxy is not active but set_toxic was called")
            
        # First remove existing toxic of same type to avoid conflicts
        try:
            req_del = urllib.request.Request(f"{TOXIPROXY_URL}/proxies/{PROXY_NAME}/toxics/{type_name}", method="DELETE")
            urllib.request.urlopen(req_del, timeout=1.0)
        except Exception:
            pass

        toxic_data = {
            "name": type_name,
            "type": type_name,
            "stream": "downstream",
            "toxicity": 1.0,
            "attributes": attributes
        }
        status, resp = self.call_api(f"{TOXIPROXY_URL}/proxies/{PROXY_NAME}/toxics", "POST", data=toxic_data)
        if status == 200 or status == 201:
            print(f"[+] Applied toxic '{type_name}': {attributes}")
        else:
            print(f"[-] Failed to apply toxic '{type_name}': {resp}")

    def clear_toxics(self):
        """Remove all active toxics."""
        if not self.toxiproxy_active:
            return
        
        print("[*] Clearing all Toxiproxy toxics...")
        # Get active toxics
        status, resp = self.call_api(f"{TOXIPROXY_URL}/proxies/{PROXY_NAME}/toxics", "GET")
        if status == 200 and resp:
            # Parse toxic names from dictionary keys or list of objects
            toxic_names = []
            if isinstance(resp, dict):
                toxic_names = list(resp.keys())
            elif isinstance(resp, list):
                for item in resp:
                    if isinstance(item, dict):
                        toxic_names.append(item.get("name"))
                    elif isinstance(item, str):
                        toxic_names.append(item)

            for name in toxic_names:
                if not name:
                    continue
                req_del = urllib.request.Request(f"{TOXIPROXY_URL}/proxies/{PROXY_NAME}/toxics/{name}", method="DELETE")
                try:
                    urllib.request.urlopen(req_del, timeout=1.0)
                except Exception:
                    pass

    def setup_tenants(self):
        """Create and authenticate Tenant A and Tenant B."""
        if not self.server_active:
            raise RuntimeError("Server is not active but setup_tenants was called")

        print("[*] Initializing Tenant A and Tenant B accounts...")
        # Register and login Tenant A
        reg_status_a, reg_resp_a = self.call_api(f"{GATEWAY_URL}/auth/register", "POST", data={
            "email": "tenant_a@cloudpool.com", "password": "TenantPass123!", "name": "Tenant A"
        })
        print(f"[*] Tenant A Register attempt returned status: {reg_status_a}")
        
        status_a, resp_a = self.call_api(f"{GATEWAY_URL}/auth/login", "POST", data={
            "email": "tenant_a@cloudpool.com", "password": "TenantPass123!"
        })
        print(f"[*] Tenant A Login attempt returned status: {status_a}")
        if status_a == 200:
            self.tenant_a_token = resp_a.get("token")
            
        # Register and login Tenant B
        reg_status_b, reg_resp_b = self.call_api(f"{GATEWAY_URL}/auth/register", "POST", data={
            "email": "tenant_b@cloudpool.com", "password": "TenantPass123!", "name": "Tenant B"
        })
        print(f"[*] Tenant B Register attempt returned status: {reg_status_b}")
        
        status_b, resp_b = self.call_api(f"{GATEWAY_URL}/auth/login", "POST", data={
            "email": "tenant_b@cloudpool.com", "password": "TenantPass123!"
        })
        print(f"[*] Tenant B Login attempt returned status: {status_b}")
        if status_b == 200:
            self.tenant_b_token = resp_b.get("token")

        if not self.tenant_a_token or not self.tenant_b_token:
            print("[-] Critical Error: Failed to acquire tenant auth tokens.")
            print(f"    Tenant A token acquired: {self.tenant_a_token is not None}")
            print(f"    Tenant B token acquired: {self.tenant_b_token is not None}")
            sys.exit(1)

    def run_tenant_request(self, tenant_name, token, expected_tenant_tag):
        """Make an API request and check if data from another tenant leaks."""
        with self._lock:
            self.total_requests += 1
        headers = {"Authorization": f"Bearer {token}"}
        
        if not self.server_active:
            raise RuntimeError("Server is not active but run_tenant_request was called")

        # Real Mode API validation
        status, resp = self.call_api(f"{GATEWAY_URL}/files", "GET", headers=headers)
        
        if status == 200:
            with self._lock:
                self.success_count += 1
            # Verify that response files do not belong to the other tenant
            # We enforce this by checking metadata tag patterns if present
            if isinstance(resp, list):
                for file_obj in resp:
                    owner_email = file_obj.get("ownerEmail", "")
                    if owner_email and owner_email != expected_tenant_tag:
                        print(f"[!] DANGER: Tenant leak detected! {tenant_name} saw file of owner {owner_email}")
                        with self._lock:
                            self.isolation_failures += 1
        else:
            with self._lock:
                self.error_count += 1

    def run_concurrent_workload(self, concurrent_users=10, requests_per_user=5):
        """Run interleaved concurrent tenant requests."""
        threads = []
        for _ in range(concurrent_users):
            t_a = threading.Thread(target=lambda: [self.run_tenant_request("Tenant A", self.tenant_a_token, "tenant_a@cloudpool.com") for _ in range(requests_per_user)])
            t_b = threading.Thread(target=lambda: [self.run_tenant_request("Tenant B", self.tenant_b_token, "tenant_b@cloudpool.com") for _ in range(requests_per_user)])
            threads.extend([t_a, t_b])

        # Interleave execution
        for t in threads:
            t.start()
        for t in threads:
            t.join()

    def run_chaos_suite(self):
        """Run the complete chaos test suite."""
        print("\n" + "="*60)
        print("          CLOUDPOOL MULTI-TENANT CHAOS SUITE")
        print("="*60)
        
        try:
            # Test Case 1: Baseline (No Toxics)
            print("\n[Test Case 1] Baseline Operations (0ms Latency, 100% Reliability)")
            self._active_toxic = None
            self.run_concurrent_workload()
            print(f"--> Done. Success: {self.success_count}, Errors: {self.error_count}, Leaks: {self.isolation_failures}")

            # Test Case 2: 100ms Latency
            print("\n[Test Case 2] Latency Simulation: 100ms Delay")
            self._active_toxic = "latency"
            self.set_toxic("latency", {"latency": 100})
            self.run_concurrent_workload()
            print(f"--> Done. Success: {self.success_count}, Errors: {self.error_count}, Leaks: {self.isolation_failures}")

            # Test Case 3: 500ms Latency
            print("\n[Test Case 3] Latency Simulation: 500ms Delay")
            self._active_toxic = "latency"
            self.set_toxic("latency", {"latency": 500})
            self.run_concurrent_workload()
            print(f"--> Done. Success: {self.success_count}, Errors: {self.error_count}, Leaks: {self.isolation_failures}")

            # Test Case 4: Packet Loss
            print("\n[Test Case 4] Packet Loss Simulation: Slicing / Fragmentation")
            self._active_toxic = "slicer"
            self.set_toxic("slicer", {"average_size": 256, "delay": 2000}) # Slice data packet payload
            self.run_concurrent_workload()
            print(f"--> Done. Success: {self.success_count}, Errors: {self.error_count}, Leaks: {self.isolation_failures}")

            # Test Case 5: Connection Drops
            print("\n[Test Case 5] Connection Drops Simulation: Immediate Timeout")
            self._active_toxic = "timeout"
            self.set_toxic("timeout", {"timeout": 1}) # Drop connection immediately
            self.run_concurrent_workload()
            print(f"--> Done. Success: {self.success_count}, Errors: {self.error_count}, Leaks: {self.isolation_failures}")
        finally:
            self.clear_toxics()
            self._active_toxic = None

        # Print Report
        print("\n" + "="*60)
        print("          CHAOS VERIFICATION SUMMARY REPORT")
        print("="*60)
        print(f"Total Requests Executed      : {self.total_requests}")
        print(f"Successful Requests          : {self.success_count}")
        print(f"Graceful Failures (Errors)   : {self.error_count}")
        print(f"Tenant Isolation Violations  : {self.isolation_failures}")
        
        isolation_percentage = 100.0 if self.isolation_failures == 0 else 0.0
        print(f"Multi-Tenant Isolation Score : {isolation_percentage}%")
        print("="*60 + "\n")
        
        if self.isolation_failures > 0:
            print("[-] FAILURE: Tenant data leaked under network degradation!")
            sys.exit(1)
        else:
            print("[+] SUCCESS: 100% Tenant Isolation verified under network degradation.")

def main():
    tester = CloudPoolChaosTester()
    tester.check_services()
    tester.configure_toxiproxy()
    tester.setup_tenants()
    tester.run_chaos_suite()

if __name__ == "__main__":
    main()
