/*
 * Copyright 2017-2024 the original author or authors.
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

package org.springframework.kafka.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.assertj.core.util.Streams;

import org.springframework.messaging.MessageHeaders;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default header mapper for Apache Kafka.
 * Most headers in {@link KafkaHeaders} are not mapped on outbound messages.
 * The exceptions are correlation and reply headers for request/reply
 * messaging.
 * Header types are added to a special header {@link #JSON_TYPES}.
 *
 * @author Gary Russell
 * @author Artem Bilan
 * @author Soby Chacko
 * @author Grzegorz Poznachowski
 *
 * @since 1.3
 */
public class DefaultKafkaHeaderMapper extends AbstractKafkaHeaderMapper {

	private static final String ITERABLE_HEADER_TYPE_PATTERN = "%s#%s";

	private static final String JAVA_LANG_STRING = "java.lang.String";

	private static final Set<String> TRUSTED_ARRAY_TYPES = Set.of(
					"[B",
					"[I",
					"[J",
					"[F",
					"[D",
					"[C"
			);

	private static final List<String> DEFAULT_TRUSTED_PACKAGES = List.of(
					"java.lang",
					"java.net",
					"java.util",
					"org.springframework.util"
			);

	private static final List<String> DEFAULT_TO_STRING_CLASSES = List.of(
					"org.springframework.util.MimeType",
					"org.springframework.http.MediaType"
			);

	/**
	 * Header name for java types of other headers.
	 */
	public static final String JSON_TYPES = "spring_json_header_types";

	private final ObjectMapper objectMapper;

	private final Set<String> trustedPackages = new LinkedHashSet<>(DEFAULT_TRUSTED_PACKAGES);

	private final Set<String> toStringClasses = new LinkedHashSet<>(DEFAULT_TO_STRING_CLASSES);

	private boolean encodeStrings;

	/**
	 * Construct an instance with the default object mapper and default header patterns
	 * for outbound headers; all inbound headers are mapped. The default pattern list is
	 * {@code "!id", "!timestamp" and "*"}. In addition, most of the headers in
	 * {@link KafkaHeaders} are never mapped as headers since they represent data in
	 * consumer/producer records.
	 *
	 * @see #DefaultKafkaHeaderMapper(ObjectMapper)
	 */
	public DefaultKafkaHeaderMapper() {
		this(JacksonUtils.enhancedObjectMapper());
	}

	/**
	 * Construct an instance with the provided object mapper and default header patterns
	 * for outbound headers; all inbound headers are mapped. The patterns are applied in
	 * order, stopping on the first match (positive or negative). Patterns are negated by
	 * preceding them with "!". The default pattern list is
	 * {@code "!id", "!timestamp" and "*"}. In addition, most of the headers in
	 * {@link KafkaHeaders} are never mapped as headers since they represent data in
	 * consumer/producer records.
	 *
	 * @param objectMapper the object mapper.
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	public DefaultKafkaHeaderMapper(ObjectMapper objectMapper) {
		this(objectMapper,
				"!" + MessageHeaders.ID,
				"!" + MessageHeaders.TIMESTAMP,
				"*");
	}

	/**
	 * Construct an instance with a default object mapper and the provided header patterns
	 * for outbound headers; all inbound headers are mapped. The patterns are applied in
	 * order, stopping on the first match (positive or negative). Patterns are negated by
	 * preceding them with "!". The patterns will replace the default patterns; you
	 * generally should not map the {@code "id" and "timestamp"} headers. Note:
	 * most of the headers in {@link KafkaHeaders} are ever mapped as headers since they
	 * represent data in consumer/producer records.
	 *
	 * @param patterns the patterns.
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	public DefaultKafkaHeaderMapper(String... patterns) {
		this(JacksonUtils.enhancedObjectMapper(), patterns);
	}

	/**
	 * Construct an instance with the provided object mapper and the provided header
	 * patterns for outbound headers; all inbound headers are mapped. The patterns are
	 * applied in order, stopping on the first match (positive or negative). Patterns are
	 * negated by preceding them with "!". The patterns will replace the default patterns;
	 * you generally should not map the {@code "id" and "timestamp"} headers. Note: most
	 * of the headers in {@link KafkaHeaders} are never mapped as headers since they
	 * represent data in consumer/producer records.
	 *
	 * @param objectMapper the object mapper.
	 * @param patterns     the patterns.
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	public DefaultKafkaHeaderMapper(ObjectMapper objectMapper, String... patterns) {
		this(true, objectMapper, patterns);
	}

	private DefaultKafkaHeaderMapper(boolean outbound, ObjectMapper objectMapper, String... patterns) {
		super(outbound, patterns);
		Assert.notNull(objectMapper, "'objectMapper' must not be null");
		Assert.noNullElements(patterns, "'patterns' must not have null elements");
		this.objectMapper = objectMapper;
	}

	/**
	 * Create an instance for inbound mapping only with pattern matching.
	 *
	 * @param patterns the patterns to match.
	 * @return the header mapper.
	 * @since 2.8.8
	 */
	public static DefaultKafkaHeaderMapper forInboundOnlyWithMatchers(String... patterns) {
		return new DefaultKafkaHeaderMapper(false, JacksonUtils.enhancedObjectMapper(), patterns);
	}

	/**
	 * Create an instance for inbound mapping only with pattern matching.
	 *
	 * @param objectMapper the object mapper.
	 * @param patterns     the patterns to match.
	 * @return the header mapper.
	 * @since 2.8.8
	 */
	public static DefaultKafkaHeaderMapper forInboundOnlyWithMatchers(ObjectMapper objectMapper, String... patterns) {
		return new DefaultKafkaHeaderMapper(false, objectMapper, patterns);
	}

	/**
	 * Return the object mapper.
	 *
	 * @return the mapper.
	 */
	protected ObjectMapper getObjectMapper() {
		return this.objectMapper;
	}

	/**
	 * Provide direct access to the trusted packages set for subclasses.
	 *
	 * @return the trusted packages.
	 * @since 2.2
	 */
	protected Set<String> getTrustedPackages() {
		return this.trustedPackages;
	}

	/**
	 * Provide direct access to the toString() classes by subclasses.
	 *
	 * @return the toString() classes.
	 * @since 2.2
	 */
	protected Set<String> getToStringClasses() {
		return this.toStringClasses;
	}

	protected boolean isEncodeStrings() {
		return this.encodeStrings;
	}

	/**
	 * Set to true to encode String-valued headers as JSON string ("..."), by default just the
	 * raw String value is converted to a byte array using the configured charset. Set to
	 * true if a consumer of the outbound record is using Spring for Apache Kafka version
	 * less than 2.3
	 *
	 * @param encodeStrings true to encode (default false).
	 * @since 2.3
	 */
	public void setEncodeStrings(boolean encodeStrings) {
		this.encodeStrings = encodeStrings;
	}

	/**
	 * Add packages to the trusted packages list used
	 * when constructing objects from JSON.
	 * By default, the following packages are trusted:
	 * <ul>
	 *	<li>java.lang</li>
	 *  <li>java.net</li>
	 *  <li>java.util</li>
	 *  <li>org.springframework.util</li>
	 * </ul>
	 * If any of the supplied packages is {@code "*"}, all packages are trusted.
	 * If a class for a non-trusted package is encountered, the header is returned to the
	 * application with value of type {@link NonTrustedHeaderType}.
	 *
	 * @param packagesToTrust the packages to trust.
	 */
	public void addTrustedPackages(String... packagesToTrust) {
		if (packagesToTrust != null) {
			for (String trusted : packagesToTrust) {
				if ("*".equals(trusted)) {
					this.trustedPackages.clear();
					break;
				}
				else {
					this.trustedPackages.add(trusted);
				}
			}
		}
	}

	/**
	 * Add class names that the outbound mapper should perform toString() operations on
	 * before mapping.
	 *
	 * @param classNames the class names.
	 * @since 2.2
	 */
	public void addToStringClasses(String... classNames) {
		this.toStringClasses.addAll(Arrays.asList(classNames));
	}

	@Override
	public void fromHeaders(MessageHeaders headers, Headers target) {
		final Map<String, String> jsonHeaders = new HashMap<>();
		final ObjectMapper headerObjectMapper = getObjectMapper();
		headers.forEach((key, value) -> {
			if (matches(key, value)) {
				if (value instanceof List<?> values) {
					for (int i = 0; i < values.size(); i++) {
						resolveHeader(key, values.get(i), target, jsonHeaders, i);
					}
				}
				else {
					resolveHeader(key, value, target, jsonHeaders, null);
				}
			}
		});
		if (!jsonHeaders.isEmpty()) {
			try {
				target.add(new RecordHeader(JSON_TYPES, headerObjectMapper.writeValueAsBytes(jsonHeaders)));
			}
			catch (IllegalStateException | JsonProcessingException e) {
				logger.error(e, "Could not add json types header");
			}
		}
	}

	private void resolveHeader(String headerName, Object value, Headers target, Map<String, String> jsonHeaders, Integer headerIndex) {
		Object valueToAdd = headerValueToAddOut(headerName, value);
		if (valueToAdd instanceof byte[] byteArray) {
			target.add(new RecordHeader(headerName, byteArray));
		}
		else {
			try {
				String className = valueToAdd.getClass().getName();
				boolean encodeToJson = this.encodeStrings;
				if (this.toStringClasses.contains(className)) {
					valueToAdd = valueToAdd.toString();
					className = JAVA_LANG_STRING;
					encodeToJson = true;
				}
				if (!encodeToJson && valueToAdd instanceof String stringValue) {
					target.add(new RecordHeader(headerName, stringValue.getBytes(getCharset())));
				}
				else {
					target.add(new RecordHeader(headerName, this.objectMapper.writeValueAsBytes(valueToAdd)));
				}
				jsonHeaders.put(headerIndex == null ?
						headerName :
						ITERABLE_HEADER_TYPE_PATTERN.formatted(headerName, headerIndex), className);
			}
			catch (Exception e) {
				logger.error(e, () -> "Could not map " + headerName + " with type " + value.getClass().getName());
			}
		}
	}

	@Override
	public void toHeaders(Headers source, final Map<String, Object> target) {
		final Map<String, String> jsonTypes = decodeJsonTypes(source);

		Streams.stream(source)
				.collect(Collectors.groupingBy(Header::key))
				.forEach((headerName, headers) -> {
					Header lastHeader = headers.get(headers.size() - 1);
					if (headerName.equals(KafkaUtils.KEY_DESERIALIZER_EXCEPTION_HEADER) ||
							headerName.equals(KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)) {
						target.put(headerName, lastHeader);
					}
					else if (headerName.equals(KafkaHeaders.DELIVERY_ATTEMPT) && matchesForInbound(headerName)) {
						target.put(headerName, ByteBuffer.wrap(lastHeader.value()).getInt());
					}
					else if (headerName.equals(KafkaHeaders.LISTENER_INFO) && matchesForInbound(headerName)) {
						target.put(headerName, new String(lastHeader.value(), getCharset()));
					}
					else if (!(headerName.equals(JSON_TYPES)) && matchesForInbound(headerName)) {
						if (headers.size() == 1) {
							if (jsonTypes.containsKey(headerName)) {
								String requestedType = jsonTypes.get(headerName);
								target.put(headerName, resolveJsonValueHeader(headers.get(0), requestedType));
							}
							else {
								target.put(headerName, headerValueToAddIn(headers.get(0)));
							}
						}
						else {
							List<Object> valueList = new ArrayList<>();
							for (int i = 0; i < headers.size(); i++) {
								var jsonTypeIterableHeader = ITERABLE_HEADER_TYPE_PATTERN.formatted(headerName, i);
								if (jsonTypes.containsKey(jsonTypeIterableHeader)) {
									String requestedType = jsonTypes.get(jsonTypeIterableHeader);
									valueList.add(resolveJsonValueHeader(headers.get(i), requestedType));
								}
								else {
									valueList.add(headerValueToAddIn(headers.get(i)));
								}
							}
							Collections.reverse(valueList);
							target.put(headerName, valueList);
						}
					}
				});
	}

	private Object resolveJsonValueHeader(Header header, String requestedType) {
		Class<?> type = Object.class;
		boolean trusted = false;
		try {
			trusted = trusted(requestedType);
			if (trusted) {
				type = ClassUtils.forName(requestedType, null);
			}
		}
		catch (Exception e) {
			logger.error(e, () -> "Could not load class for header: " + header.key());
		}
		if (String.class.equals(type) && (header.value().length == 0 || header.value()[0] != '"')) {
			return new String(header.value(), getCharset());
		}
		else {
			if (trusted) {
				try {
					return decodeValue(header, type);
				}
				catch (IOException e) {
					logger.error(e, () ->
							"Could not decode json type: " + requestedType + " for key: " + header.key());
					return header.value();
				}
			}
			else {
				return new NonTrustedHeaderType(header.value(), requestedType);
			}
		}
	}

	private Object decodeValue(Header h, Class<?> type) throws IOException, LinkageError {
		ObjectMapper headerObjectMapper = getObjectMapper();
		Object value = headerObjectMapper.readValue(h.value(), type);
		if (type.equals(NonTrustedHeaderType.class)) {
			// Upstream NTHT propagated; may be trusted here...
			NonTrustedHeaderType nth = (NonTrustedHeaderType) value;
			if (trusted(nth.getUntrustedType())) {
				try {
					value = headerObjectMapper.readValue(nth.getHeaderValue(),
							ClassUtils.forName(nth.getUntrustedType(), null));
				}
				catch (Exception e) {
					logger.error(e, () -> "Could not decode header: " + nth);
				}
			}
		}
		return value;
	}

	private Map<String, String> decodeJsonTypes(Headers source) {
		Map<String, String> types = Collections.emptyMap();
		Header jsonTypes = source.lastHeader(JSON_TYPES);
		if (jsonTypes != null) {
			ObjectMapper headerObjectMapper = getObjectMapper();
			try {
				types = headerObjectMapper.readValue(jsonTypes.value(), new TypeReference<>() { });
			}
			catch (IOException e) {
				logger.error(e, () -> "Could not decode json types: " + new String(jsonTypes.value(), StandardCharsets.UTF_8));
			}
		}
		return types;
	}

	protected boolean trusted(String requestedType) {
		if (requestedType.equals(NonTrustedHeaderType.class.getName())) {
			return true;
		}
		if (TRUSTED_ARRAY_TYPES.contains(requestedType)) {
			return true;
		}
		String type = requestedType.startsWith("[") ? requestedType.substring(2) : requestedType;
		if (!this.trustedPackages.isEmpty()) {
			int lastDot = type.lastIndexOf('.');
			if (lastDot < 0) {
				return false;
			}
			String packageName = type.substring(0, lastDot);
			for (String trustedPackage : this.trustedPackages) {
				if (packageName.equals(trustedPackage) || packageName.startsWith(trustedPackage + ".")) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	/**
	 * Represents a header that could not be decoded due to an untrusted type.
	 */
	public static class NonTrustedHeaderType {

		private byte[] headerValue;

		private String untrustedType;

		public NonTrustedHeaderType() {
		}

		NonTrustedHeaderType(byte[] headerValue, String untrustedType) { // NOSONAR
			this.headerValue = headerValue; // NOSONAR
			this.untrustedType = untrustedType;
		}

		public void setHeaderValue(byte[] headerValue) { // NOSONAR
			this.headerValue = headerValue; // NOSONAR array reference
		}

		public byte[] getHeaderValue() {
			return this.headerValue; // NOSONAR
		}

		public void setUntrustedType(String untrustedType) {
			this.untrustedType = untrustedType;
		}

		public String getUntrustedType() {
			return this.untrustedType;
		}

		@Override
		public String toString() {
			try {
				return "NonTrustedHeaderType [headerValue=" + new String(this.headerValue, StandardCharsets.UTF_8)
						+ ", untrustedType=" + this.untrustedType + "]";
			}
			catch (@SuppressWarnings("unused") Exception e) {
				return "NonTrustedHeaderType [headerValue=" + Arrays.toString(this.headerValue) + ", untrustedType="
						+ this.untrustedType + "]";
			}
		}

	}

}
