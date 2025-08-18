@echo off
REM APISCAN Runner - Scans Spring PetClinic REST API
REM This script builds the project and runs the API scanner

echo Building APISCAN...
call mvn clean package -DskipTests -q

if %ERRORLEVEL% neq 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Running APISCAN on Spring PetClinic REST...
echo.
echo The OpenAPI specification will be generated as:
@REM echo   %CD%\spring-petclinic-rest-openapi.yaml
echo   %CD%\shopizer-openapi.yaml
echo.
@REM java -jar apiscan-cli\target\apiscan-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar "C:\Users\Rudi Kristanto\prj\spring-petclinic-rest"
java -jar apiscan-cli\target\apiscan-cli-1.0.0-SNAPSHOT-jar-with-dependencies.jar "C:\Users\Rudi Kristanto\prj\shopizer"

echo.
echo =========================================================
echo Check the generated OpenAPI file at:
@REM echo   %CD%\spring-petclinic-rest-openapi.yaml
echo   %CD%\shopizer-openapi.yaml
echo =========================================================
pause