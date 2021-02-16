/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.kafka.retrytopic.destinationtopic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.retrytopic.TestClockUtils;

/**
 * @author Tomaz Fernandes
 * @since 2.7
 */
class DestinationTopicContainerTests extends DestinationTopicTests {

	private Map<String, DestinationTopicResolver.DestinationsHolder> destinationTopicMap;

	private final Clock clock = TestClockUtils.CLOCK;

	private DestinationTopicContainer destinationTopicContainer = new DestinationTopicContainer(clock);

	private long originalTimestamp = Instant.now(this.clock).toEpochMilli();

	private byte[] originalTimestampBytes = BigInteger.valueOf(originalTimestamp).toByteArray();

	@BeforeEach
	public void setup() {
		destinationTopicMap = new HashMap<>();
		DestinationTopicResolver.DestinationsHolder mainDestinationHolder =
				DestinationTopicResolver.holderFor(mainDestinationTopic, firstRetryDestinationTopic);
		DestinationTopicResolver.DestinationsHolder firstRetryDestinationHolder =
				DestinationTopicResolver.holderFor(firstRetryDestinationTopic, secondRetryDestinationTopic);
		DestinationTopicResolver.DestinationsHolder secondRetryDestinationHolder =
				DestinationTopicResolver.holderFor(secondRetryDestinationTopic, dltDestinationTopic);
		DestinationTopicResolver.DestinationsHolder dltDestinationHolder =
				DestinationTopicResolver.holderFor(dltDestinationTopic, noOpsDestinationTopic);

		DestinationTopicResolver.DestinationsHolder mainDestinationHolder2 =
				DestinationTopicResolver.holderFor(mainDestinationTopic2, firstRetryDestinationTopic2);
		DestinationTopicResolver.DestinationsHolder firstRetryDestinationHolder2 =
				DestinationTopicResolver.holderFor(firstRetryDestinationTopic2, secondRetryDestinationTopic2);
		DestinationTopicResolver.DestinationsHolder secondRetryDestinationHolder2 =
				DestinationTopicResolver.holderFor(secondRetryDestinationTopic2, dltDestinationTopic2);
		DestinationTopicResolver.DestinationsHolder dltDestinationHolder2 =
				DestinationTopicResolver.holderFor(dltDestinationTopic2, noOpsDestinationTopic2);

		DestinationTopicResolver.DestinationsHolder mainDestinationHolder3 =
				DestinationTopicResolver.holderFor(mainDestinationTopic3, firstRetryDestinationTopic3);
		DestinationTopicResolver.DestinationsHolder firstRetryDestinationHolder3 =
				DestinationTopicResolver.holderFor(firstRetryDestinationTopic3, secondRetryDestinationTopic3);
		DestinationTopicResolver.DestinationsHolder secondRetryDestinationHolder3 =
				DestinationTopicResolver.holderFor(secondRetryDestinationTopic3, noOpsDestinationTopic3);

		destinationTopicMap.put(mainDestinationTopic.getDestinationName(), mainDestinationHolder);
		destinationTopicMap.put(firstRetryDestinationTopic.getDestinationName(), firstRetryDestinationHolder);
		destinationTopicMap.put(secondRetryDestinationTopic.getDestinationName(), secondRetryDestinationHolder);
		destinationTopicMap.put(dltDestinationTopic.getDestinationName(), dltDestinationHolder);
		destinationTopicMap.put(mainDestinationTopic2.getDestinationName(), mainDestinationHolder2);
		destinationTopicMap.put(firstRetryDestinationTopic2.getDestinationName(), firstRetryDestinationHolder2);
		destinationTopicMap.put(secondRetryDestinationTopic2.getDestinationName(), secondRetryDestinationHolder2);
		destinationTopicMap.put(dltDestinationTopic2.getDestinationName(), dltDestinationHolder2);
		destinationTopicMap.put(mainDestinationTopic3.getDestinationName(), mainDestinationHolder3);
		destinationTopicMap.put(firstRetryDestinationTopic3.getDestinationName(), firstRetryDestinationHolder3);
		destinationTopicMap.put(secondRetryDestinationTopic3.getDestinationName(), secondRetryDestinationHolder3);
		destinationTopicContainer.addDestinations(destinationTopicMap);
	}

