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

package org.springframework.kafka.retrytopic;

import java.math.BigInteger;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

import org.springframework.core.NestedRuntimeException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.KafkaBackoffException;
import org.springframework.kafka.retrytopic.destinationtopic.DestinationTopic;
import org.springframework.kafka.retrytopic.destinationtopic.DestinationTopicResolver;
import org.springframework.util.Assert;

/**
 *
 * Creates and configures the {@link DeadLetterPublishingRecoverer} that will be used to
 * forward the messages using the {@link DestinationTopicResolver}.
 *
 * @author Tomaz Fernandes
 * @since 2.7
 *
 */
public class DeadLetterPublishingRecovererFactory {

	private static final CharSequence KAFKA_DLT_HEADERS_PREFIX = "kafka_dlt";

	private static final String NO_OPS_RETRY_TOPIC = "internal-kafka-noOpsRetry";

	private final DestinationTopicResolver destinationTopicResolver;

	public DeadLetterPublishingRecovererFactory(DestinationTopicResolver destinationTopicResolver) {
		this.destinationTopicResolver = destinationTopicResolver;
	}

	@SuppressWarnings("unchecked")
	public DeadLetterPublishingRecoverer create(Configuration configuration) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(configuration.template,
					((cr, e) -> this.resolveDestination(cr, e, configuration))) {
			@Override
			protected void publish(ProducerRecord<Object, Object> outRecord, KafkaOperations<Object, Object> kafkaTemplate) {
				if (NO_OPS_RETRY_TOPIC.equals(outRecord.topic())) {
					this.logger.warn(() -> "Processing failed for dlt topic, giving up.");
					return;
				}

				KafkaOperations<Object, Object> kafkaOperationsForTopic = (KafkaOperations<Object, Object>)
						DeadLetterPublishingRecovererFactory.this.destinationTopicResolver.getKafkaOperationsFor(outRecord.topic());
				super.publish(outRecord, kafkaOperationsForTopic);
			}
		};

		recoverer.setHeadersFunction((consumerRecord, e) -> addHeaders(consumerRecord, e, getAttempts(consumerRecord)));
		return recoverer;
	}

	private TopicPartition resolveDestination(ConsumerRecord<?, ?> cr, Exception e, Configuration configuration) {
		if (isBackoffException(e)) {
			throw (NestedRuntimeException) e; // Necessary to not commit the offset and seek to current again
		}

		int attempt = getAttempts(cr);

		DestinationTopic nextDestination = this.destinationTopicResolver.resolveNextDestination(cr.topic(), attempt, e);

		return nextDestination.isNoOpsTopic()
					? new TopicPartition(NO_OPS_RETRY_TOPIC, 0)
					: new TopicPartition(nextDestination.getDestinationName(),
				cr.partition() % nextDestination.getDestinationPartitions());
	}

	private boolean isBackoffException(Exception e) {
		return NestedRuntimeException.class.isAssignableFrom(e.getClass())
				&& ((NestedRuntimeException) e).contains(KafkaBackoffException.class);
	}

	private int getAttempts(ConsumerRecord<?, ?> consumerRecord) {
		Header header = consumerRecord.headers().lastHeader(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS);
		return header != null
				? header.value()[0]
				: 1;
	}

	private Headers addHeaders(ConsumerRecord<?, ?> consumerRecord, Exception e, int attempts) {
		Headers headers = filterPreviousDltHeaders(consumerRecord.headers());
		headers.add(RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS,
				BigInteger.valueOf(attempts + 1).toByteArray());
		headers.add(RetryTopicHeaders.DEFAULT_HEADER_BACKOFF_TIMESTAMP,
					this.destinationTopicResolver
							.resolveDestinationNextExecutionTime(consumerRecord.topic(), attempts, e).getBytes());
		return headers;
	}

	// TODO: Integrate better with the DeadLetterPublisherRecoverer so that the headers end up better.
	private RecordHeaders filterPreviousDltHeaders(Headers headers) {
		return StreamSupport
				.stream(headers.spliterator(), false)
				.filter(header -> !new String(header.value()).contains(KAFKA_DLT_HEADERS_PREFIX))
				.reduce(new RecordHeaders(), ((recordHeaders, header) -> {
					recordHeaders.add(header);
					return recordHeaders;
				}), (a, b) -> a);
	}

	public static class Configuration {

		private final KafkaOperations<?, ?> template;

		Configuration(KafkaOperations<?, ?> template) {
			Assert.notNull(template,
					() -> "You need to provide a KafkaOperations instance.");
			this.template = template;
		}
	}
}
