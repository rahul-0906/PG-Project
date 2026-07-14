@echo off
echo Launching Core PG CRM Full Stack...
start "Core CRM Backend" cmd /k "cd /d "%~dp0..\..\core-pg-crm\backend" && mvn spring-boot:run"
start "Core CRM Frontend" cmd /k "cd /d "%~dp0..\..\core-pg-crm\frontend" && (if not exist node_modules call npm install) && npm run dev"
exit
