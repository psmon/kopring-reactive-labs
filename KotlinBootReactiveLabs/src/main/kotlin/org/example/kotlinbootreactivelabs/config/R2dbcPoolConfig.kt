package org.example.kotlinbootreactivelabs.config

import io.r2dbc.pool.ConnectionPool
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

/**
 * R2DBC Pool 설정
 *
 * R2DBC의 커넥션 풀은 리액티브 프로그래밍 패러다임을 따르기 때문에 커넥션 풀을 명시적으로 웜업한 상태로 두지 않음
 * -> 그러므로 따로 웜업이 필요한 어플리케이션이라면 설정 클래스 파일에 웜업 코드를 따로 명시적으로 추가하여야 함
 * [카카오페이 블로그 글 참조](https://tech.kakaopay.com/post/r2dbc-connection-pool-missing)
 */
@Configuration
class R2dbcPoolConfig(
    //ConnectionPool Bean을 주입받음
    private val r2dbcConnectionPool: ConnectionPool,
) {
    @PostConstruct
    fun init() {
        println("R2DBC Pool Warm Up Start")
        //명시적으로 ConnectionPool의 warmup을 진행합니다.
        r2dbcConnectionPool.warmup().block()
        println("R2DBC Pool Warm Up End")
    }
}