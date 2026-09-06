@echo off
setlocal
set APP_HOME=%~dp0
java -Dfile.encoding=UTF-8 -jar "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" %*
exit /b %ERRORLEVEL%
