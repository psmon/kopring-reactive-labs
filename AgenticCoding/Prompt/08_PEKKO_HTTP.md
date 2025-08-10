
Pekko HTTP를 활용해  Pekko의 액터모델을 이용한 응답 API를 만들고자합니다.
아래 지침을 참고해 생성해주세요

# 프로젝트 생성지침
- AgenticCoding/Projects/PEKKO_HTTP 폴더에서 프로젝트를 생성합니다.
- 어플리케이션으로 작동이 아닌 코틀린기반 기능모듈만 만들고자합니다.
- 다음 기능요구사항을 충족하는 유닛테스트를 작성실행및 검증을 진행하려합니다.

## 구현핵심기능
- Hello를 요청하면 Pekko로 응답하는 액터모델을 생성하고, API를 연결해주세요
- API를 통해 사용자의 행동이벤트가 호출되면~ 액터모델에 Stream으로 이벤트를 전송해서 로깅으로 출력해주세요
- PekkoHTTP와 Pekko액터모델및 Stream에 연결해 좋은 샘플유형이 있으면 추가로 작성해주세요


## 유닛테스트 수행및 부가지침
- 코드 완성후, 완성된 코드 유닛테스트 시도합니다.
- 유닛테스트외에 curl을 통한 통합 테스트도 수행합니다. curl을 테스트할수 있는 테스트문서파일도 작성해주세요
- pekko-http 용 swagger 문서도 작성해주세요
- pekko-http를 통해 pekko 의 액터모델을 전면채탤할때  장단점도 설명합니다. spring boot를 사용하지 않고 pekko-http를 이용해 구현하는 이유와 장점을 설명해주세요
- 유닛테스트 코드가 작성되고~ 검증되면 readme.md에 이 프로젝트의 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다. 필요하면 mermaid이용 다이어그램 설명도 함께합니다.

## 다국어 작성 지침 지원
- README.md 는 영문으로 작성
- README-kr.md 은 한글로작성(영문 작성버전을 한글로 번역)

# Pekko HTTP 참고
Spring Boot를 사용하지 않고 다음 Pekko HTTP를 이용해 웹HTTP 서비스를 구현합니다.  
Spring 종속성은 사용하지말고 다음을 참고 라이트웨이 하게 구성합니다.

- https://pekko.apache.org/docs/pekko-http/current/release-notes/releases-1.2.html
- https://pekko.apache.org/docs/pekko-http/current/introduction.html
- https://pekko.apache.org/docs/pekko-http/current/introduction.html#using-apache-pekko-http
- https://pekko.apache.org/docs/pekko-http/current/introduction.html#streaming
- https://pekko.apache.org/docs/pekko-http/current/introduction.html#marshalling
- https://pekko.apache.org/docs/pekko-http/current/configuration.html
- https://github.com/theiterators/pekko-http-microservice
- https://github.com/pjfanning/pekko-http-json
- https://github.com/swagger-akka-http/swagger-pekko-http


## Pekko 참고지식

다음 디렉토리에 코틀린으로 작동되는 Pekko 샘플코드들이 있습니다.

```
current working directory:
├── CommonModel/
│   └── src/
├── Docs/
│   ├── eng/
│   └── kr/
├── KotlinBootReactiveLabs/
│   └── src/
│       ├── main/
│       └── test/
└── README.MD
```

### 참고대상
- 참고대상 디렉토리는 참고코드 위치 하위 디렉토리에 있는 파일을 참고
- 코틀린으로 리액티브 스트림기반의 동시성처리및 다양한 액터모델이 구현되었습니다.
- 코드학습대상은 *.kt와 .md파일을 참고할것
- 유닛테스트가 필요하게될시 test 파일에 사용되는 방식을 참고하고 개선할것
- 필요한 디펜던시는 이 샘플코드와 동일할시, 버전을 동일하게 맞출것 - 그레이들사용
