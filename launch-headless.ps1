# CloudPool — Local Launch Script
# Starts all microservices in separate PowerShell windows
# No Docker or external databases required (uses H2 embedded DB)

$ROOT = Join-Path -Path $PSScriptRoot -ChildPath "backend\spring-boot"
$JAVA = "java"
$EXCLUDE = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
$COMMON_PROPS = "--spring.autoconfigure.exclude=$EXCLUDE"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   CloudPool Local Launcher" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Auth :8082
Write-Host "[1/5] Starting cloudpool-auth on :8082..." -ForegroundColor Green
$authCmd = "Write-Host 'CloudPool AUTH :8082' -ForegroundColor Yellow; & '$JAVA' -jar (Get-ChildItem '$ROOT\cloudpool-auth\target\cloudpool-auth-*.jar' | Select-Object -First 1).FullName '$COMMON_PROPS'"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $authCmd) -WindowStyle Normal
Start-Sleep -Seconds 3

# Data :8083
Write-Host "[2/5] Starting cloudpool-data on :8083..." -ForegroundColor Green
$dataCmd = "Write-Host 'CloudPool DATA :8083' -ForegroundColor Magenta; & '$JAVA' -jar (Get-ChildItem '$ROOT\cloudpool-data\target\cloudpool-data-*.jar' | Select-Object -First 1).FullName '$COMMON_PROPS'"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $dataCmd) -WindowStyle Normal
Start-Sleep -Seconds 3

# Compute :8084
Write-Host "[3/5] Starting cloudpool-compute on :8084..." -ForegroundColor Green
$computeCmd = "Write-Host 'CloudPool COMPUTE :8084' -ForegroundColor Blue; & '$JAVA' -jar (Get-ChildItem '$ROOT\cloudpool-compute\target\cloudpool-compute-*.jar' | Select-Object -First 1).FullName '$COMMON_PROPS'"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $computeCmd) -WindowStyle Normal
Start-Sleep -Seconds 3

# Network :8085
Write-Host "[4/5] Starting cloudpool-network on :8085..." -ForegroundColor Green
$networkCmd = "Write-Host 'CloudPool NETWORK :8085' -ForegroundColor DarkCyan; & '$JAVA' -jar (Get-ChildItem '$ROOT\cloudpool-network\target\cloudpool-network-*.jar' | Select-Object -First 1).FullName '$COMMON_PROPS'"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $networkCmd) -WindowStyle Normal
Start-Sleep -Seconds 5

# Gateway :8080 — last (proxies to all above)
Write-Host "[5/5] Starting cloudpool-gateway on :8080..." -ForegroundColor Green
$gatewayCmd = "Write-Host 'CloudPool GATEWAY :8080' -ForegroundColor Red; & '$JAVA' -jar (Get-ChildItem '$ROOT\cloudpool-gateway\target\cloudpool-gateway-*.jar' | Select-Object -First 1).FullName"
Start-Process powershell -ArgumentList @("-NoExit", "-Command", $gatewayCmd) -WindowStyle Normal

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
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
