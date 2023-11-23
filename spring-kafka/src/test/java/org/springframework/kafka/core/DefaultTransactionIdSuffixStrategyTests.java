/*
 * Copyright 2016-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.kafka.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * @author Ilya Starchenko
 *
 * @since 3.2
 */
class DefaultTransactionIdSuffixStrategyTests {

	private DefaultTransactionIdSuffixStrategy suffixStrategy;

	@BeforeEach
	void setUp() {
		suffixStrategy = new DefaultTransactionIdSuffixStrategy();
	}

	@Test
	void acquireSuffixWithoutCache() {
		String txIdPrefix = "txIdPrefix";
		String suffix = suffixStrategy.acquireSuffix(txIdPrefix);
		assertThat(suffix).isNotNull();
	}

	@Test
	void acquireSuffixWithCache() {
		String txIdPrefix = "txIdPrefix";
		String suffix = "suffix";
		assertThatNoException().isThrownBy(() -> suffixStrategy.releaseSuffix(txIdPrefix, suffix));
	}


	@Test
	void acquireSuffixWithCacheExhausted() {
		String txIdPrefix = "txIdPrefix";
		suffixStrategy.setMaxCache(2);
		String suffix1 = suffixStrategy.acquireSuffix(txIdPrefix);
		String suffix2 = suffixStrategy.acquireSuffix(txIdPrefix);
		assertThatExceptionOfType(NoProducerAvailableException.class)
				.isThrownBy(() -> suffixStrategy.acquireSuffix(txIdPrefix))
				.extracting(NoProducerAvailableException::getTxIdPrefix).isEqualTo(txIdPrefix);
	}

	@Test
	void releaseSuffixWithCache() {
		String txIdPrefix = "txIdPrefix";
		suffixStrategy.setMaxCache(2);
		String suffix = suffixStrategy.acquireSuffix(txIdPrefix);
		assertThatNoException().isThrownBy(() -> suffixStrategy.releaseSuffix(txIdPrefix, suffix));
	}

	@SuppressWarnings("rawtypes")
	@Test
	void releaseAllSuffixesWithCache() {
		String txIdPrefix = "txIdPrefix";
		suffixStrategy.setMaxCache(2);
		String suffix1 = suffixStrategy.acquireSuffix(txIdPrefix);
		String suffix2 = suffixStrategy.acquireSuffix(txIdPrefix);
		assertThatNoException().isThrownBy(() -> suffixStrategy.releaseSuffix(txIdPrefix, suffix1));
		Map<?, ?> suffixCache = KafkaTestUtils.getPropertyValue(suffixStrategy, "suffixCache", Map.class);
		assertThat(suffixCache).hasSize(1);
		Queue queue = (Queue) suffixCache.get(txIdPrefix);
		assertThat(queue).hasSize(1);
		assertThat(queue.poll()).isEqualTo(suffix1);
	}

	@Test
	void setMaxCacheIsNegative() {
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> suffixStrategy.setMaxCache(-1));
	}

	@Test
	void setMaxCacheIsZero() {
		assertThatNoException().isThrownBy(() -> suffixStrategy.setMaxCache(0));
	}
}