	@Test
	void shouldResolveRetryDestination() {
		assertEquals(firstRetryDestinationTopic, destinationTopicContainer
				.resolveNextDestination(mainDestinationTopic.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
		assertEquals(secondRetryDestinationTopic, destinationTopicContainer
				.resolveNextDestination(firstRetryDestinationTopic.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
		assertEquals(dltDestinationTopic, destinationTopicContainer
				.resolveNextDestination(secondRetryDestinationTopic.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
		assertEquals(noOpsDestinationTopic, destinationTopicContainer
				.resolveNextDestination(dltDestinationTopic.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));

		assertEquals(firstRetryDestinationTopic2, destinationTopicContainer
				.resolveNextDestination(mainDestinationTopic2.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
		assertEquals(secondRetryDestinationTopic2, destinationTopicContainer
				.resolveNextDestination(firstRetryDestinationTopic2.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
		assertEquals(dltDestinationTopic2, destinationTopicContainer
				.resolveNextDestination(secondRetryDestinationTopic2.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
		assertEquals(dltDestinationTopic2, destinationTopicContainer
				.resolveNextDestination(dltDestinationTopic2.getDestinationName(), 1,
						new IllegalArgumentException(), this.originalTimestamp));
	}

	@Test
	void shouldResolveDltDestinationForNonRetryableException() {
		assertEquals(dltDestinationTopic, destinationTopicContainer
				.resolveNextDestination(mainDestinationTopic.getDestinationName(),
						1, new RuntimeException(), originalTimestamp));
	}

	@Test
	void shouldResolveRetryDestinationForWrappedException() {
		assertEquals(firstRetryDestinationTopic, destinationTopicContainer
				.resolveNextDestination(mainDestinationTopic.getDestinationName(),
						1, new ListenerExecutionFailedException("Test exception!",
								new IllegalArgumentException()), originalTimestamp));
	}

	@Test
	void shouldResolveNoOpsDestinationForDoNotRetryDltPolicy() {
		assertEquals(noOpsDestinationTopic, destinationTopicContainer
				.resolveNextDestination(dltDestinationTopic.getDestinationName(),
						1, new IllegalArgumentException(), originalTimestamp));
	}

	@Test
	void shouldResolveDltDestinationForAlwaysRetryDltPolicy() {
		assertEquals(dltDestinationTopic2, destinationTopicContainer
				.resolveNextDestination(dltDestinationTopic2.getDestinationName(),
						1, new IllegalArgumentException(), originalTimestamp));
	}

	@Test
	void shouldResolveDltDestinationForExpiredTimeout() {
		long timestampInThePastToForceTimeout = this.originalTimestamp - 10000;
		assertEquals(dltDestinationTopic2, destinationTopicContainer
				.resolveNextDestination(mainDestinationTopic2.getDestinationName(),
						1, new IllegalArgumentException(), timestampInThePastToForceTimeout));
	}

	@Test
	void shouldThrowIfNoDestinationFound() {
		assertThrows(NullPointerException.class,
				() -> destinationTopicContainer.resolveNextDestination("Non-existing-topic", 0,
						new IllegalArgumentException(), originalTimestamp));
	}

	@Test
	void shouldResolveNoOpsIfDltAndNotRetryable() {
		assertEquals(noOpsDestinationTopic3, destinationTopicContainer
						.resolveNextDestination(mainDestinationTopic3.getDestinationName(), 0,
						new RuntimeException(), originalTimestamp));
	}

	@Test
	void shouldResolveDestinationNextExecutionTime() {
		RuntimeException e = new IllegalArgumentException();
		assertEquals(getExpectedNextExecutionTime(firstRetryDestinationTopic),
				destinationTopicContainer.resolveDestinationNextExecutionTimestamp(
					mainDestinationTopic.getDestinationName(), 0, e, originalTimestamp));
		assertEquals(getExpectedNextExecutionTime(secondRetryDestinationTopic),
				destinationTopicContainer.resolveDestinationNextExecutionTimestamp(
					firstRetryDestinationTopic.getDestinationName(), 0, e, originalTimestamp));
		assertEquals(getExpectedNextExecutionTime(dltDestinationTopic),
				destinationTopicContainer.resolveDestinationNextExecutionTimestamp(
					secondRetryDestinationTopic.getDestinationName(), 0, e, originalTimestamp));
		assertEquals(getExpectedNextExecutionTime(noOpsDestinationTopic),
				destinationTopicContainer.resolveDestinationNextExecutionTimestamp(
					dltDestinationTopic.getDestinationName(), 0, e, originalTimestamp));
	}

	private long getExpectedNextExecutionTime(DestinationTopic destinationTopic) {
		return originalTimestamp + destinationTopic.getDestinationDelay();
	}

	@Test
	void shouldThrowIfAddsDestinationsAfterClosed() {
		destinationTopicContainer.onApplicationEvent(null);
		assertThrows(IllegalStateException.class, () ->
				destinationTopicContainer.addDestinations(Collections.emptyMap()));
	}
}
