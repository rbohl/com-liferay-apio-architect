/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.apio.architect.internal.writer;

import com.liferay.apio.architect.alias.representor.FieldFunction;
import com.liferay.apio.architect.alias.representor.NestedFieldFunction;
import com.liferay.apio.architect.consumer.TriConsumer;
import com.liferay.apio.architect.documentation.contributor.CustomDocumentation;
import com.liferay.apio.architect.function.throwable.ThrowableTriFunction;
import com.liferay.apio.architect.internal.annotation.ActionKey;
import com.liferay.apio.architect.internal.annotation.ActionManager;
import com.liferay.apio.architect.internal.annotation.ActionManagerImpl;
import com.liferay.apio.architect.internal.documentation.Documentation;
import com.liferay.apio.architect.internal.message.json.DocumentationMessageMapper;
import com.liferay.apio.architect.internal.message.json.JSONObjectBuilder;
import com.liferay.apio.architect.internal.request.RequestInfo;
import com.liferay.apio.architect.language.AcceptLanguage;
import com.liferay.apio.architect.operation.Operation;
import com.liferay.apio.architect.related.RelatedCollection;
import com.liferay.apio.architect.related.RelatedModel;
import com.liferay.apio.architect.representor.BaseRepresentor;
import com.liferay.apio.architect.representor.Representor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Writes the documentation.
 *
 * @author Alejandro Hernández
 */
public class DocumentationWriter {

	/**
	 * Creates a new {@code DocumentationWriter} object, without creating the
	 * builder.
	 *
	 * @param  function the function that transforms a builder into the {@code
	 *         DocumentationWriter}
	 * @return the {@code DocumentationWriter} instance
	 */
	public static DocumentationWriter create(
		Function<Builder, DocumentationWriter> function) {

		return function.apply(new Builder());
	}

	public DocumentationWriter(Builder builder) {
		_documentation = builder._documentation;
		_documentationMessageMapper = builder._documentationMessageMapper;
		_requestInfo = builder._requestInfo;

		_customDocumentation = _documentation.getCustomDocumentation();
	}

	/**
	 * Writes the {@link Documentation} to a string.
	 *
	 * @return the JSON representation of the {@code Documentation}
	 */
	public String write() {
		JSONObjectBuilder jsonObjectBuilder = new JSONObjectBuilder();

		_writeDocumentationMetadata(jsonObjectBuilder);

		Map<String, Representor> representors =
			_documentation.getRepresentors();

		Supplier<ActionManager> actionManagerSupplier =
			_documentation.getActionManagerSupplier();

		ActionManagerImpl actionManager =
			(ActionManagerImpl)actionManagerSupplier.get();

		Map<ActionKey, ThrowableTriFunction<Object, ?, List<Object>, ?>>
			actions = actionManager.getActions();

		Set<ActionKey> actionKeys = actions.keySet();

		Stream<ActionKey> stream = actionKeys.stream();

		stream.filter(
			actionKey -> representors.containsKey(actionKey.getResource())
		).filter(
			actionKey -> actionKey.isGetRequest() && !actionKey.isNested() &&
			 !actionKey.isCustom()
		).forEach(
			actionKey -> {
				String name = actionKey.getResource();

				Representor representor = representors.get(name);

				_writeRoute(
					jsonObjectBuilder, name, representor,
					_getResourceMapperTriConsumer(actionKey),
					(resource, type, resourceJsonObjectBuilder) ->
						_writeOperations(
							actionManager, resource, type, actionKey,
							resourceJsonObjectBuilder),
					_getWriteFieldsRepresentorConsumer(actionKey, representor));
			}
		);

		_documentationMessageMapper.onFinish(jsonObjectBuilder, _documentation);

		return jsonObjectBuilder.build();
	}

	/**
	 * Creates {@code DocumentationWriter} instances.
	 */
	public static class Builder {

		/**
		 * Add information about the documentation being written to the builder.
		 *
		 * @param  documentation the documentation being written
		 * @return the updated builder
		 */
		public DocumentationMessageMapperStep documentation(
			Documentation documentation) {

			_documentation = documentation;

			return new DocumentationMessageMapperStep();
		}

		public class BuildStep {

			/**
			 * Constructs and returns a {@code DocumentationWriter} instance
			 * with the information provided to the builder.
			 *
			 * @return the {@code DocumentationWriter} instance
			 */
			public DocumentationWriter build() {
				return new DocumentationWriter(Builder.this);
			}

		}

