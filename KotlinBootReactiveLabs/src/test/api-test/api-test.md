# API Test

## API TEST Page 정보
- http://localhost:8080/api-doc
- http://localhost:8080/cx-chat/wstest-counselor.html
- http://localhost:8080/cx-chat/wstest-user.html


## 사전 최초셋팅 for WSL , WSL TO Windown Local

### WSL LocalHost Forwarding 설정
```
# C:\USER\USER_NAME\.wslconfig
[wsl2]
localhostForwarding=true
```

###
```bash
chmod +x .loadenv.sh
```
## CX API TEST

### WebNori 채널을 관리하는 슈퍼메니저를 만듭니다.

```bash
source ./.loadenv.sh
curl -X POST "http://$WSLHOST:8080/api/admin/channel/add-counselor-manager?channel=webnori"  
```

### Webnori 채널에 새로운 상담사를 추가합니다.

```bash
curl -X POST "http://$WSLHOST:8080/api/admin/counselor/add-counselor?channel=webnori&id=counselor1"
```

### Webnori 채널의 counselor1상담사가 로그인합니다.

```bash
curl -X POST "http://$WSLHOST:8080/api/auth/login?id=counselor1&password=counselor1&identifier=webnori&nick=counselor1&authType=counselor"    
```

### 사용자가 로그인합니다.
```bash
curl -X POST "http://$WSLHOST:8080/api/auth/login?id=user1&password=user1&identifier=user1&nick=sam&authType=user"
```