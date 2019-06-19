/*
 * Copyright 2017-2019 the original author or authors.
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

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * @author Gary Russell
 * @author Chris Gilbert
 * @since 1.0.6
 */
@EmbeddedKafka(topics = { "txCache1", "txCache2", "txCacheSendFromListener" },
		brokerProperties = {
				"transaction.state.log.replication.factor=1",
				"transaction.state.log.min.isr=1"}
)
@SpringJUnitConfig
@DirtiesContext
public class DefaultKafkaConsumerFactoryTests {

	@Autowired
	private EmbeddedKafkaBroker embeddedKafka;

	@Test
	public void testProvidedDeserializerInstancesAreShared() {
		ConsumerFactory<String, String> target = new DefaultKafkaConsumerFactory<>(Collections.emptyMap(), new StringDeserializer() {

		}, null);
		assertThat(target.getKeyDeserializer()).isSameAs(target.getKeyDeserializer());
	}

	@Test
	public void testSupplierProvidedDeserializersAreNotShared() {
		ConsumerFactory<String, String> target = new DefaultKafkaConsumerFactory<>(Collections.emptyMap(), () -> new StringDeserializer() {

		}, null);
		assertThat(target.getKeyDeserializer()).isNotSameAs(target.getKeyDeserializer());
	}

