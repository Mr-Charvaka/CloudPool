# Incident Runbook

## Database Crash

```bash
# Check Postgres status
kubectl exec -n cloudpool deployment/postgres -- pg_isready

# View logs
kubectl logs -n cloudpool deployment/postgres --tail=100

# Restart Postgres
kubectl rollout restart -n cloudpool deployment/postgres

# If data corruption, restore from latest backup
# 1. Scale down services
kubectl scale deployment -n cloudpool --all --replicas=0

# 2. Restore database
PGPASSWORD=$POSTGRES_PASSWORD pg_restore -h postgres -U cloudpool -d cloudpool \
  --clean --if-exists s3://cloudpool-backups/daily/$(date +%Y-%m-%d)/dump.sql

# 3. Re-run migrations
kubectl create job -n cloudpool --from=cronjob/flyway-migrate manual-migrate

# 4. Scale back up
kubectl scale deployment -n cloudpool --all --replicas=1
```

## Disk Full

```bash
# Check disk usage
kubectl exec -n cloudpool deployment/cloudpool-api -- df -h

# List largest files
kubectl exec -n cloudpool deployment/cloudpool-api -- \
  find /data -type f -size +100M -exec ls -lh {} \; | sort -k5 -h

# Clear temp files
kubectl exec -n cloudpool deployment/cloudpool-api -- \
  find /tmp -type f -atime +1 -delete

# Increase PVC size (if using dynamic provisioning)
kubectl edit pvc -n cloudpool cloudpool-data
# Change: resources.requests.storage: 10Gi -> 50Gi
```

## API Unresponsive

```bash
# Check pod status
kubectl get pods -n cloudpool
kubectl describe pod -n cloudpool -l app=cloudpool-api

# Check logs
kubectl logs -n cloudpool deployment/cloudpool-api --tail=50

# Check resource usage
kubectl top pods -n cloudpool

# Restart if OOM or hung
kubectl rollout restart -n cloudpool deployment/cloudpool-api

# If deployment is stuck, force rollback
kubectl rollout undo -n cloudpool deployment/cloudpool-api
```

## Redis Down

```bash
# Check Redis
kubectl exec -n cloudpool deployment/redis -- redis-cli ping

# Restart
kubectl rollout restart -n cloudpool deployment/redis

# If persistent data loss, cache will warm naturally
```

## RabbitMQ Down

```bash
# Check status
kubectl exec -n cloudpool deployment/rabbitmq -- rabbitmqctl status

# Restart
kubectl rollout restart -n cloudpool deployment/rabbitmq

# Check for stuck queues
kubectl exec -n cloudpool deployment/rabbitmq -- rabbitmqctl list_queues
```

## Weaviate Down (Vector Search Fails)

```bash
# Check Weaviate
kubectl exec -n cloudpool deployment/weaviate -- wget -qO- http://localhost:8080/v1/.well-known/ready

# Restart
kubectl rollout restart -n cloudpool deployment/weaviate

# VectorService has a Java-side cosine fallback — performance degrades but doesn't crash
```

## Emergency Commands

```bash
# Full restart of all services (ordered)
kubectl rollout restart -n cloudpool deployment/postgres
kubectl rollout status -n cloudpool deployment/postgres
kubectl rollout restart -n cloudpool deployment/redis deployment/rabbitmq deployment/weaviate
kubectl rollout status -n cloudpool deployment/redis deployment/rabbitmq deployment/weaviate
kubectl rollout restart -n cloudpool deployment/cloudpool-api
kubectl rollout status -n cloudpool deployment/cloudpool-api

# Scale everything to zero (maintenance mode)
kubectl scale deployment -n cloudpool --all --replicas=0

# Drain a node safely
kubectl drain <node-name> --ignore-daemonsets --delete-emptydir-data
kubectl uncordon <node-name>
```