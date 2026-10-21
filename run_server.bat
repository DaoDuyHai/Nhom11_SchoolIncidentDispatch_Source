@echo off
title School Incident Dispatch - Server
cd /d "%~dp0source"
echo ========================================================
echo   Dang khoi chay Central Dispatch Server...
echo ========================================================
set MVN_CMD="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"
if not exist %MVN_CMD% set MVN_CMD=mvn
%MVN_CMD% exec:java -pl server
pause
