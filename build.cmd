@echo off
setlocal
chcp 65001 >nul

python -c "import sys; sys.exit(sys.version_info < (3, 12))" >nul 2>&1
if not errorlevel 1 goto use_python
py -3 -c "import sys; sys.exit(sys.version_info < (3, 12))" >nul 2>&1
if not errorlevel 1 goto use_py
echo Python 3.12 or newer is required. Install Python and add it to PATH.
set "build_exit=1"
goto finish

:use_python
python -X utf8 "%~dp0tools\packaging\build.py"
set "build_exit=%errorlevel%"
goto finish

:use_py
py -3 -X utf8 "%~dp0tools\packaging\build.py"
set "build_exit=%errorlevel%"

:finish
echo.
pause
exit /b %build_exit%