	@Test
	public void testNoOverrides() {
		Map<String, Object> originalConfig = Collections.emptyMap();
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps).isEqualTo(originalConfig);
				return null;
			}
		}.createConsumer(null, null, null, null);
	}

	@Test
	public void testPropertyOverrides() {
		Map<String, Object> originalConfig = Stream
				.of(new AbstractMap.SimpleEntry<>("config1", new Object()),
						new AbstractMap.SimpleEntry<>("config2", new Object()))
				.collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

		Properties overrides = new Properties();
		overrides.setProperty("config1", "overridden");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get("config1")).isEqualTo("overridden");
				assertThat(configProps.get("config2")).isSameAs(originalConfig.get("config2"));
				return null;
			}
		}.createConsumer(null, null, null, overrides);
	}

	@Test
	public void testClientIdSuffixOnDefault() {
		Map<String, Object> originalConfig = Collections.singletonMap(ConsumerConfig.CLIENT_ID_CONFIG, "original");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.CLIENT_ID_CONFIG)).isEqualTo("original-1");
				return null;
			}
		}.createConsumer(null, null, "-1", null);
	}

	@Test
	public void testClientIdSuffixWithoutDefault() {
		new DefaultKafkaConsumerFactory<String, String>(Collections.emptyMap()) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.CLIENT_ID_CONFIG)).isNull();
				return null;
			}
		}.createConsumer(null, null, "-1", null);
	}

	@Test
	public void testClientIdPrefixOnDefault() {
		Map<String, Object> originalConfig = Collections.singletonMap(ConsumerConfig.CLIENT_ID_CONFIG, "original");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.CLIENT_ID_CONFIG)).isEqualTo("overridden");
				return null;
			}
		}.createConsumer(null, "overridden", null, null);
	}

	@Test
	public void testClientIdPrefixWithoutDefault() {
		new DefaultKafkaConsumerFactory<String, String>(Collections.emptyMap()) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.CLIENT_ID_CONFIG)).isEqualTo("overridden");
				return null;
			}
		}.createConsumer(null, "overridden", null, null);
	}

	@Test
	public void testClientIdSuffixAndPrefixOnDefault() {
		Map<String, Object> originalConfig = Collections.singletonMap(ConsumerConfig.CLIENT_ID_CONFIG, "original");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.CLIENT_ID_CONFIG)).isEqualTo("overridden-1");
				return null;
			}
		}.createConsumer(null, "overridden", "-1", null);
	}

	@Test
	public void testClientIdSuffixAndPrefixOnPropertyOverride() {
		Map<String, Object> originalConfig = Collections.singletonMap(ConsumerConfig.CLIENT_ID_CONFIG, "original");
		Properties overrides = new Properties();
		overrides.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, "property-overridden");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.CLIENT_ID_CONFIG)).isEqualTo("overridden-1");
				return null;
			}
		}.createConsumer(null, "overridden", "-1", overrides);
	}


	@Test
	public void testGroupIdOnDefault() {
		Map<String, Object> originalConfig = Collections.singletonMap(ConsumerConfig.GROUP_ID_CONFIG, "original");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("overridden");
				return null;
			}
		}.createConsumer("overridden", null, null, null);
	}

	@Test
	public void testGroupIdWithoutDefault() {
		new DefaultKafkaConsumerFactory<String, String>(Collections.emptyMap()) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("overridden");
				return null;
			}
		}.createConsumer("overridden", null, null, null);
	}

	@Test
	public void testGroupIdOnPropertyOverride() {
		Map<String, Object> originalConfig = Collections.singletonMap(ConsumerConfig.GROUP_ID_CONFIG, "original");
		Properties overrides = new Properties();
		overrides.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "property-overridden");
		new DefaultKafkaConsumerFactory<String, String>(originalConfig) {
			protected KafkaConsumer<String, String> createKafkaConsumer(Map<String, Object> configProps) {
				assertThat(configProps.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("overridden");
				return null;
			}
		}.createConsumer("overridden", null, null, overrides);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNestedTxProducerIsCached() throws Exception {
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(this.embeddedKafka);
		producerProps.put(ProducerConfig.RETRIES_CONFIG, 1);
		DefaultKafkaProducerFactory<Integer, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
		KafkaTemplate<Integer, String> template = new KafkaTemplate<>(pf);
			DefaultKafkaProducerFactory<Integer, String> pfTx = new DefaultKafkaProducerFactory<>(producerProps);
		pfTx.setTransactionIdPrefix("fooTx.");
		KafkaTemplate<Integer, String> templateTx = new KafkaTemplate<>(pfTx);
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("txCache1Group", "false", this.embeddedKafka);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		DefaultKafkaConsumerFactory<Integer, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
		ContainerProperties containerProps = new ContainerProperties("txCache1");
		CountDownLatch latch = new CountDownLatch(1);
		containerProps.setMessageListener((MessageListener<Integer, String>) r -> {
			templateTx.executeInTransaction(t -> t.send("txCacheSendFromListener", "bar"));
			templateTx.executeInTransaction(t -> t.send("txCacheSendFromListener", "baz"));
			latch.countDown();
		});
		KafkaTransactionManager<Integer, String> tm = new KafkaTransactionManager<>(pfTx);
		containerProps.setTransactionManager(tm);
		KafkaMessageListenerContainer<Integer, String> container = new KafkaMessageListenerContainer<>(cf,
				containerProps);
		container.start();
		try {
			ListenableFuture<SendResult<Integer, String>> future = template.send("txCache1", "foo");
			future.get(10, TimeUnit.SECONDS);
			pf.getCache();
			assertThat(KafkaTestUtils.getPropertyValue(pf, "cache", Map.class)).hasSize(0);
			assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
			assertThat(KafkaTestUtils.getPropertyValue(pfTx, "cache", Map.class)).hasSize(1);
			assertThat(pfTx.getCache()).hasSize(1);
		}
		finally {
			container.stop();
			pf.destroy();
			pfTx.destroy();
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testContainerTxProducerIsNotCached() throws Exception {
		Map<String, Object> producerProps = KafkaTestUtils.producerProps(this.embeddedKafka);
		producerProps.put(ProducerConfig.RETRIES_CONFIG, 1);
		DefaultKafkaProducerFactory<Integer, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
		KafkaTemplate<Integer, String> template = new KafkaTemplate<>(pf);
			DefaultKafkaProducerFactory<Integer, String> pfTx = new DefaultKafkaProducerFactory<>(producerProps);
		pfTx.setTransactionIdPrefix("fooTx.");
		KafkaTemplate<Integer, String> templateTx = new KafkaTemplate<>(pfTx);
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("txCache2Group", "false", this.embeddedKafka);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		DefaultKafkaConsumerFactory<Integer, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
		ContainerProperties containerProps = new ContainerProperties("txCache2");
		CountDownLatch latch = new CountDownLatch(1);
		containerProps.setMessageListener((MessageListener<Integer, String>) r -> {
			templateTx.send("txCacheSendFromListener", "bar");
			templateTx.send("txCacheSendFromListener", "baz");
			latch.countDown();
		});
		KafkaTransactionManager<Integer, String> tm = new KafkaTransactionManager<>(pfTx);
		containerProps.setTransactionManager(tm);
		KafkaMessageListenerContainer<Integer, String> container = new KafkaMessageListenerContainer<>(cf,
				containerProps);
		container.start();
		try {
			ListenableFuture<SendResult<Integer, String>> future = template.send("txCache2", "foo");
			future.get(10, TimeUnit.SECONDS);
			assertThat(KafkaTestUtils.getPropertyValue(pf, "cache", Map.class)).hasSize(0);
			assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
			assertThat(KafkaTestUtils.getPropertyValue(pfTx, "cache", Map.class)).hasSize(0);
		}
		finally {
			container.stop();
			pf.destroy();
			pfTx.destroy();
		}
	}

	@Configuration
	public static class Config {

	}

}
