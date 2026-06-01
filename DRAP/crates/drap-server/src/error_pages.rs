pub const HTML_404: &str = r#"
<!DOCTYPE html>
<html>
<head>
    <title>404 - Tunnel Not Found</title>
    <style>
        body { font-family: 'Inter', sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
        .card { background: #1e293b; padding: 2rem; border-radius: 12px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); max-width: 400px; text-align: center; border: 1px solid #334155; }
        h1 { color: #38bdf8; margin-bottom: 0.5rem; }
        p { color: #94a3b8; line-height: 1.6; }
        .logo { font-weight: bold; font-size: 1.2rem; color: #38bdf8; margin-bottom: 1rem; display: block; }
    </style>
</head>
<body>
    <div class="card">
        <span class="logo">D-RAP Tunnel</span>
        <h1>Tunnel Not Found</h1>
        <p>The subdomain you're trying to reach doesn't have an active tunnel connected. If you're the developer, make sure your CLI client is running.</p>
    </div>
</body>
</html>
"#;

pub const HTML_502: &str = r#"
<!DOCTYPE html>
<html>
<head>
    <title>502 - Bad Gateway</title>
    <style>
        body { font-family: 'Inter', sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
        .card { background: #1e293b; padding: 2rem; border-radius: 12px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); max-width: 400px; text-align: center; border: 1px solid #ef4444; }
        h1 { color: #ef4444; margin-bottom: 0.5rem; }
        p { color: #94a3b8; line-height: 1.6; }
        .logo { font-weight: bold; font-size: 1.2rem; color: #ef4444; margin-bottom: 1rem; display: block; }
    </style>
</head>
<body>
    <div class="card">
        <span class="logo">D-RAP Tunnel</span>
        <h1>Bad Gateway</h1>
        <p>The D-RAP relay established a connection to your local machine, but your application didn't respond or is not running on the expected port.</p>
    </div>
</body>
</html>
"#;

pub const HTML_429: &str = r#"
<!DOCTYPE html>
<html>
<head>
    <title>429 - Too Many Requests</title>
    <style>
        body { font-family: 'Inter', sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
        .card { background: #1e293b; padding: 2rem; border-radius: 12px; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); max-width: 400px; text-align: center; border: 1px solid #fbbf24; }
        h1 { color: #fbbf24; margin-bottom: 0.5rem; }
        p { color: #94a3b8; line-height: 1.6; }
        .logo { font-weight: bold; font-size: 1.2rem; color: #fbbf24; margin-bottom: 1rem; display: block; }
    </style>
</head>
<body>
    <div class="card">
        <span class="logo">D-RAP Tunnel</span>
        <h1>Slow Down</h1>
        <p>This tunnel has exceeded its allocated rate limit. Please wait a moment before trying again or upgrade your plan.</p>
    </div>
</body>
</html>
"#;
