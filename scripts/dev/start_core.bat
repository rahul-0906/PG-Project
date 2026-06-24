@echo off
echo Launching Core PG CRM Full Stack...
start "Core CRM Backend" cmd /k "cd ..\\..\\core-pg-crm\\backend && mvn spring-boot:run"
start "Core CRM Frontend" cmd /k "cd ..\\..\\core-pg-crm\\frontend && npm run dev"
exit
