"""Compute service (serverless, containers, cron)."""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from cloudpool._client import CloudPoolClient
from cloudpool.models.compute import (
    ContainerDeployment,
    CronExecution,
    CronJob,
    ServerlessFunction,
    StaticSite,
)


class ComputeClient:
    """Synchronous compute client.

    Manages static sites, serverless functions, container deployments,
    and cron jobs.

    Accessed via ``cloudpool.compute`` on a ``CloudPool`` instance.
    """

    def __init__(self, client: CloudPoolClient) -> None:
        self._client = client

    # ── Static Sites ──

    def deploy_static_site(self, name: str, bucket_name: str, domain: str) -> StaticSite:
        """Deploy a static site.

        Args:
            name: Site name.
            bucket_name: Source bucket for site files.
            domain: Custom domain or subdomain.

        Returns:
            The deployed StaticSite.
        """
        resp = self._client.request(
            "POST", "/api/compute/static",
            json={"name": name, "bucketName": bucket_name, "domain": domain},
        )
        return StaticSite.from_dict(resp)

    def list_static_sites(self) -> List[StaticSite]:
        """List static sites.

        Returns:
            List of StaticSite objects.
        """
        resp = self._client.request("GET", "/api/compute/static")
        return [StaticSite.from_dict(s) for s in (resp if isinstance(resp, list) else [])]

    def delete_static_site(self, site_id: str) -> None:
        """Delete a static site.

        Args:
            site_id: The site's unique identifier.
        """
        self._client.request("DELETE", f"/api/compute/static/{site_id}")

    # ── Serverless Functions ──

    def deploy_serverless_function(
        self,
        name: str,
        trigger_route: str,
        code: str,
        runtime: str = "node18",
    ) -> ServerlessFunction:
        """Deploy a serverless function.

        Args:
            name: Function name.
            trigger_route: HTTP trigger path.
            code: Function source code.
            runtime: Runtime environment (e.g., "node18", "python3").

        Returns:
            The deployed ServerlessFunction.
        """
        resp = self._client.request(
            "POST", "/api/compute/serverless",
            json={
                "name": name,
                "triggerRoute": trigger_route,
                "code": code,
                "runtime": runtime,
            },
        )
        return ServerlessFunction.from_dict(resp)

    def list_serverless_functions(self) -> List[ServerlessFunction]:
        """List serverless functions.

        Returns:
            List of ServerlessFunction objects.
        """
        resp = self._client.request("GET", "/api/compute/serverless")
        return [ServerlessFunction.from_dict(f) for f in (resp if isinstance(resp, list) else [])]

    def execute_serverless_function(
        self,
        function_id: str,
        params: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Execute a serverless function.

        Args:
            function_id: The function's unique identifier.
            params: Optional input parameters.

        Returns:
            Function execution result.
        """
        return self._client.request(
            "POST", f"/api/compute/serverless/{function_id}/execute",
            json=params or {},
        )

    def delete_serverless_function(self, function_id: str) -> None:
        """Delete a serverless function.

        Args:
            function_id: The function's unique identifier.
        """
        self._client.request("DELETE", f"/api/compute/serverless/{function_id}")

    # ── Container Deployments ──

    def deploy_container(
        self,
        name: str,
        docker_image: str,
        cpu: str = "0.5",
        memory: str = "512Mi",
        replicas: int = 1,
    ) -> ContainerDeployment:
        """Deploy a container.

        Args:
            name: Deployment name.
            docker_image: Docker image URL.
            cpu: CPU allocation (e.g., "0.5", "1").
            memory: Memory allocation (e.g., "512Mi", "1Gi").
            replicas: Number of replicas.

        Returns:
            The deployed ContainerDeployment.
        """
        resp = self._client.request(
            "POST", "/api/compute/container",
            json={
                "name": name,
                "dockerImage": docker_image,
                "cpu": cpu,
                "memory": memory,
                "replicas": replicas,
            },
        )
        return ContainerDeployment.from_dict(resp)

    def list_containers(self) -> List[ContainerDeployment]:
        """List container deployments.

        Returns:
            List of ContainerDeployment objects.
        """
        resp = self._client.request("GET", "/api/compute/container")
        return [ContainerDeployment.from_dict(c) for c in (resp if isinstance(resp, list) else [])]

    def delete_container(self, container_id: str) -> None:
        """Delete a container deployment.

        Args:
            container_id: The container's unique identifier.
        """
        self._client.request("DELETE", f"/api/compute/container/{container_id}")

    # ── Cron Jobs ──

    def list_cron_jobs(self, project_id: str) -> List[CronJob]:
        """List cron jobs for a project.

        Args:
            project_id: The project's unique identifier.

        Returns:
            List of CronJob objects.
        """
        resp = self._client.request("GET", f"/api/v1/projects/{project_id}/cron")
        return [CronJob.from_dict(j) for j in (resp if isinstance(resp, list) else [])]

    def create_cron_job(
        self,
        project_id: str,
        name: str,
        cron_expression: str,
        target_url: str,
        http_method: str = "GET",
        payload: Optional[str] = None,
        headers: Optional[Dict[str, str]] = None,
        is_active: bool = True,
    ) -> CronJob:
        """Create a cron job.

        Args:
            project_id: The project's unique identifier.
            name: Job name.
            cron_expression: Cron schedule expression.
            target_url: HTTP endpoint URL.
            http_method: HTTP method (GET, POST, etc.).
            payload: Optional request body.
            headers: Optional request headers.
            is_active: Whether the job starts active.

        Returns:
            The created CronJob.
        """
        body: Dict[str, Any] = {
            "name": name,
            "cronExpression": cron_expression,
            "targetUrl": target_url,
            "httpMethod": http_method,
            "isActive": is_active,
        }
        if payload:
            body["payload"] = payload
        if headers:
            body["headers"] = headers
        resp = self._client.request(
            "POST", f"/api/v1/projects/{project_id}/cron", json=body,
        )
        return CronJob.from_dict(resp)

    def delete_cron_job(self, project_id: str, job_id: str) -> None:
        """Delete a cron job.

        Args:
            project_id: The project's unique identifier.
            job_id: The job's unique identifier.
        """
        self._client.request("DELETE", f"/api/v1/projects/{project_id}/cron/{job_id}")

    def get_cron_executions(self, project_id: str, job_id: str) -> List[CronExecution]:
        """Get execution history for a cron job.

        Args:
            project_id: The project's unique identifier.
            job_id: The job's unique identifier.

        Returns:
            List of CronExecution objects.
        """
        resp = self._client.request(
            "GET", f"/api/v1/projects/{project_id}/cron/{job_id}/executions",
        )
        return [CronExecution.from_dict(e) for e in (resp if isinstance(resp, list) else [])]


class AsyncComputeClient:
    """Asynchronous compute client."""

    def __init__(self, client: Any) -> None:
        self._client = client

    async def deploy_serverless_function(self, name: str, trigger_route: str, code: str) -> ServerlessFunction:
        resp = await self._client.request("POST", "/api/compute/serverless", json={"name": name, "triggerRoute": trigger_route, "code": code})
        return ServerlessFunction.from_dict(resp)

    async def list_serverless_functions(self) -> List[ServerlessFunction]:
        resp = await self._client.request("GET", "/api/compute/serverless")
        return [ServerlessFunction.from_dict(f) for f in (resp if isinstance(resp, list) else [])]

    async def execute_serverless_function(self, function_id: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        return await self._client.request("POST", f"/api/compute/serverless/{function_id}/execute", json=params or {})

    async def delete_serverless_function(self, function_id: str) -> None:
        await self._client.request("DELETE", f"/api/compute/serverless/{function_id}")

    async def list_cron_jobs(self, project_id: str) -> List[CronJob]:
        resp = await self._client.request("GET", f"/api/v1/projects/{project_id}/cron")
        return [CronJob.from_dict(j) for j in (resp if isinstance(resp, list) else [])]

    async def create_cron_job(self, project_id: str, name: str, cron_expression: str, target_url: str, http_method: str = "GET") -> CronJob:
        resp = await self._client.request("POST", f"/api/v1/projects/{project_id}/cron", json={
            "name": name, "cronExpression": cron_expression, "targetUrl": target_url, "httpMethod": http_method,
        })
        return CronJob.from_dict(resp)

    async def delete_cron_job(self, project_id: str, job_id: str) -> None:
        await self._client.request("DELETE", f"/api/v1/projects/{project_id}/cron/{job_id}")
