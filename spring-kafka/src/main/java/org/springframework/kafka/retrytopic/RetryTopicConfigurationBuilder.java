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

import java.util.ArrayList;
import java.util.List;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.classify.BinaryExceptionClassifierBuilder;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.retrytopic.destinationtopic.DestinationTopic;
import org.springframework.kafka.retrytopic.destinationtopic.DestinationTopicPropertiesFactory;
import org.springframework.kafka.support.AllowDenyCollectionManager;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.backoff.SleepingBackOffPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.util.Assert;

/**
 *
 * Builder class to create {@link RetryTopicConfiguration} instances.
 *
 * @author Tomaz Fernandes
 * @since 2.7.0
 *
 */
public class RetryTopicConfigurationBuilder {
	private int maxAttempts = BackOffValuesGenerator.NOT_SET;
	private BackOffPolicy backOffPolicy;
	private RetryTopicConfigurer.EndpointHandlerMethod dltHandlerMethod;
	private List<String> includeTopicNames = new ArrayList<>();
	private List<String> excludeTopicNames = new ArrayList<>();
	private String retryTopicSuffix;
	private String dltSuffix;
	private RetryTopicConfiguration.TopicCreation topicCreationConfiguration = new RetryTopicConfiguration.TopicCreation();
	private ConcurrentKafkaListenerContainerFactory<?, ?> listenerContainerFactory;
	private String listenerContainerFactoryName;
	private BinaryExceptionClassifierBuilder classifierBuilder;
	private RetryTopicConfiguration.FixedDelayTopicStrategy fixedDelayTopicStrategy =
			RetryTopicConfiguration.FixedDelayTopicStrategy.MULTIPLE_TOPICS;
	private RetryTopicConfiguration.DltProcessingFailureStrategy dltProcessingFailureStrategy =
			RetryTopicConfiguration.DltProcessingFailureStrategy.ALWAYS_RETRY;

	/* ---------------- Configure Dlt Bean and Method -------------- */
	public RetryTopicConfigurationBuilder dltHandlerMethod(Class<?> clazz, String methodName) {
		this.dltHandlerMethod = RetryTopicConfigurer.createHandlerMethodWith(clazz, methodName);
		return this;
	}

	RetryTopicConfigurationBuilder dltHandlerMethod(RetryTopicConfigurer.EndpointHandlerMethod endpointHandlerMethod) {
		this.dltHandlerMethod = endpointHandlerMethod;
		return this;
	}

	/* ---------------- Configure Topic GateKeeper -------------- */
	public RetryTopicConfigurationBuilder includeTopics(List<String> topicNames) {
		this.includeTopicNames.addAll(topicNames);
		return this;
	}

	public RetryTopicConfigurationBuilder excludeTopics(List<String> topicNames) {
		this.excludeTopicNames.addAll(topicNames);
		return this;
	}

	public RetryTopicConfigurationBuilder includeTopic(String topicName) {
		this.includeTopicNames.add(topicName);
		return this;
	}

	public RetryTopicConfigurationBuilder excludeTopic(String topicName) {
		this.excludeTopicNames.add(topicName);
		return this;
	}

	/* ---------------- Configure Topic Suffixes -------------- */

	public RetryTopicConfigurationBuilder retryTopicSuffix(String suffix) {
		this.retryTopicSuffix = suffix;
		return this;
	}

	public RetryTopicConfigurationBuilder dltSuffix(String suffix) {
		this.dltSuffix = suffix;
		return this;
	}

	/* ---------------- Configure BackOff -------------- */

	public RetryTopicConfigurationBuilder maxAttempts(int maxAttempts) {
		Assert.isTrue(maxAttempts > 0, "Number of attempts should be positive");
		Assert.isTrue(this.maxAttempts == BackOffValuesGenerator.NOT_SET,
				"You have already set the number of attempts");
		this.maxAttempts = maxAttempts;
		return this;
	}

	public RetryTopicConfigurationBuilder exponentialBackoff(long initialInterval, double multiplier, long maxInterval) {
		return exponentialBackoff(initialInterval, multiplier, maxInterval, false);
	}

