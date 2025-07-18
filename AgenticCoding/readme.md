# 액터모델 +리액티브 스트림을 코드구현 by Claude Code

**리액티브 스트림(Reactive Streams)**은 비동기 데이터 처리와 흐름 제어(Backpressure)를 표준화한 자바 진영의 명세로, Netflix, Lightbend, Pivotal 등 주요 기업들이 참여해 2015년에 정식 발표되었습니다. Java 9부터는 Flow API로 포함되어 공식적인 JVM 표준이 되었으며, Spring WebFlux, Akka Streams, Project Reactor, RxJava 등 다양한 프레임워크들이 이를 기반으로 비동기 스트림 처리의 신뢰성과 일관성을 제공하고 있습니다. 특히 고속 데이터 전송, 스트리밍 분석, 웹소켓 기반 실시간 서비스에 핵심적인 기술로 자리 잡았습니다.

**액터모델(Actor Model)**은 상태를 메시지를 통해만 변경할 수 있는 독립적인 액터 단위로 분산 시스템을 구성하는 개념으로, 동시성 문제를 안전하게 해결하면서 수평 확장이 용이하다는 장점이 있습니다. Erlang/OTP, Akka(Lightbend), Microsoft Orleans 등의 구현체를 통해 WhatsApp, LinkedIn, Tesla, Microsoft 같은 글로벌 기업들이 실시간 통신, IoT, 게임, AI 에이전트 등에 성공적으로 활용하고 있으며, 이벤트 기반 구조와 궁합이 좋아 복잡한 비즈니스 로직이나 AI 파이프라인의 오케스트레이션에도 탁월한 구조를 제공합니다.

## 프로젝트 목적

액터모델을 알지못해도 액터모델을 이용하는 다양한 샘플기능을 바이브로도 만들수있지만 
바이브를 통해 액터구현을 다양하게 시도할수 있음으로 학습곡선에따른 시간을 단축하는것과 동시에
액터시스템이 지원하는 클러스터 포함 상태프로그래밍/분산처리 설계능력을 로컬로 수행함으로 고오급 설계 능력을 배양하는데 목적이있습니다. 

## 이용 AI-TOOL
- Cluade Code

## 프롬프트 샘플

AgenticCoding/Projects는 여기서 구성된 프롬프트에의해 생성된 프로젝트로 참고할수 있으며
프롬프트를 참고해 다양한 지침을 생성한후 다양한 액터시스템을 구현에 도전할수 있습니다.
샘플코드의 생성순서는 난이도에 따른 학습순서와 무관하게 작성시도됩니다.

### 검증된 프롬프트 목록

현재상위 경로에서 수행

- [SSE-PUSH 시스템](./Prompt/00_SSE-PUSH-SYSTEM.md) - AgenticCoding/Prompt/00_SSE-PUSH-SYSTEM.md 지침을 수행해
- [액터 동시성 처리](./Prompt/01_ACTOR_CONCURRENCY.md) - AgenticCoding/Prompt/01_ACTOR_CONCURRENCY.md 지침을 수행해
- [LLM 토큰 제어](./Prompt/02_LLM_THROTTLE.md) - AgenticCoding/Prompt/02_LLM_THROTTLE.md 지침을 수행해

> **참고**: 액터모델은 자바(+코틀린)가 지원하는 다양한 동시성프로그래밍을 이해하며 이를 이용하기때문에, 동시성 처리 기본기는 중요합니다.

## 생성된 프로젝트

클루드에의해 코딩없이 생성된 프로젝트는 [Projects 디렉토리](./Projects/)에서 확인할수 있습니다.

### 프로젝트 목록

- [SSE Push System](./Projects/SSE-PUSH-SYSTEM/)
  - Server-Sent Events를 활용한 실시간 푸시 시스템으로 액터모델과 연결되어 더 강력한 시스템으로 업그레이드 할수 있습니다.
- [Actor Concurrency](./Projects/ACTOR_CONCURRENCY/) 
  - 액터 모델 기반 동시성 처리 예제로, JVM이 지원하는 동시성 프로그래밍을 함께 학습합니다.
- [LLM Throttle](./Projects/LLM-THROTTLE/)
  - LLM 호출 제약조절을 자동으로 할수 있는 액터장치로, 설계된 액터를 통해 다양한 형태의 메일박스(큐)를 구현할수 있습니다. 


## 액터모델(리액티브 스트림) 학습을 위한 다양한 프롬프트

다음과 같은 질문을 LLM에게 의뢰해~ 액터모델이 할수 있는것들을 조사한후 엑터모델을 활용한 다양한 고급기능 구현을 시도할수 있습니다.

- 자바진영의 리액티브 스트림을 영향을 준 기업과 활동들을 연도별로 정리
  - webflux 부터 시작한 자바개발자에게 인사이트를 넓혀줄수 있습니다.
- 액터모델로 시도할수 있는 유용한 샘플소개
- AKKA및 언어 상관없이 액터모델을 도입해 성공한 다양한 테크기업의 사례소개, 국내/해외를 나눠서 
  - 기업테크 조사는 할루네이션이 있을수 있음으로 실 활용사례는 검색을 한번더 하는것을 권장
- CRUD만 개발할줄 아는 개발자가 액터모델을 수련하게되면 할수 있는것들? 주로 DB/REDIS에만 의존해 상태없는 프로그래밍만 하는 케이스와 비교해 설명해죠
- 다양한 AI를 활용해야하는시대 액터모델을 도입했을때~ 효과와 실제 활용한 기업의 사례는?
- Backpresure 가 적용된것과 아닌것의 프로그래밍 난이도차이? 안정성/성능 관점에서도 비교해죠
- JAVA9에서 STREAM API가 적용되기까지 영향을 준 활동및, 영향력 있는 개발자는? 

## Docs
- https://wiki.webnori.com/display/AKKA/Vibe+Coding+Actor+Model