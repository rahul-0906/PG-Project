@echo off
echo Launching Master Control Plane Full Stack...
start "Control Plane Backend" cmd /k "cd ..\\..\\master-control-plane\\backend && mvn spring-boot:run"
start "Control Plane Frontend" cmd /k "cd ..\\..\\master-control-plane\\frontend && npm run dev"
exit
