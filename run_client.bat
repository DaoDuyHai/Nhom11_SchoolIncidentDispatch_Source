@echo off
chcp 65001 >nul
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
title CLIENT - HE THONG DIEU PHOI SU CO TRUONG THPT
echo ============================================================================
echo   KHOI DONG CLIENT (JAVAFX DESKTOP APP)
echo ============================================================================

set MAVEN_CMD="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist %MAVEN_CMD% (
    %MAVEN_CMD% javafx:run -pl client -f source\pom.xml
) else (
    mvn javafx:run -pl client -f source\pom.xml
)
pause