	public RetryTopicConfigurationBuilder exponentialBackoff(long initialInterval, double multiplier, long maxInterval,
															boolean withRandom) {
		Assert.isNull(this.backOffPolicy, "You have already selected backoff policy");
		Assert.isTrue(initialInterval >= 1, "Initial interval should be >= 1");
		Assert.isTrue(multiplier > 1, "Multiplier should be > 1");
		Assert.isTrue(maxInterval > initialInterval, "Max interval should be > than initial interval");
		ExponentialBackOffPolicy policy = withRandom ? new ExponentialRandomBackOffPolicy()
				: new ExponentialBackOffPolicy();
		policy.setInitialInterval(initialInterval);
		policy.setMultiplier(multiplier);
		policy.setMaxInterval(maxInterval);
		this.backOffPolicy = policy;
		return this;
	}

	public RetryTopicConfigurationBuilder fixedBackOff(long interval) {
		Assert.isNull(this.backOffPolicy, "You have already selected backoff policy");
		Assert.isTrue(interval >= 1, "Interval should be >= 1");
		FixedBackOffPolicy policy = new FixedBackOffPolicy();
		policy.setBackOffPeriod(interval);
		this.backOffPolicy = policy;
		return this;
	}

	public RetryTopicConfigurationBuilder uniformRandomBackoff(long minInterval, long maxInterval) {
		Assert.isNull(this.backOffPolicy, "You have already selected backoff policy");
		Assert.isTrue(minInterval >= 1, "Min interval should be >= 1");
		Assert.isTrue(maxInterval >= 1, "Max interval should be >= 1");
		Assert.isTrue(maxInterval > minInterval, "Max interval should be > than min interval");
		UniformRandomBackOffPolicy policy = new UniformRandomBackOffPolicy();
		policy.setMinBackOffPeriod(minInterval);
		policy.setMaxBackOffPeriod(maxInterval);
		this.backOffPolicy = policy;
		return this;
	}

	public RetryTopicConfigurationBuilder noBackoff() {
		Assert.isNull(this.backOffPolicy, "You have already selected backoff policy");
		this.backOffPolicy = new NoBackOffPolicy();
		return this;
	}

	public RetryTopicConfigurationBuilder customBackoff(SleepingBackOffPolicy<?> backOffPolicy) {
		Assert.isNull(this.backOffPolicy, "You have already selected backoff policy");
		Assert.notNull(backOffPolicy, "You should provide non null custom policy");
		this.backOffPolicy = backOffPolicy;
		return this;
	}

	public RetryTopicConfigurationBuilder fixedBackOff(int interval) {
		FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
		backOffPolicy.setBackOffPeriod(interval);
		this.backOffPolicy = backOffPolicy;
		return this;
	}

	public RetryTopicConfigurationBuilder useSameTopicForFixedDelays() {
		this.fixedDelayTopicStrategy = RetryTopicConfiguration.FixedDelayTopicStrategy.SINGLE_TOPIC;
		return this;
	}

	RetryTopicConfigurationBuilder useSameTopicForFixedDelays(RetryTopicConfiguration.FixedDelayTopicStrategy useSameTopicForFixedDelays) {
		this.fixedDelayTopicStrategy = useSameTopicForFixedDelays;
		return this;
	}

	/* ---------------- Configure Topics Auto Creation -------------- */

	public RetryTopicConfigurationBuilder doNotAutoCreateRetryTopics() {
		this.topicCreationConfiguration = new RetryTopicConfiguration.TopicCreation(false);
		return this;
	}

	public RetryTopicConfigurationBuilder autoCreateTopicsWith(int numPartitions, short replicationFactor) {
		this.topicCreationConfiguration = new RetryTopicConfiguration.TopicCreation(true, numPartitions, replicationFactor);
		return this;
	}

	public RetryTopicConfigurationBuilder autoCreateTopics(boolean shouldCreate, int numPartitions, short replicationFactor) {
		this.topicCreationConfiguration = new RetryTopicConfiguration.TopicCreation(shouldCreate, numPartitions, replicationFactor);
		return this;
	}

