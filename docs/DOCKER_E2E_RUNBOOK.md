# Docker E2E Runbook: Local SaaS Provisioning Pipeline

This runbook guides you through verifying the complete multi-tenant SaaS provisioning pipeline locally using Docker. By utilizing a multi-stage containerized setup, the provisioning system compiles code internally and accesses the host's Docker socket to spawn isolated PG Core instances on demand.

---

## 1. Pre-Flight Checks (The Clean Slate)

To prevent port allocation conflicts, ensure that all host-level services and legacy processes are fully terminated:

* **Stop Host PostgreSQL Instances:** If you have a local PostgreSQL database running on your host machine, stop it to free up **Port 5432**.
  ```powershell
  # Windows Example (Run as Administrator)
  Stop-Service -Name postgresql*
  ```
* **Kill Legacy Servers:** Close any running instances of local servers started by bare-metal `.bat` scripts (e.g., Spring Boot or Vite Node.js servers running on ports `8090`, `5176`, or `8081`).
* **Verify Docker Desktop:** Ensure that **Docker Desktop is active and running** on your system.

---

## 2. Local DNS Configuration (One-Time Setup)

To resolve subdomains dynamically for your tenant apps, you must map the master test domain to your localhost loopback interface.

1. Open **Notepad** (or your favorite text editor) **as Administrator**.
2. Open the Windows hosts file: `C:\Windows\System32\drivers\etc\hosts`.
3. Add the following entry to the end of the file:
   ```text
   127.0.0.1       test.pgcrm.com
   ```
4. Save and close the file.

---

## 3. Ignition (Launching the Cluster)

With the environment clean and DNS mapped, initialize the container cluster:

1. Open a terminal at the monorepo root directory: `E:\Antigravity Project\PG Project`.
2. Run the build and start command:
   ```bash
   docker compose up --build -d
   ```
   > [!NOTE]
   > The multi-stage build configured in the [backend Dockerfile](file:///E:/Antigravity%20Project/PG%20Project/master-control-plane/backend/Dockerfile) and [frontend Dockerfile](file:///E:/Antigravity%20Project/PG%20Project/master-control-plane/frontend/Dockerfile) compiles the source code completely inside the build containers. **Zero pre-flight Maven or npm commands are required on your host machine.**

3. Validate that the services are online using:
   ```bash
   docker compose ps
   ```

---

## 4. The E2E Execution Steps

Once the containers are running, perform the following verification flow:

### **Step 1: Verify the Command Center**
Navigate to the Admin Dashboard at [http://localhost:5176/dashboard](http://localhost:5176/dashboard) to confirm the admin control panel is responsive and successfully querying the backend API.

### **Step 2: Run the Onboarding Wizard**
Open the Tenant Onboarding Wizard at [http://localhost:5176/](http://localhost:5176/). 
* Complete the multi-step onboarding wizard.
* Set the workspace subdomain to `test`.

### **Step 3: Monitor Provisioning Status**
Return to the Admin Dashboard at [http://localhost:5176/dashboard](http://localhost:5176/dashboard). You should see:
1. A new tenant record for the subdomain `test` initialized in the `PROVISIONING` state.
2. The async orchestrator executes the backend provisioning scripts via the mounted Docker socket (`/var/run/docker.sock`).
3. The dashboard state will poll and transition to `LIVE` once the container launches and registers.

### **Step 4: Access the Tenant Application**
Open a new browser tab and navigate to [http://test.pgcrm.com:8081](http://test.pgcrm.com:8081).
* Verify that the isolated PG Core application is fully loaded and functional.
* This verifies that the reverse proxy, database migration scripts, and dynamic container creation executed correctly.

---

## 5. Legacy Fallback Note

> [!TIP]
> The original `.bat` startup scripts in the monorepo remain fully intact and operational. If you lack a Docker Desktop environment or need to attach debugger utilities directly, you can still fall back to bare-metal local execution.
