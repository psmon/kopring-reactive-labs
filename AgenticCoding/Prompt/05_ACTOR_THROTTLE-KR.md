# 프로젝트 생성지침
- AgenticCoding/Projects/ACTOR_THROTTLE 폴더에서 새롭게 시작합니다.
- 어플리케이션으로 작동이 아닌 코틀린기반 기능모듈만 만들고자합니다.
- 다음과 같은 액터를 작성하고 유닛테스트에서만 실행및 검증을 진행하려합니다.

## 구현액터및 유닛테스트 구현
- 액터모델에 Throttle장치를 탑재해, TPS를 제어하는 듀토리얼성의 액터모델을 작성하려고합니다.
- 작업은 "mallID" 단위로 발생하며 , mallID별 작업은 TPS1 제약설정이 됩니다.
- 작업 관리자가 mallID별 하위 작업액터를 관리합니다. 

## 다국어 작성 지침 지원
- README.md 는 영문으로 작성
- README-kr.md 은 한글로작성(영문 작성버전을 한글로 번역)

## Throttle

Throttle 장치는 다음을 참고~ Sleep(스레드차단)없이 제어

```
val helloLimitSource = Source.queue<HelloLimit>(100, OverflowStrategy.backpressure())
    .throttle(3, Duration.ofSeconds(1))
    .to(Sink.foreach { cmd ->
        // TPS3을 준수하면서 흘려보냅니다.
        helloStateActor.tell(Hello(cmd.message, cmd.replyTo))
    })
    .run(materializer)
// 100개의 이벤트를 동시에 요청할수 있지만 tps제약에의해 순차처리가 됩니다.
for (i in 1..100) {
    helloLimitSource.offer(HelloLimit("Hello", probe.ref()))
}   
```


## 유닛테스트 수행및 부가지침
- 코드 완성후, 완성된 코드 유닛테스트 시도합니다.  
- pekko testkit을 활용해 , 완료대상 probe를 통해 검증및 tps확인합니다.
- 유닛테스트 코드가 완성되면 readme.md에 코드컨셉및 듀토리얼을 초보자를 위해 쉽게 설명합니다.

## 참고코드 사전 지식

다음과같은 디렉토리에 참고할만한 샘플코드들이 있습니다.

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
- 스프링 부트기반 코틀린으로 리액티브 스트림기반의 동시성처리및 다양한 액터모델이 구현되었습니다.
- 코드학습대상은 *.kt와 .md파일을 참고할것
- 유닛테스트가 필요하게될시 test 파일에 사용되는 방식을 참고하고 개선할것
- 필요한 디펜던시는 이 샘플코드와 동일할시, 버전을 동일하게 맞출것 - 그레이들사용