	/* ---------------- Configure Exception Classifier -------------- */

	public RetryTopicConfigurationBuilder retryOn(Class<? extends Throwable> throwable) {
		classifierBuilder().retryOn(throwable);
		return this;
	}

	public RetryTopicConfigurationBuilder notRetryOn(Class<? extends Throwable> throwable) {
		classifierBuilder().notRetryOn(throwable);
		return this;
	}

	public RetryTopicConfigurationBuilder retryOn(List<Class<? extends Throwable>> throwables) {
		throwables
				.stream()
				.forEach(throwable -> classifierBuilder().retryOn(throwable));
		return this;
	}

	public RetryTopicConfigurationBuilder notRetryOn(List<Class<? extends Throwable>> throwables) {
		throwables
				.stream()
				.forEach(throwable -> classifierBuilder().notRetryOn(throwable));
		return this;
	}

	public RetryTopicConfigurationBuilder traversingCauses() {
		classifierBuilder().traversingCauses();
		return this;
	}

	RetryTopicConfigurationBuilder traversingCauses(boolean traversing) {
		if (traversing) {
			classifierBuilder().traversingCauses();
		}
		return this;
	}

	private BinaryExceptionClassifierBuilder classifierBuilder() {
		if (this.classifierBuilder == null) {
			this.classifierBuilder = new BinaryExceptionClassifierBuilder();
		}
		return this.classifierBuilder;
	}

	/* ---------------- DLT Processing Failure Behavior -------------- */
	public RetryTopicConfigurationBuilder abortOnDltFailure() {
		this.dltProcessingFailureStrategy =
				RetryTopicConfiguration.DltProcessingFailureStrategy.ABORT;
		return this;
	}

	RetryTopicConfigurationBuilder dltProcessingFailureStrategy(
			RetryTopicConfiguration.DltProcessingFailureStrategy dltProcessingFailureStrategy) {
		this.dltProcessingFailureStrategy = dltProcessingFailureStrategy;
		return this;
	}


	/* ---------------- Configure KafkaListenerContainerFactory -------------- */
	public RetryTopicConfigurationBuilder listenerFactory(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
		this.listenerContainerFactory = factory;
		return this;
	}

	public RetryTopicConfigurationBuilder listenerFactory(String factoryBeanName) {
		this.listenerContainerFactoryName = factoryBeanName;
		return this;
	}

	// The templates are configured per ListenerContainerFactory. Only the first configured ones will be used.
	public RetryTopicConfiguration create(KafkaOperations<?, ?> sendToTopicKafkaTemplate) {
		ListenerContainerFactoryResolver.Configuration listenerContainerFactory =
				new ListenerContainerFactoryResolver.Configuration(this.listenerContainerFactory,
						this.listenerContainerFactoryName);
		DeadLetterPublishingRecovererFactory.Configuration deadLetterProviderConfig =
				new DeadLetterPublishingRecovererFactory.Configuration(sendToTopicKafkaTemplate);
		AllowDenyCollectionManager<String> allowListManager =
				new AllowDenyCollectionManager<>(this.includeTopicNames, this.excludeTopicNames);
		List<DestinationTopic.Properties> destinationTopicProperties =
				new DestinationTopicPropertiesFactory(this.retryTopicSuffix, this.dltSuffix, this.maxAttempts,
						this.backOffPolicy, buildClassifier(), this.topicCreationConfiguration.getNumPartitions(),
						sendToTopicKafkaTemplate, this.fixedDelayTopicStrategy, this.dltProcessingFailureStrategy)
						.createProperties();
		return new RetryTopicConfiguration(destinationTopicProperties, deadLetterProviderConfig,
				this.dltHandlerMethod, this.topicCreationConfiguration, allowListManager, listenerContainerFactory);
	}

	private BinaryExceptionClassifier buildClassifier() {
		return this.classifierBuilder != null
				? this.classifierBuilder.build()
				: new BinaryExceptionClassifierBuilder().retryOn(Throwable.class).build();
	}

}
