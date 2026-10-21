@echo off
title School Incident Dispatch - Client
cd /d "%~dp0source"
echo ========================================================
echo   Dang khoi chay Client JavaFX...
echo ========================================================
set MVN_CMD="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
if not exist %MVN_CMD% set MVN_CMD=mvn
%MVN_CMD% javafx:run -pl client
pause
