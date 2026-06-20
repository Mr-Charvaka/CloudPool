$ROOT = "d:\D\RESUME PROJECTS\Cloud Pool\backend\spring-boot"
$JAVA = "C:\Users\aman7\.jdks\openjdk-26.0.1\bin\java.exe"
$EXCLUDE = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
$COMMON_PROPS = "--spring.autoconfigure.exclude=$EXCLUDE"

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar "$R\cloudpool-auth\target\cloudpool-auth-0.1.0-SNAPSHOT.jar" $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 3

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar "$R\cloudpool-data\target\cloudpool-data-0.1.0-SNAPSHOT.jar" $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 3

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar "$R\cloudpool-compute\target\cloudpool-compute-0.1.0-SNAPSHOT.jar" $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 3

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar "$R\cloudpool-network\target\cloudpool-network-0.1.0-SNAPSHOT.jar" $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS
Start-Sleep -Seconds 5

Start-Job -ScriptBlock { param($J, $R, $C) & $J -jar "$R\cloudpool-gateway\target\cloudpool-gateway-0.1.0-SNAPSHOT.jar" $C } -ArgumentList $JAVA, $ROOT, $COMMON_PROPS

