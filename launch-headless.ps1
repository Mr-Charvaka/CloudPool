# CloudPool — Local Launch Script
# Starts all microservices in separate PowerShell windows
# No Docker or external databases required (uses H2 embedded DB)

$ROOT = Join-Path -Path $PSScriptRoot -ChildPath "backend\spring-boot"
$JAVA = "java"
$EXCLUDE = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
$COMMON_PROPS = "--spring.autoconfigure.exclude=$EXCLUDE"

$env:JWT_SECRET="local-development-secret-key-that-is-at-least-64-characters-long-for-hs512-algorithm"
$env:SPRING_DATASOURCE_URL="jdbc:h2:file:./data/cloudpooldb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;AUTO_SERVER=TRUE"
$env:SPRING_DATASOURCE_USERNAME="sa"
$env:SPRING_DATASOURCE_PASSWORD=""
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME="org.h2.Driver"
$env:SPRING_JPA_DATABASE_PLATFORM="org.hibernate.dialect.H2Dialect"
$env:CLOUDPOOL_ENCRYPTION_MASTER_KEY="MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
$env:CLOUDPOOL_ENCRYPTION_SALT="MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
$env:CLOUDPOOL_DRAP_EXECUTABLE_PATH="./drap.exe"
$env:CLOUDPOOL_DRAP_ENABLED="false"
if (-not (Test-Path "logs")) { New-Item -ItemType Directory -Path "logs" | Out-Null }

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   CloudPool Headless Launcher" -ForegroundColor Cyan
Write-Host "   Logs will be saved to .\logs\" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Data :8083
Write-Host "[1/5] Starting cloudpool-data in background..." -ForegroundColor Green
$dataJar = (Get-ChildItem "$ROOT\cloudpool-data\target\cloudpool-data-*.jar" | Select-Object -First 1).FullName
Start-Process $JAVA -ArgumentList "-jar `"$dataJar`" $COMMON_PROPS" -RedirectStandardOutput "logs\data.log" -RedirectStandardError "logs\data.err" -WindowStyle Hidden
Start-Sleep -Seconds 10

# Auth :8082
Write-Host "[2/5] Starting cloudpool-auth in background..." -ForegroundColor Green
$authJar = (Get-ChildItem "$ROOT\cloudpool-auth\target\cloudpool-auth-*.jar" | Select-Object -First 1).FullName
Start-Process $JAVA -ArgumentList "-jar `"$authJar`" $COMMON_PROPS" -RedirectStandardOutput "logs\auth.log" -RedirectStandardError "logs\auth.err" -WindowStyle Hidden
Start-Sleep -Seconds 3

# Compute :8084
Write-Host "[3/5] Starting cloudpool-compute in background..." -ForegroundColor Green
$computeJar = (Get-ChildItem "$ROOT\cloudpool-compute\target\cloudpool-compute-*.jar" | Select-Object -First 1).FullName
Start-Process $JAVA -ArgumentList "-jar `"$computeJar`" $COMMON_PROPS" -RedirectStandardOutput "logs\compute.log" -RedirectStandardError "logs\compute.err" -WindowStyle Hidden
Start-Sleep -Seconds 3

# Network :8085
Write-Host "[4/5] Starting cloudpool-network in background..." -ForegroundColor Green
$networkJar = (Get-ChildItem "$ROOT\cloudpool-network\target\cloudpool-network-*.jar" | Select-Object -First 1).FullName
Start-Process $JAVA -ArgumentList "-jar `"$networkJar`" $COMMON_PROPS" -RedirectStandardOutput "logs\network.log" -RedirectStandardError "logs\network.err" -WindowStyle Hidden
Start-Sleep -Seconds 3

# Gateway :8080 — last (proxies to all above)
Write-Host "[5/5] Starting cloudpool-gateway in background..." -ForegroundColor Green
$gatewayJar = (Get-ChildItem "$ROOT\cloudpool-gateway\target\cloudpool-gateway-*.jar" | Select-Object -First 1).FullName
Start-Process $JAVA -ArgumentList "-jar `"$gatewayJar`"" -RedirectStandardOutput "logs\gateway.log" -RedirectStandardError "logs\gateway.err" -WindowStyle Hidden

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan

while ($true) { Start-Sleep -Seconds 10 }
Write-Host " All services launching in new windows!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Dashboard:          http://localhost:8080/index.html" -ForegroundColor White
Write-Host " GraphQL Playground: http://localhost:8080/graphiql" -ForegroundColor White
Write-Host " H2 Console:         http://localhost:8080/h2-console" -ForegroundColor White
Write-Host " Auth health:        http://localhost:8082/actuator/health" -ForegroundColor White
Write-Host " Data health:        http://localhost:8083/actuator/health" -ForegroundColor White
Write-Host " Compute health:     http://localhost:8084/actuator/health" -ForegroundColor White
Write-Host " Network health:     http://localhost:8085/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "Allow 20-30 seconds for all services to fully start." -ForegroundColor Yellow
