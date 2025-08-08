### Cluade Code for WSL (Windows Subsystem for Linux) Installation Guide

윈도우 개발자를 위한 CluadeCode 사용을 위한 WSL에서 인스톨

```
To properly install Node.js packages in WSL that detect your environment as Linux rather than Windows:

1. Install NVM (Node Version Manager) in WSL: wslforcluade.sh 동일파일있음 - 스크립트 안정성 여부확인후 수행권장
    
    ```bash
    sudo apt-get install curl
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/master/install.sh | bash
    ```
    
2. Close and reopen your terminal, or run:
    
    ```bash
    source ~/.bashrc
    ```
    
3. Install Node.js using NVM:
    
    ```bash
    nvm install node
    ```
    
4. Verify Node.js is installed in the Linux environment:
    
    ```bash
    which npm
    ```
    
    Should show: `/home/username/.nvm/versions/node/vX.X.X/bin/npm`
    
5. Now install packages through this Node.js installation:
    
    ```bash
    npm config set os linux
    npm install -g @anthropic-ai/claude-code
    ```
    
- **git 설치**
    
    ```bash
    sudo apt install git
    ```
    
- **ripgrep 설치**
    
    ```bash
    sudo apt install ripgrep
    ```
```