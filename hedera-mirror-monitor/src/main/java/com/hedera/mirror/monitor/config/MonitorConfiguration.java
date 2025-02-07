package com.hedera.mirror.monitor.config;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.util.List;
import java.util.function.Function;
import javax.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import com.hedera.mirror.monitor.generator.TransactionGenerator;
import com.hedera.mirror.monitor.publish.PublishException;
import com.hedera.mirror.monitor.publish.PublishProperties;
import com.hedera.mirror.monitor.publish.PublishRequest;
import com.hedera.mirror.monitor.publish.TransactionPublisher;
import com.hedera.mirror.monitor.subscribe.Subscriber;

@Log4j2
@Configuration
class MonitorConfiguration {

    @Resource
    private PublishProperties publishProperties;

    @Resource
    private TransactionGenerator transactionGenerator;

    @Resource
    private TransactionPublisher transactionPublisher;

    @Resource
    private Subscriber subscriber;

    /**
     * Constructs a reactive flow for publishing and subscribing to transactions. The transaction generator will run on
     * a single thread and generate transactions as fast as possible. Next, a parallel Flux will concurrently publish
     * those transactions to the main nodes. Finally, a subscriber will receive every published transaction response and
     * validate whether that transaction was received by the mirror node APIs.
     *
     * @return the subscribed flux
     */
    @Bean
    Disposable publishSubscribe() {
        return Flux.<List<PublishRequest>>generate(sink -> sink.next(transactionGenerator.next(0)))
                .flatMapIterable(Function.identity())
                .retry()
                .name("generate")
                .metrics()
                .subscribeOn(Schedulers.single())
                .parallel(publishProperties.getClients())
                .runOn(Schedulers.newParallel("publisher", publishProperties.getClients()))
                .map(transactionPublisher::publish)
                .sequential()
                .parallel(publishProperties.getThreads())
                .runOn(Schedulers.newParallel("resolver", publishProperties.getThreads()))
                .flatMap(Function.identity())
                .sequential()
                .onErrorContinue(PublishException.class, (t, r) -> {})
                .doFinally(s -> log.warn("Stopped after {} signal", s))
                .doOnError(t -> log.error("Unexpected error during publish/subscribe flow:", t))
                .doOnSubscribe(s -> log.info("Starting publisher flow"))
                .subscribe(subscriber::onPublish);
    }
}
