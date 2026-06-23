#!/usr/bin/env python3
import subprocess
import re
import os
import sys

def run_k6_load_test(target_url="http://localhost:8080"):
    print(f"[*] Starting k6 load test against {target_url}...")
    
    # Register the benchmark user to prevent login requests from failing with 401
    import urllib.request
    import json
    try:
        reg_url = f"{target_url}/api/auth/register"
        reg_data = json.dumps({
            "email": "benchmark_dev@cloudpool.com",
            "password": "BenchmarkPass123!",
            "name": "Benchmark User"
        }).encode("utf-8")
        req = urllib.request.Request(
            reg_url, 
            data=reg_data, 
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=5.0) as resp:
            print("[+] Registered benchmark user successfully.")
    except Exception as e:
        print(f"[*] Benchmark user registration note (may already exist): {e}")
        
    # Locate load.js script relative to this file
    script_dir = os.path.dirname(os.path.abspath(__file__))
    load_js_path = os.path.join(script_dir, "load.js")
    
    # Setup environment variable for target URL
    env = os.environ.copy()
    env["TARGET_URL"] = target_url
    
    k6_commands = ["k6", r"C:\Program Files\k6\k6.exe"]
    for k6_cmd in k6_commands:
        try:
            # Run k6 command and capture stdout/stderr
            result = subprocess.run(
                [k6_cmd, "run", load_js_path],
                capture_output=True,
                text=True,
                env=env,
                check=True
            )
            output = result.stdout
            print("[+] k6 execution completed successfully.")
            return output
        except FileNotFoundError:
            continue
        except subprocess.CalledProcessError as e:
            if e.returncode == 99:
                print("[!] k6 completed with crossed thresholds (exit code 99). Proceeding to parse metrics.")
                return e.stdout
            else:
                print(f"[-] k6 failed with exit code {e.returncode}")
                print(e.stderr)
                sys.exit(1)

    print("[-] Error: 'k6' executable not found in standard paths.")
    print("[-] k6 load testing tool is required but not installed.")
    print("[*] Install k6 from https://k6.io/docs/getting-started/installation/")
    sys.exit(1)

def parse_k6_metrics(output):
    # Regex to extract statistics from k6 stdout
    reqs_sec_match = re.search(r"http_reqs\.+:\s+\d+\s+([\d\.]+)/s", output)
    failed_reqs_match = re.search(r"http_req_failed\.+:\s+([\d\.]+)%", output)
    
    # Extract p50, p95, p99 robustly from http_req_duration line
    duration_line = ""
    for line in output.splitlines():
        if "http_req_duration" in line and "avg=" in line:
            duration_line = line
            break
            
    p50 = "0ms"
    p95 = "0ms"
    p99 = "0ms"
    
    if duration_line:
        med_m = re.search(r"med=([\d\.\w]+)", duration_line)
        p95_m = re.search(r"p\(95\)=([\d\.\w]+)", duration_line)
        p99_m = re.search(r"p\(99\)=([\d\.\w]+)", duration_line)
        
        if med_m: p50 = med_m.group(1)
        if p95_m: p95 = p95_m.group(1)
        if p99_m: p99 = p99_m.group(1)
        else:
            # fallback to max if p99 is not found
            max_m = re.search(r"max=([\d\.\w]+)", duration_line)
            if max_m: p99 = max_m.group(1)
            else: p99 = p95
            
    throughput = reqs_sec_match.group(1) if reqs_sec_match else "0"
    failed_percent = failed_reqs_match.group(1) if failed_reqs_match else "0"
    
    return {
        "throughput": f"{float(throughput):,.1f} req/s",
        "p50": p50,
        "p95": p95,
        "p99": p99,
        "failed": f"{failed_percent}%"
    }

def generate_ascii_chart(p50_val, p95_val, p99_val):
    # Strip unit (ms) to get numerical values
    def to_float(val):
        return float(re.sub(r"[^\d\.]", "", val))
        
    v50, v95, v99 = to_float(p50_val), to_float(p95_val), to_float(p99_val)
    max_val = max(v50, v95, v99, 1.0)
    max_chars = 40
    
    def bar(val):
        chars = int((val / max_val) * max_chars)
        return "#" * chars + "-" * (max_chars - chars)

    chart = [
        "```text",
        f"P50 Latency | {bar(v50)} {p50_val}",
        f"P95 Latency | {bar(v95)} {p95_val}",
        f"P99 Latency | {bar(v99)} {p99_val}",
        "```"
    ]
    return "\n".join(chart)

def update_benchmarks_markdown(metrics, chart):
    benchmarks_path = "d:/D/RESUME PROJECTS/Cloud Pool/docs/BENCHMARKS.md"
    if not os.path.exists(benchmarks_path):
        print(f"[-] Benchmarks file not found at {benchmarks_path}")
        return

    with open(benchmarks_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Create new performance report section
    report_section = f"""
## ⚡ Latest Load Test Performance Report (Automated k6 Run)

### Key Metrics
* **Throughput**: {metrics['throughput']}
* **P50 Latency**: {metrics['p50']}
* **P95 Latency**: {metrics['p95']}
* **P99 Latency**: {metrics['p99']}
* **Failed Requests**: {metrics['failed']}

### Latency Distribution Chart
{chart}
"""
    
    # Check if section already exists, replace it, otherwise append
    pattern = r"## ⚡ Latest Load Test Performance Report.*?(?=\n##|$)"
    if re.search(pattern, content, re.DOTALL):
        updated_content = re.sub(pattern, report_section.strip(), content, flags=re.DOTALL)
    else:
        updated_content = content.rstrip() + "\n\n" + report_section

    with open(benchmarks_path, "w", encoding="utf-8") as f:
        f.write(updated_content)
    print(f"[+] Successfully updated {benchmarks_path} with latest load test report.")

def main():
    target = "http://localhost:8080"
    if len(sys.argv) > 1:
        target = sys.argv[1]
        
    output = run_k6_load_test(target)
    metrics = parse_k6_metrics(output)
    
    print("\n" + "="*50)
    print("           k6 LOAD TEST METRICS")
    print("="*50)
    print(f"Throughput      : {metrics['throughput']}")
    print(f"P50 Latency     : {metrics['p50']}")
    print(f"P95 Latency     : {metrics['p95']}")
    print(f"P99 Latency     : {metrics['p99']}")
    print(f"Failed Requests : {metrics['failed']}")
    print("="*50 + "\n")
    
    chart = generate_ascii_chart(metrics['p50'], metrics['p95'], metrics['p99'])
    print(chart)
    print()
    
    update_benchmarks_markdown(metrics, chart)

if __name__ == "__main__":
    main()
