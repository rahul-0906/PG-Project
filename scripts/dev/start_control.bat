@echo off
echo Launching Master Control Plane Full Stack...
start "Control Plane Backend" cmd /k "cd /d "%~dp0..\..\master-control-plane\backend" && mvn spring-boot:run"
start "Control Plane Frontend" cmd /k "cd /d "%~dp0..\..\master-control-plane\frontend" && (if not exist node_modules call npm install) && npm run dev"
exit
