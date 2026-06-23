$ROOT = Join-Path -Path $PSScriptRoot -ChildPath "backend\spring-boot"
$JAVA = "java"
$EXCLUDE = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
$COMMON_PROPS = "--spring.autoconfigure.exclude=$EXCLUDE"

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar (Get-ChildItem "$R\cloudpool-auth\target\cloudpool-auth-*.jar" | Select-Object -First 1).FullName $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 3

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar (Get-ChildItem "$R\cloudpool-data\target\cloudpool-data-*.jar" | Select-Object -First 1).FullName $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 3

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar (Get-ChildItem "$R\cloudpool-compute\target\cloudpool-compute-*.jar" | Select-Object -First 1).FullName $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 3

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar (Get-ChildItem "$R\cloudpool-network\target\cloudpool-network-*.jar" | Select-Object -First 1).FullName $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 5

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar (Get-ChildItem "$R\cloudpool-gateway\target\cloudpool-gateway-*.jar" | Select-Object -First 1).FullName $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS

