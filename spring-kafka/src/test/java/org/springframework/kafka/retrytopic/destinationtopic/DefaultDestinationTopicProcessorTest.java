package org.springframework.kafka.retrytopic.destinationtopic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DefaultDestinationTopicProcessorTest extends DestinationTopicTest {

	@Mock
	private DestinationTopicResolver destinationTopicResolver;

	@Captor
	private ArgumentCaptor<Map> destinationMapCaptor;

	private DefaultDestinationTopicProcessor destinationTopicProcessor =
			new DefaultDestinationTopicProcessor(destinationTopicResolver);

	@Test
	void shouldProcessDestinationProperties() {
		// setup
		DestinationTopicProcessor.Context context = new DestinationTopicProcessor.Context(allProps);
		List<DestinationTopic.Properties> processedProps = new ArrayList<>();

		// when
		destinationTopicProcessor.processDestinationProperties(props -> processedProps.add(props), context);

		// then
		assertEquals(allProps, processedProps);
	}

	@Test
	void shouldRegisterTopicDestinations() {
		// setup
		DestinationTopicProcessor.Context context = new DestinationTopicProcessor.Context(allProps);

		// when
		registerFirstTopicDestinations(context);
		registerSecondTopicDestinations(context);

		// then
		assertTrue(context.destinationsByTopicMap.containsKey(FIRST_TOPIC));
		List<DestinationTopic> destinationTopicsForFirstTopic = context.destinationsByTopicMap.get(FIRST_TOPIC);
		assertEquals(4, destinationTopicsForFirstTopic.size());
		assertEquals(mainDestinationTopic, destinationTopicsForFirstTopic.get(0));
		assertEquals(firstRetryDestinationTopic, destinationTopicsForFirstTopic.get(1));
		assertEquals(secondRetryDestinationTopic, destinationTopicsForFirstTopic.get(2));
		assertEquals(dltDestinationTopic, destinationTopicsForFirstTopic.get(3));

		assertTrue(context.destinationsByTopicMap.containsKey(SECOND_TOPIC));
		List<DestinationTopic> destinationTopicsForSecondTopic = context.destinationsByTopicMap.get(SECOND_TOPIC);
		assertEquals(4, destinationTopicsForSecondTopic.size());
		assertEquals(mainDestinationTopic2, destinationTopicsForSecondTopic.get(0));
		assertEquals(firstRetryDestinationTopic2, destinationTopicsForSecondTopic.get(1));
		assertEquals(secondRetryDestinationTopic2, destinationTopicsForSecondTopic.get(2));
		assertEquals(dltDestinationTopic2, destinationTopicsForSecondTopic.get(3));
	}

	private void registerFirstTopicDestinations(DestinationTopicProcessor.Context context) {
		allFirstDestinationsHolders.forEach(propsHolder ->
				destinationTopicProcessor.registerDestinationTopic(FIRST_TOPIC,
						getSuffixedName(propsHolder), propsHolder.props, context));
	}

	private String getSuffixedName(PropsHolder propsHolder) {
		return propsHolder.topicName + propsHolder.props.suffix();
	}

	private void registerSecondTopicDestinations(DestinationTopicProcessor.Context context) {
		allSecondDestinationHolders.forEach(propsHolder ->
				destinationTopicProcessor.registerDestinationTopic(SECOND_TOPIC,
						getSuffixedName(propsHolder), propsHolder.props, context));
	}

	@Test
	void shouldCreateDestinationMap() {
		// setup
		DefaultDestinationTopicProcessor destinationTopicProcessor =
				new DefaultDestinationTopicProcessor(destinationTopicResolver);

		DestinationTopicProcessor.Context context = new DestinationTopicProcessor.Context(allProps);

		// when
		registerFirstTopicDestinations(context);
		registerSecondTopicDestinations(context);
		destinationTopicProcessor.processRegisteredDestinations(topic -> {}, context);

		// then
		verify(destinationTopicResolver).addDestinations(destinationMapCaptor.capture());
		Map<String, DestinationTopicResolver.DestinationsHolder> destinationMap = destinationMapCaptor.getValue();

		assertEquals(8, destinationMap.size());

		assertTrue(destinationMap.containsKey(mainDestinationTopic.getDestinationName()));
		assertEquals(mainDestinationTopic, destinationMap.get(mainDestinationTopic.getDestinationName()).getSourceDestination());
		assertEquals(firstRetryDestinationTopic, destinationMap.get(mainDestinationTopic.getDestinationName()).getNextDestination());
		assertTrue(destinationMap.containsKey(firstRetryDestinationTopic.getDestinationName()));
		assertEquals(firstRetryDestinationTopic, destinationMap.get(firstRetryDestinationTopic.getDestinationName()).getSourceDestination());
		assertEquals(secondRetryDestinationTopic, destinationMap.get(firstRetryDestinationTopic.getDestinationName()).getNextDestination());
		assertTrue(destinationMap.containsKey(secondRetryDestinationTopic.getDestinationName()));
		assertEquals(secondRetryDestinationTopic, destinationMap.get(secondRetryDestinationTopic.getDestinationName()).getSourceDestination());
		assertEquals(dltDestinationTopic, destinationMap.get(secondRetryDestinationTopic.getDestinationName()).getNextDestination());
		assertTrue(destinationMap.containsKey(dltDestinationTopic.getDestinationName()));
		assertEquals(dltDestinationTopic, destinationMap.get(dltDestinationTopic.getDestinationName()).getSourceDestination());
		assertEquals(noOpsDestinationTopic, destinationMap.get(dltDestinationTopic.getDestinationName()).getNextDestination());

		assertTrue(destinationMap.containsKey(mainDestinationTopic2.getDestinationName()));
		assertEquals(mainDestinationTopic2, destinationMap.get(mainDestinationTopic2.getDestinationName()).getSourceDestination());
		assertEquals(firstRetryDestinationTopic2, destinationMap.get(mainDestinationTopic2.getDestinationName()).getNextDestination());
		assertTrue(destinationMap.containsKey(firstRetryDestinationTopic2.getDestinationName()));
		assertEquals(firstRetryDestinationTopic2, destinationMap.get(firstRetryDestinationTopic2.getDestinationName()).getSourceDestination());
		assertEquals(secondRetryDestinationTopic2, destinationMap.get(firstRetryDestinationTopic2.getDestinationName()).getNextDestination());
		assertTrue(destinationMap.containsKey(secondRetryDestinationTopic2.getDestinationName()));
		assertEquals(secondRetryDestinationTopic2, destinationMap.get(secondRetryDestinationTopic2.getDestinationName()).getSourceDestination());
		assertEquals(dltDestinationTopic2, destinationMap.get(secondRetryDestinationTopic2.getDestinationName()).getNextDestination());
		assertTrue(destinationMap.containsKey(dltDestinationTopic2.getDestinationName()));
		assertEquals(dltDestinationTopic2, destinationMap.get(dltDestinationTopic2.getDestinationName()).getSourceDestination());
		assertEquals(noOpsDestinationTopic2, destinationMap.get(dltDestinationTopic2.getDestinationName()).getNextDestination());

	}

	@Test
	void shouldApplyTopicsCallback() {
		// setup
		DefaultDestinationTopicProcessor destinationTopicProcessor =
				new DefaultDestinationTopicProcessor(destinationTopicResolver);

		DestinationTopicProcessor.Context context = new DestinationTopicProcessor.Context(allProps);

		List<String> allTopics = allFirstDestinationsTopics
				.stream()
				.map(destinationTopic -> destinationTopic.getDestinationName())
				.collect(Collectors.toList());

		allTopics.addAll(allSecondDestinationTopics
				.stream()
				.map(destinationTopic -> destinationTopic.getDestinationName())
				.collect(Collectors.toList()));

		List<String> allProcessedTopics = new ArrayList<>();

		// when
		registerFirstTopicDestinations(context);
		registerSecondTopicDestinations(context);
		destinationTopicProcessor.processRegisteredDestinations(topics -> allProcessedTopics.addAll(topics), context);

		// then
		assertEquals(allTopics, allProcessedTopics);

	}
}