		public class DocumentationMessageMapperStep {

			/**
			 * Adds information to the builder about the {@link
			 * DocumentationMessageMapper}.
			 *
			 * @param  documentationMessageMapper the {@code
			 *         DocumentationMessageMapper}
			 * @return the updated builder
			 */
			public RequestInfoStep documentationMessageMapper(
				DocumentationMessageMapper documentationMessageMapper) {

				_documentationMessageMapper = documentationMessageMapper;

				return new RequestInfoStep();
			}

		}

		public class RequestInfoStep {

			/**
			 * Adds information to the builder about the request.
			 *
			 * @param  requestInfo the information obtained from the request. It
			 *         can be created by using a {@link RequestInfo.Builder}.
			 * @return the updated builder
			 */
			public BuildStep requestInfo(RequestInfo requestInfo) {
				_requestInfo = requestInfo;

				return new BuildStep();
			}

		}

		private Documentation _documentation;
		private DocumentationMessageMapper _documentationMessageMapper;
		private RequestInfo _requestInfo;

	}

	private Stream<String> _calculateNestableFieldNames(
		BaseRepresentor representor) {

		List<FieldFunction> fieldFunctions = new ArrayList<>();

		fieldFunctions.addAll(representor.getApplicationRelativeURLFunctions());
		fieldFunctions.addAll(representor.getBinaryFunctions());
		fieldFunctions.addAll(representor.getBooleanFunctions());
		fieldFunctions.addAll(representor.getBooleanListFunctions());
		fieldFunctions.addAll(representor.getLinkFunctions());
		fieldFunctions.addAll(representor.getLocalizedStringFunctions());
		fieldFunctions.addAll(representor.getNestedFieldFunctions());
		fieldFunctions.addAll(representor.getNumberFunctions());
		fieldFunctions.addAll(representor.getNumberListFunctions());
		fieldFunctions.addAll(representor.getRelativeURLFunctions());
		fieldFunctions.addAll(representor.getStringFunctions());
		fieldFunctions.addAll(representor.getStringListFunctions());

		Stream<FieldFunction> fieldFunctionStream = fieldFunctions.stream();

		Stream<String> fieldNamesStream = fieldFunctionStream.map(
			FieldFunction::getKey);

		List<RelatedModel> relatedModels = representor.getRelatedModels();

		Stream<RelatedModel> relatedModelStream = relatedModels.stream();

		Stream<String> relatedModelNamesStream = relatedModelStream.map(
			RelatedModel::getKey);

		fieldNamesStream = Stream.concat(
			fieldNamesStream, relatedModelNamesStream);

		List<NestedFieldFunction> nestedFieldFunctions =
			representor.getNestedFieldFunctions();

		Stream<NestedFieldFunction> nestedFieldFunctionStream =
			nestedFieldFunctions.stream();

		Stream<String> nestedFieldNamesStream = nestedFieldFunctionStream.map(
			nestedFieldFunction -> _calculateNestableFieldNames(
				nestedFieldFunction.getNestedRepresentor())
		).reduce(
			Stream::concat
		).orElseGet(
			Stream::empty
		);

		return Stream.concat(fieldNamesStream, nestedFieldNamesStream);
	}

	private String _getCustomDocumentation(String name) {
		return Optional.ofNullable(
			_customDocumentation.getDescriptionFunction(name)
		).map(
			localeFunction -> {
				AcceptLanguage acceptLanguage =
					_requestInfo.getAcceptLanguage();

				return localeFunction.apply(
					acceptLanguage.getPreferredLocale());
			}
		).orElse(
			null
		);
	}

	private TriConsumer<JSONObjectBuilder, String, String>
		_getResourceMapperTriConsumer(ActionKey actionKey) {

		if (actionKey.isCollection()) {
			return _documentationMessageMapper::mapResourceCollection;
		}

		return _documentationMessageMapper::mapResource;
	}

	private Consumer<JSONObjectBuilder> _getWriteFieldsRepresentorConsumer(
		ActionKey actionKey, Representor representor) {

		if (actionKey.isCollection()) {
			return __ -> {
			};
		}

		return resourceJsonObjectBuilder -> _writeAllFields(
			representor, resourceJsonObjectBuilder);
	}

