package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class AddressBookEntryRepositoryTest extends AbstractRepositoryTest {

    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Resource
    protected AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    protected AddressBookRepository addressBookRepository;

    @Test
    void save() {
        addressBookRepository.save(addressBook(null, 1L));
        AddressBookEntry addressBookEntry = addressBookEntryRepository.save(addressBookEntry(null, 1L, 3));
        assertThat(addressBookEntryRepository.findById(addressBookEntry.getId()))
                .get()
                .isEqualTo(addressBookEntry);
    }

    @Test
    void verifySequence() {
        long consensusTimestamp = 1L;
        addressBookRepository.save(addressBook(null, consensusTimestamp));
        addressBookEntryRepository.save(addressBookEntry(null, consensusTimestamp, 3));
        addressBookEntryRepository.save(addressBookEntry(null, consensusTimestamp, 4));
        addressBookEntryRepository.save(addressBookEntry(null, consensusTimestamp, 5));
        assertThat(addressBookEntryRepository.findAll())
                .isNotNull()
                .extracting(AddressBookEntry::getId)
                .containsSequence(1L, 2L, 3L);
    }

    private AddressBookEntry addressBookEntry(Consumer<AddressBookEntry.AddressBookEntryBuilder> nodeAddressCustomizer, long consensusTimestamp, long nodeAccountId) {
        String nodeAccountIdString = String.format("0.0.%s", nodeAccountId);
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .ip("127.0.0.1")
                .publicKey("rsa+public/key")
                .memo(nodeAccountIdString)
                .nodeAccountId(EntityId.of(nodeAccountIdString, EntityTypeEnum.ACCOUNT))
                .nodeId(nodeAccountId)
                .nodeCertHash("nodeCertHash".getBytes());

        if (nodeAddressCustomizer != null) {
            nodeAddressCustomizer.accept(builder);
        }

        return builder.build();
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer,
                                    long consensusTimestamp) {

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .startConsensusTimestamp(consensusTimestamp)
                .fileData("address book memo".getBytes())
                .fileId(addressBookEntityId102);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }
}
