@echo off
chcp 65001 >nul
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
title KHOI TAO CSDL - HE THONG DIEU PHOI SU CO TRUONG THPT
echo ============================================================================
echo   KHOI TAO VA RESET CSDL SQLITE (SCHEMA + SEED DATA)
echo ============================================================================

set MAVEN_CMD="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist %MAVEN_CMD% (
    %MAVEN_CMD% exec:java -pl service -f source\pom.xml
) else (
    mvn exec:java -pl service -f source\pom.xml
)
pause

