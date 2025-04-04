#!/bin/bash

# 환경변수 설정
# 윈도우에서 ipconfig 명령어를 통해 WSL(자신)의 IP 주소를 확인하고, 해당 IP 주소를 WSLHOST 변수에 저장합니다.
export WSLHOST=172.27.16.1

# 테스트 출력
echo "WSLHOST is: $WSLHOST"