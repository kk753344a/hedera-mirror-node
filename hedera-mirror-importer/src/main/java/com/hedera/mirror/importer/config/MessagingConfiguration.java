package com.hedera.mirror.importer.config;

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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;

import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;
import com.hedera.mirror.importer.parser.event.EventParserProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

@Configuration
public class MessagingConfiguration {

    // Shared channel containing all stream types until they're routed to the individual channels
    public static final String CHANNEL_STREAM = "stream";
    public static final String CHANNEL_BALANCE = CHANNEL_STREAM + ".balance";
    public static final String CHANNEL_EVENT = CHANNEL_STREAM + ".event";
    public static final String CHANNEL_RECORD = CHANNEL_STREAM + ".record";

    @Bean(CHANNEL_BALANCE)
    MessageChannel channelBalance(BalanceParserProperties properties) {
        return MessageChannels.queue(properties.getQueueCapacity()).get();
    }

    @Bean(CHANNEL_EVENT)
    MessageChannel channelEvent(EventParserProperties properties) {
        return MessageChannels.queue(properties.getQueueCapacity()).get();
    }

    @Bean(CHANNEL_RECORD)
    MessageChannel channelRecord(RecordParserProperties properties) {
        return MessageChannels.queue(properties.getQueueCapacity()).get();
    }

    @Bean
    IntegrationFlow streamFileRouter() {
        return IntegrationFlows.from(CHANNEL_STREAM)
                .route(StreamFile.class, s -> s.getType().toString().toLowerCase(), s -> s.prefix(CHANNEL_STREAM + "."))
                .get();
    }
}
