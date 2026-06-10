import requests
from typing import Dict, Any, List, Optional

class CloudPoolError(Exception):
    """Exception raised for errors in the CloudPool SDK."""
    def __init__(self, message: str, status_code: Optional[int] = None):
        super().__init__(message)
        self.status_code = status_code


class DatabaseClient:
    def __init__(self, client: "CloudPoolClient"):
        self.client = client

    def create_table(self, name: str, display_name: str, description: str, fields: List[Dict[str, Any]], project_id: Optional[str] = None) -> Dict[str, Any]:
        """Provision a new dynamic relational database table."""
        body = {
            "name": name,
            "displayName": display_name,
            "description": description,
            "fields": fields
        }
        if project_id:
            body["projectId"] = project_id

        return self.client.request("POST", "v1/db/tables", json=body)

    def list_tables(self, project_id: Optional[str] = None) -> List[Dict[str, Any]]:
        """List all custom/dynamic database tables."""
        path = "v1/db/tables"
        if project_id:
            path += f"?projectId={project_id}"
        return self.client.request("GET", path)

    def get_table(self, table_id: str) -> Dict[str, Any]:
        """Get specific custom table definition metadata by ID."""
        return self.client.request("GET", f"v1/db/tables/{table_id}")

    def delete_table(self, table_id: str) -> Dict[str, Any]:
        """Delete custom relational table and drops database structure."""
        return self.client.request("DELETE", f"v1/db/tables/{table_id}")

    def insert_record(self, table_id: str, record: Dict[str, Any]) -> Dict[str, Any]:
        """Insert record row into a dynamic custom database table."""
        return self.client.request("POST", f"v1/db/tables/{table_id}/records", json=record)

    def query_records(self, table_id: str) -> List[Dict[str, Any]]:
        """Query and list all records inside a custom relational database table."""
        return self.client.request("GET", f"v1/db/tables/{table_id}/records")


class FilesClient:
    def __init__(self, client: "CloudPoolClient"):
        self.client = client

    def upload_file(self, bucket_id: str, file_name: str, file_content: bytes, content_type: str = "application/octet-stream") -> Dict[str, Any]:
        """Upload a file to a storage bucket."""
        files = {"file": (file_name, file_content, content_type)}
        return self.client.request("POST", f"v1/storage/buckets/{bucket_id}/files", files=files)


class CloudPoolClient:
    def __init__(self, base_url: str = "http://localhost:8080/api", api_key: Optional[str] = None, jwt_token: Optional[str] = None):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.jwt_token = jwt_token
        self.database = DatabaseClient(self)
        self.files = FilesClient(self)

    def request(self, method: str, path: str, **kwargs) -> Any:
        """Helper to perform authenticated HTTP requests."""
        url = f"{self.base_url}/{path.lstrip('/')}"
        
        headers = kwargs.pop("headers", {})
        if self.api_key:
            headers["X-API-KEY"] = self.api_key
        elif self.jwt_token:
            headers["Authorization"] = f"Bearer {self.jwt_token}"

        try:
            response = requests.request(method, url, headers=headers, **kwargs)
            return self._handle_response(response)
        except requests.RequestException as e:
            raise CloudPoolError(f"HTTP Request failed: {e}")

    def _handle_response(self, response: requests.Response) -> Any:
        if not response.ok:
            error_msg = response.reason
            try:
                json_data = response.json()
                if "error" in json_data:
                    error_msg = json_data["error"]
            except Exception:
                if response.text:
                    error_msg = response.text
            raise CloudPoolError(f"CloudPool API Error [{response.status_code}]: {error_msg}", response.status_code)
        
        try:
            return response.json()
        except ValueError:
            return response.text
