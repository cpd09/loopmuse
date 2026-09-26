# 터미널 단축 명령 설정

작성일: 2026-09-26

현재 Windows 사용자 `CPD`가 어느 작업 디렉터리에서든 `agyskip`과 `codexskip`을 실행할 수 있도록 설정한 내역이다. 설정 파일은 이 저장소 밖의 사용자 홈 디렉터리에 있으며, 이 문서는 설정을 재현하고 확인하기 위한 기록이다.

| 명령 | 실제 실행 명령 | 사용자 PATH의 실행 폴더 |
| --- | --- | --- |
| `agyskip` | `agy --dangerously-skip-permissions` | `C:\Users\CPD\AppData\Local\agy\bin` |
| `codexskip` | `codex --no-daemon --dangerously-bypass-approvals-and-sandbox` | `C:\Users\CPD\AppData\Roaming\npm` |

두 폴더는 Windows의 **사용자 PATH**에 등록되어 있다. 따라서 적용 범위는 이 PC의 `CPD` 계정이며, 모든 사용자 계정에 적용되는 시스템 PATH 설정은 아니다. 명령 뒤에 붙인 일반적인 CLI 인수도 원래 실행 파일로 전달된다.

## `agyskip` 설정

- `C:\Users\CPD\AppData\Local\agy\bin\agyskip.cmd`: CMD와 Windows 명령 실행 환경용 배치 파일. 같은 폴더의 `agy.exe`를 `--dangerously-skip-permissions %*`로 실행한다.
- `C:\Users\CPD\AppData\Local\agy\bin\agyskip`: Git Bash 등 POSIX 호환 셸용 스크립트. 같은 폴더의 `agy.exe`를 `--dangerously-skip-permissions "$@"`로 실행한다.
- `C:\Users\CPD\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1`: Windows PowerShell 함수 등록.
- `C:\Users\CPD\Documents\PowerShell\Microsoft.PowerShell_profile.ps1`: PowerShell 7용 함수 등록.

배치 파일과 셸 스크립트의 핵심 내용은 다음과 같다.

```bat
@ECHO OFF
"%~dp0agy.exe" --dangerously-skip-permissions %*
EXIT /B %ERRORLEVEL%
```

```sh
#!/usr/bin/env sh
exec "$(dirname "$0")/agy.exe" --dangerously-skip-permissions "$@"
```

두 PowerShell 프로필에는 다음 함수가 있다.

```powershell
function agyskip {
    & agy --dangerously-skip-permissions @args
}
```

Windows PowerShell 프로필에는 Node.js와 npm 실행 폴더를 현재 세션의 PATH 앞에 추가하는 설정도 있다. `agyskip` 설치 과정에서 CMD, Git Bash, PowerShell에서 `agyskip --help` 실행을 확인했다. 이번 문서 작성 시에는 같은 셸에서 `agyskip --version`이 `1.2.11`을 출력하는 것을 다시 확인했다. PowerShell 7 프로필 파일은 존재하지만 현재 환경에서 `pwsh` 명령을 찾지 못해 그 셸의 실행은 별도로 확인하지 않았다.

## `codexskip` 설정

- `C:\Users\CPD\AppData\Roaming\npm\codexskip.cmd`: CMD와 PowerShell에서 사용하는 배치 파일. 같은 폴더의 `codex.cmd`를 `--no-daemon --dangerously-bypass-approvals-and-sandbox %*`로 호출한다.
- `C:\Users\CPD\AppData\Roaming\npm\codexskip`: Git Bash에서 사용하는 POSIX 셸 스크립트. 같은 폴더의 `codex`를 `--no-daemon --dangerously-bypass-approvals-and-sandbox "$@"`로 실행한다.

```bat
@echo off
call "%~dp0codex.cmd" --no-daemon --dangerously-bypass-approvals-and-sandbox %*
exit /b %errorlevel%
```

```sh
#!/usr/bin/env sh
exec "$(dirname "$0")/codex" --no-daemon --dangerously-bypass-approvals-and-sandbox "$@"
```

`codexskip.cmd`를 먼저 등록한 뒤 Git Bash에서 명령을 찾지 못하는 것을 확인했다. 이에 확장자 없는 셸 스크립트를 추가했다. 이후 서로 다른 디렉터리에서 PowerShell, CMD, Git Bash로 `codexskip --version`을 실행해 모두 `codex-cli 0.157.0`이 출력되는 것을 확인했다. 설치된 CLI의 `codex --help`에서도 두 옵션을 확인했다.

## 사용 및 확인

새 터미널을 열고 원하는 디렉터리에서 다음과 같이 사용한다.

```text
agyskip
codexskip
codexskip --version
```

명령 경로는 PowerShell에서 `Get-Command agyskip,codexskip`, CMD에서 `where agyskip` 및 `where codexskip`, Git Bash에서 `command -v agyskip` 및 `command -v codexskip`으로 확인할 수 있다. VS Code와 Android Studio 내장 터미널도 해당 사용자 PATH를 받은 새 셸이라면 같은 명령을 사용할 수 있다. 두 IDE의 내장 터미널에서는 별도 실행 검증을 하지 않았다.

`agyskip`과 `codexskip`은 각각 권한 확인을 건너뛰는 옵션을 항상 포함한다. 특히 `codexskip`은 Codex의 승인 절차와 샌드박스를 우회한다. 일반적인 Codex 옵션 동작은 [공식 CLI 명령 문서](https://learn.chatgpt.com/docs/developer-commands?surface=cli)에서 확인할 수 있다.
