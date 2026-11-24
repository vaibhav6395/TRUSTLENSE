# Development environment setup

This guide installs the tools required by the current TRUSTLENS full-stack vertical slice. It covers Kali Linux (team lead) and Windows 10/11 (teammates).

## Verified project requirements

Checked against `backend/pom.xml`, `frontend/package.json`, and `docker-compose.yml` on `main`:

| Tool | Requirement | Why |
|---|---|---|
| Git | Current stable | Clone and sync the repository |
| JDK | Java 21 | Backend uses Spring Boot 3.4.4 and `java.version=21` |
| Apache Maven | 3.9+ recommended | Build and run the backend |
| Node.js | 20+; use an active LTS release | Frontend uses Vite and npm |
| npm | Bundled with Node.js | Install frontend dependencies |
| Docker Engine + Compose v2 | Current stable | Run PostgreSQL 16 from Compose |
| PostgreSQL | Provided by Docker | Database container; no separate host install needed |

> Install from official vendor sources or your operating system's trusted package manager. Review installer prompts. Do not run unknown install scripts copied from chat messages.

---

## A. Kali Linux

Open a terminal.

### 1. Update package lists and install common tools

```bash
sudo apt update
sudo apt install -y git curl ca-certificates gnupg openjdk-21-jdk maven
```

Check versions:

```bash
git --version
java -version
javac -version
mvn --version
```

The Java output must show version 21.

### 2. Install Node.js and npm

First check whether Kali's package version meets the project requirement:

```bash
sudo apt install -y nodejs npm
node --version
npm --version
```

Node.js must be version 20 or newer. If Kali's package is older, install an active LTS release from the official Node.js download page:

https://nodejs.org/en/download/

Choose the Linux binary that matches your CPU architecture, verify the published checksum/signature, and follow the official installation instructions. Open a new terminal and re-check `node --version` and `npm --version`.

### 3. Install Docker Engine and Compose v2

The following uses Docker's official Debian repository with the Debian 13 (Trixie) package suite, as documented for current Kali installations. If your Kali base uses a different Debian stable suite, check the Kali and Docker installation documentation before changing the suite.

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<'EOF'
Types: deb
URIs: https://download.docker.com/linux/debian
Suites: trixie
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

If your computer is ARM-based, replace `amd64` with the architecture reported by:

```bash
dpkg --print-architecture
```

Verify Docker and Compose:

```bash
sudo docker --version
sudo docker compose version
sudo docker run --rm hello-world
```

**Security note:** Docker daemon access is highly privileged. This guide intentionally uses `sudo docker` rather than automatically adding your account to the `docker` group. Membership in that group effectively grants root-level control of the machine.

Official references:
- Kali Docker guide: https://www.kali.org/docs/containers/installing-docker-on-kali/
- Docker Debian install: https://docs.docker.com/engine/install/debian/
- Docker Compose install: https://docs.docker.com/compose/install/linux/

---

## B. Windows 10/11 (PowerShell)

Use an up-to-date Windows version. Open PowerShell. The commands below use Windows Package Manager (`winget`). If `winget` is not available, install/update **App Installer** from Microsoft Store first.

### 1. Install Git, Java 21, Maven, and Node.js LTS

```powershell
winget install --exact --id Git.Git
winget install --exact --id EclipseAdoptium.Temurin.21.JDK
winget install --exact --id Apache.Maven
winget install --exact --id OpenJS.NodeJS.LTS
```

If a package ID is not found by your local winget source, search it with:

```powershell
winget search Git
winget search Temurin
winget search Maven
winget search Node.js
```

Close and reopen PowerShell after installation, then verify:

```powershell
git --version
java -version
javac -version
mvn --version
node --version
npm --version
```

Java must report version 21 and Node.js must report version 20 or newer.

### 2. Install Docker Desktop

```powershell
winget install --exact --id Docker.DockerDesktop
```

Start Docker Desktop from the Start menu. If prompted, use the WSL 2 backend and complete any WSL setup/restart requested by the installer.

Verify in a new PowerShell window:

```powershell
docker --version
docker compose version
docker run --rm hello-world
```

Official references:
- Docker Desktop for Windows: https://docs.docker.com/desktop/setup/install/windows-install/
- Eclipse Temurin: https://adoptium.net/installation/
- Maven: https://maven.apache.org/install/
- Node.js: https://nodejs.org/en/download/

---

## C. Get the project

Clone the branch that currently contains the full-stack work:

```bash
git clone --single-branch --branch main git@github.com:vaibhav6395/TRUSTLENSE.git
cd TRUSTLENSE
```

For HTTPS instead of SSH:

```bash
git clone --single-branch --branch main https://github.com/vaibhav6395/TRUSTLENSE.git
```

For a private repository, authenticate with SSH or a GitHub Personal Access Token. GitHub account passwords are not accepted for HTTPS Git operations.

## D. Start TRUSTLENS

Run these commands from the repository root.

### 1. Create a local environment file from the template and replace its sample values:

```bash
cp .env.example .env
```

The Compose database reads its password from `.env`. Never commit the populated `.env` file.

2. Start PostgreSQL

Kali/Linux:

```bash
sudo docker compose up -d db
sudo docker compose ps
```

Windows PowerShell:

```powershell
docker compose up -d db
docker compose ps
```

### 3. Start the backend

Kali/Linux:

```bash
set -a
source ../.env
set +a
DEMO_DATA_ENABLED=true COOKIE_SECURE=false mvn spring-boot:run
```

Windows PowerShell:

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/trustlens"
$env:DB_USERNAME = "trustlens"
$env:DB_PASSWORD = "the-same-value-as-POSTGRES_PASSWORD-in-your-local-env-file"
$env:DEMO_DATA_ENABLED = "true"
$env:DEMO_FACULTY_PASSWORD = "your-unique-password-at-least-14-characters"
$env:DEMO_ADMIN_PASSWORD = "a-different-unique-password-at-least-14-characters"
$env:COOKIE_SECURE = "false"
mvn spring-boot:run
```

### 4. Start the frontend

Open a second terminal at the repository root.

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173.

The local demo data is synthetic and intended only for a disposable development database. Both demo passwords must be supplied as environment variables and must be at least 14 characters. Demo seeding is disabled by default; do not enable it in a deployed or shared environment. Never commit real credentials, tokens, or populated `.env` files.

To stop the database container:

```bash
docker compose down
```

To remove the local database volume as well (this permanently deletes the local PostgreSQL data):

```bash
docker compose down --volumes
```

## E. Final environment check

Run these commands and share the output if setup fails:

```bash
git --version
java -version
mvn --version
node --version
npm --version
docker --version
docker compose version
```

On Linux, prefix Docker commands with `sudo` if your account is not configured for Docker access.

## Troubleshooting

- **Java version mismatch:** check `JAVA_HOME` and make sure Java 21 is first on your PATH.
- **Maven cannot find Java:** install a JDK (not only a JRE), then open a new terminal.
- **Node is below 20:** install an active LTS version from the official Node.js download page.
- **Docker daemon unavailable on Kali:** run `sudo systemctl status docker`, then `sudo systemctl start docker`.
- **Docker Desktop unavailable on Windows:** start Docker Desktop and wait until its engine is ready; check WSL 2 setup if requested.
- **Port already in use:** check whether another PostgreSQL or application process is using ports 5432, 8080, or 5173 before changing configuration.
