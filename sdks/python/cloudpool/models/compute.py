"""Data models for compute services."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class StaticSite:
    """A deployed static site.

    Attributes:
        id: Unique site identifier.
        name: Site name.
        bucket_name: Source bucket for static files.
        domain: Custom domain or assigned URL.
        status: Deployment status.
        created_at: Creation timestamp.
    """

    id: str
    name: str
    bucket_name: str = ""
    domain: str = ""
    status: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "StaticSite":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            bucket_name=d.get("bucketName", ""),
            domain=d.get("domain", ""),
            status=d.get("status", ""),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class ServerlessFunction:
    """A serverless function deployment.

    Attributes:
        id: Unique function identifier.
        name: Function name.
        trigger_route: HTTP trigger route.
        status: Deployment status (e.g., "active", "deploying").
        created_at: Creation timestamp.
    """

    id: str
    name: str
    trigger_route: str = ""
    status: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ServerlessFunction":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            trigger_route=d.get("triggerRoute", ""),
            status=d.get("status", ""),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class ContainerDeployment:
    """A container-based deployment.

    Attributes:
        id: Unique deployment identifier.
        name: Deployment name.
        docker_image: Docker image URL.
        cpu: CPU allocation (e.g., "0.5", "1").
        memory: Memory allocation (e.g., "512Mi", "1Gi").
        replicas: Number of running replicas.
        status: Deployment status.
        created_at: Creation timestamp.
    """

    id: str
    name: str
    docker_image: str = ""
    cpu: str = "0.5"
    memory: str = "512Mi"
    replicas: int = 1
    status: str = ""
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ContainerDeployment":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            docker_image=d.get("dockerImage", ""),
            cpu=d.get("cpu", "0.5"),
            memory=d.get("memory", "512Mi"),
            replicas=d.get("replicas", 1),
            status=d.get("status", ""),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class CronJob:
    """A scheduled cron job.

    Attributes:
        id: Unique job identifier.
        name: Job name.
        cron_expression: Cron schedule expression.
        target_url: HTTP endpoint to invoke.
        http_method: HTTP method for the request.
        is_active: Whether the job is active.
        created_at: Creation timestamp.
    """

    id: str
    name: str
    cron_expression: str = ""
    target_url: str = ""
    http_method: str = "GET"
    is_active: bool = True
    created_at: str = ""

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "CronJob":
        return cls(
            id=d["id"],
            name=d.get("name", ""),
            cron_expression=d.get("cronExpression", ""),
            target_url=d.get("targetUrl", ""),
            http_method=d.get("httpMethod", "GET"),
            is_active=d.get("active", d.get("isActive", True)),
            created_at=d.get("createdAt", ""),
        )


@dataclass
class CronExecution:
    """A single execution of a cron job.

    Attributes:
        id: Unique execution identifier.
        job_id: The cron job that was executed.
        status: Execution status (e.g., "success", "failed").
        triggered_at: When the execution was triggered.
        response_status: HTTP response status from the target.
    """

    id: str
    job_id: str
    status: str = ""
    triggered_at: str = ""
    response_status: Optional[int] = None

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "CronExecution":
        return cls(
            id=d["id"],
            job_id=d.get("jobId", ""),
            status=d.get("status", ""),
            triggered_at=d.get("triggeredAt", ""),
            response_status=d.get("responseStatus"),
        )
