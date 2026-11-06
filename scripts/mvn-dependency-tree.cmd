@echo off
rem Run from the project root so Maven finds the aggregator pom.xml.
cd /d "%~dp0.."
call mvnw.cmd dependency:tree -Dverbose
pause
