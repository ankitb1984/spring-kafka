/*
 * Copyright 2015-2016 the original author or authors.
 *
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
 */

package org.springframework.kafka.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;


/**
 * A template for executing high-level operations.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 *
 * @author Marius Bogoevici
 * @author Gary Russell
 */
public class AsyncKafkaTemplate<K, V> implements AsyncKafkaOperations<K, V> {

	protected final Log logger = LogFactory.getLog(this.getClass()); //NOSONAR

	private final ProducerFactory<K, V> producerFactory;

	private volatile Producer<K, V> producer;

	private volatile String defaultTopic;

	/**
	 * Create an instance using the supplied producer factory.
	 * @param producerFactory the producer factory.
	 */
	public AsyncKafkaTemplate(ProducerFactory<K, V> producerFactory) {
		this.producerFactory = producerFactory;
	}

	/**
	 * The default topic for send methods where a topic is not
	 * providing.
	 * @return the topic.
	 */
	public String getDefaultTopic() {
		return this.defaultTopic;
	}

	/**
	 * Set the default topic for send methods where a topic is not
	 * providing.
	 * @param defaultTopic the topic.
	 */
	public void setDefaultTopic(String defaultTopic) {
		this.defaultTopic = defaultTopic;
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(V data) {
		return send(this.defaultTopic, data);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(K key, V data) {
		return send(this.defaultTopic, key, data);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(int partition, K key, V data) {
		return send(this.defaultTopic, partition, key, data);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(String topic, V data) {
		ProducerRecord<K, V> producerRecord = new ProducerRecord<>(topic, data);
		return doSend(producerRecord);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(String topic, K key, V data) {
		ProducerRecord<K, V> producerRecord = new ProducerRecord<>(topic, key, data);
		return doSend(producerRecord);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(String topic, int partition, V data) {
		ProducerRecord<K, V> producerRecord = new ProducerRecord<K, V>(topic, partition, null, data);
		return doSend(producerRecord);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(String topic, int partition, K key, V data) {
		ProducerRecord<K, V> producerRecord = new ProducerRecord<>(topic, partition, key, data);
		return doSend(producerRecord);
	}

	@Override
	public ListenableFuture<SendResult<K, V>> send(Message<?> message) {
		ProducerRecord<K, V> producerRecord = messageToProducerRecord(message);
		return doSend(producerRecord);
	}

	@Override
	public void flush() {
		this.producer.flush();
	}

	/**
	 * Send the producer record.
	 * @param producerRecord the producer record.
	 * @return a Future for the {@link RecordMetadata}.
	 */
	protected ListenableFuture<SendResult<K, V>> doSend(final ProducerRecord<K, V> producerRecord) {
		if (this.producer == null) {
			synchronized (this) {
				if (this.producer == null) {
					this.producer = this.producerFactory.createProducer();
				}
			}
		}
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Sending: " + producerRecord);
		}
		final SettableListenableFuture<SendResult<K, V>> future = new SettableListenableFuture<>();
		this.producer.send(producerRecord, new Callback() {

			@Override
			public void onCompletion(RecordMetadata metadata, Exception exception) {
				if (exception == null) {
					future.set(new SendResult<>(producerRecord, metadata));
				}
				else {
					future.setException(new KafkaProducerException(producerRecord, "Failed to send", exception));
				}
			}

		});
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Sent: " + producerRecord);
		}
		return future;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ProducerRecord<K, V> messageToProducerRecord(Message<?> message) {
		MessageHeaders headers = message.getHeaders();
		String topic = headers.get(KafkaHeaders.TOPIC, String.class);
		Integer partition = headers.get(KafkaHeaders.PARTITION_ID, Integer.class);
		Object key = headers.get(KafkaHeaders.MESSAGE_KEY);
		Object payload = message.getPayload();
		return new ProducerRecord(topic == null ? this.defaultTopic : topic, partition, key, payload);
	}

}
