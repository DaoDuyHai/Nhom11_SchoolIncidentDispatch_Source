@echo off
chcp 65001 >nul
title SERVER - HE THONG DIEU PHOI SU CO TRUONG THPT
echo ============================================================================
echo   KHOI DONG SERVER TRUNG TAM (PORT 5000)
echo ============================================================================

set CP=source\common\target\classes;source\service\target\classes;source\server\target\classes;%USERPROFILE%\.m2\repository\org\xerial\sqlite-jdbc\3.42.0.0\sqlite-jdbc-3.42.0.0.jar;%USERPROFILE%\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar

java -cp "%CP%" com.incidentreport.server.Server
pause