	private void _writeAllFields(
		Representor representor, JSONObjectBuilder resourceJsonObjectBuilder) {

		Stream<String> fieldNamesStream = _calculateNestableFieldNames(
			representor);

		Stream<RelatedCollection> relatedCollections =
			representor.getRelatedCollections();

		Stream<String> relatedCollectionsNamesStream = relatedCollections.map(
			RelatedCollection::getKey);

		fieldNamesStream = Stream.concat(
			fieldNamesStream, relatedCollectionsNamesStream);

		_writeFields(fieldNamesStream, resourceJsonObjectBuilder);
	}

	private void _writeDocumentationMetadata(
		JSONObjectBuilder jsonObjectBuilder) {

		Optional<String> apiTitleOptional =
			_documentation.getAPITitleOptional();

		apiTitleOptional.ifPresent(
			title -> _documentationMessageMapper.mapTitle(
				jsonObjectBuilder, title));

		Optional<String> apiDescriptionOptional =
			_documentation.getAPIDescriptionOptional();

		apiDescriptionOptional.ifPresent(
			description -> _documentationMessageMapper.mapDescription(
				jsonObjectBuilder, description));

		Optional<String> entryPointOptional =
			_documentation.getEntryPointOptional();

		entryPointOptional.ifPresent(
			entryPoint -> _documentationMessageMapper.mapEntryPoint(
				jsonObjectBuilder, entryPoint)
		);
	}

	private void _writeFields(
		Stream<String> fields, JSONObjectBuilder resourceJsonObjectBuilder) {

		Stream<String> stream = fields.distinct();

		stream.forEach(
			field -> _writeFormField(resourceJsonObjectBuilder, field));
	}

	private void _writeFormField(
		JSONObjectBuilder resourceJsonObjectBuilder, String fieldName) {

		JSONObjectBuilder jsonObjectBuilder = new JSONObjectBuilder();

		String customDocumentation = _getCustomDocumentation(fieldName);

		_documentationMessageMapper.mapProperty(
			jsonObjectBuilder, fieldName, customDocumentation);

		_documentationMessageMapper.onFinishProperty(
			resourceJsonObjectBuilder, jsonObjectBuilder, fieldName);
	}

	private void _writeOperation(
		Operation operation, JSONObjectBuilder jsonObjectBuilder,
		String resourceName, String type) {

		JSONObjectBuilder operationJsonObjectBuilder = new JSONObjectBuilder();

		String customDocumentation = _getCustomDocumentation(
			operation.getName());

		_documentationMessageMapper.mapOperation(
			operationJsonObjectBuilder, resourceName, type, operation,
			customDocumentation);

		_documentationMessageMapper.onFinishOperation(
			jsonObjectBuilder, operationJsonObjectBuilder, operation);
	}

	private void _writeOperations(
		ActionManager actionManager, String resource, String type,
		ActionKey actionKey, JSONObjectBuilder resourceJsonObjectBuilder) {

		List<Operation> actions = actionManager.getActions(actionKey, null);

		actions.forEach(
			operation -> _writeOperation(
				operation, resourceJsonObjectBuilder, resource, type));
	}

	private void _writeRoute(
		JSONObjectBuilder jsonObjectBuilder, String name,
		Representor representor,
		TriConsumer<JSONObjectBuilder, String, String> writeResourceTriConsumer,
		TriConsumer<String, String, JSONObjectBuilder>
			writeOperationsTriConsumer,
		Consumer<JSONObjectBuilder> writeFieldsRepresentor) {

		List<String> types = representor.getTypes();

		types.forEach(
			type -> {
				JSONObjectBuilder resourceJsonObjectBuilder =
					new JSONObjectBuilder();

				String customDocumentation = _getCustomDocumentation(type);

				writeResourceTriConsumer.accept(
					resourceJsonObjectBuilder, type, customDocumentation);

				writeOperationsTriConsumer.accept(
					name, type, resourceJsonObjectBuilder);

				writeFieldsRepresentor.accept(resourceJsonObjectBuilder);

				_documentationMessageMapper.onFinishResource(
					jsonObjectBuilder, resourceJsonObjectBuilder, type);
			});
	}

	private final CustomDocumentation _customDocumentation;
	private final Documentation _documentation;
	private final DocumentationMessageMapper _documentationMessageMapper;
	private final RequestInfo _requestInfo;

}