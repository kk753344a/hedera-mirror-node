package com.hedera.mirror.importer.reader.balance;

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

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.apache.commons.codec.digest.DigestUtils;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.TokenBalance;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;

@Named
public class ProtoBalanceFileReader implements BalanceFileReader {

    public static final String FILE_EXTENSION = "pb";

    @Override
    public boolean supports(StreamFileData streamFileData) {
        return streamFileData.getFilename().contains(FILE_EXTENSION);
    }

    @Override
    public AccountBalanceFile read(StreamFileData streamFileData, Consumer<AccountBalance> itemConsumer) {
        MessageDigest messageDigest = DigestUtils.getSha384Digest();
        itemConsumer = itemConsumer != null ? itemConsumer : accountBalance -> {
        };

        try (InputStream inputStream = new DigestInputStream(streamFileData.getInputStream(), messageDigest)) {
            AccountBalanceFile accountBalanceFile = new AccountBalanceFile();
            accountBalanceFile.setBytes(streamFileData.getBytes());
            accountBalanceFile.setLoadStart(Instant.now().getEpochSecond());

            AllAccountBalances allAccountBalances = AllAccountBalances.parseFrom(inputStream.readAllBytes());
            long consensusTimestamp = Utility.timestampInNanosMax(allAccountBalances.getConsensusTimestamp());
            allAccountBalances.getAllAccountsList().stream()
                    .map((balances -> this.readSingleAccountBalances(consensusTimestamp, balances)))
                    .forEachOrdered(itemConsumer);

            accountBalanceFile.setConsensusTimestamp(consensusTimestamp);
            accountBalanceFile.setCount((long) allAccountBalances.getAllAccountsCount());
            accountBalanceFile.setFileHash(Utility.bytesToHex(messageDigest.digest()));
            accountBalanceFile.setName(streamFileData.getFilename());
            return accountBalanceFile;
        } catch (IOException ex) {
            throw new InvalidStreamFileException("Error reading account balance pb file", ex);
        }
    }

    private AccountBalance readSingleAccountBalances(long consensusTimestamp, SingleAccountBalances balances) {
        EntityId accountId = EntityId.of(balances.getAccountID());
        List<TokenBalance> tokenBalances = balances.getTokenUnitBalancesList().stream()
                .map(tokenBalance -> {
                    EntityId tokenId = EntityId.of(tokenBalance.getTokenId());
                    TokenBalance.Id id = new TokenBalance.Id(consensusTimestamp, accountId, tokenId);
                    return new TokenBalance(tokenBalance.getBalance(), id);
                })
                .collect(Collectors.toList());
        return new AccountBalance(
                balances.getHbarBalance(),
                tokenBalances,
                new AccountBalance.Id(consensusTimestamp, accountId)
        );
    }
}
