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

In our multi-tenant SaaS architecture, the Spring Boot Control Plane dynamic orchestrator provisions isolated PG Core containers mapped to subdomains under a master domain structure. Since standard public DNS servers cannot route local requests to dynamically generated containers running on your host, you must configure a local DNS mapping via your system's `hosts` file. This intercepts browser requests and forces resolution to the localhost interface.

> [!CAUTION]
> **Safety Warning:** The system `hosts` file is critical for all local networking. Ensure you only append the requested lines. Editing or deleting existing entries can disrupt system network resolution, active VPN connections, or local development environments.

### **Configuration Steps:**

1. **Launch a text editor with administrative privileges:** Search for **Notepad** (or your preferred editor) in your system search bar, right-click the application icon, and select **Run as Administrator**.
2. **Open the system hosts file:** In the editor, browse to and open the Windows `hosts` configuration file:
   ```text
   C:\Windows\System32\drivers\etc\hosts
   ```
3. **Append the local DNS mappings:** Navigate to the bottom of the file and paste the following block. This includes our main E2E testing domain along with several placeholders to accommodate future multi-tenant testing scenarios:
   ```text
   # B2B SaaS local multi-tenant routing
   127.0.0.1       test.pgcrm.com
   127.0.0.1       stanza.pgcrm.com
   127.0.0.1       dev-tenant.pgcrm.com
   ```
4. **Save and close the file:** Save the changes (`Ctrl + S`) and close your editor.

### **Verification Step:**
Validate that your operating system resolves the mapped domains correctly by executing a network test in your terminal:
```powershell
ping test.pgcrm.com
```
**Expected Outcome:** The command should initiate requests resolving to `127.0.0.1` (or `::1`). If it displays the correct IP loopback address, DNS resolution has taken effect immediately. If the domain fails to resolve, flush your system DNS resolver cache and re-verify your modifications:
```powershell
ipconfig /flushdns
```

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
