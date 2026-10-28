@echo off
chcp 65001 >nul
title CLIENT - HE THONG DIEU PHOI SU CO TRUONG THPT
echo ============================================================================
echo   KHOI DONG CLIENT (JAVAFX DESKTOP APP)
echo ============================================================================

set MAVEN_CMD="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist %MAVEN_CMD% (
    %MAVEN_CMD% javafx:run -f source\client\pom.xml
) else (
    mvn javafx:run -f source\client\pom.xml
)
pause

