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

package com.liferay.apio.architect.internal.message.json.ld;

import static com.liferay.apio.architect.internal.message.json.ld.JSONLDMessageMapperUtil.getOperationTypes;
import static com.liferay.apio.architect.operation.HTTPMethod.DELETE;
import static com.liferay.apio.architect.operation.HTTPMethod.GET;

import com.liferay.apio.architect.internal.annotation.Action;
import com.liferay.apio.architect.internal.annotation.ActionKey;
import com.liferay.apio.architect.internal.documentation.Documentation;
import com.liferay.apio.architect.internal.message.json.DocumentationMessageMapper;
import com.liferay.apio.architect.internal.message.json.JSONObjectBuilder;

import java.util.stream.Stream;

import org.osgi.service.component.annotations.Component;

/**
 * Represents documentation in JSON-LD + Hydra format.
 *
 * <p>
 * For more information, see <a href="https://json-ld.org/">JSON-LD </a> and <a
 * href="https://www.hydra-cg.com/">Hydra </a> .
 * </p>
 *
 * @author Alejandro Hernández
 * @author Javier Gamarra
 * @author Zoltán Takács
 */
@Component(service = DocumentationMessageMapper.class)
public class JSONLDDocumentationMessageMapper
	implements DocumentationMessageMapper {

	@Override
	public String getMediaType() {
		return "application/ld+json";
	}

	@Override
	public void mapAction(
		JSONObjectBuilder jsonObjectBuilder, String resourceName, String type,
		Action action, String description) {

		ActionKey actionKey = action.getActionKey();

		jsonObjectBuilder.field(
			"@id"
		).stringValue(
			"_:" + actionKey.getIdName()
		);

		jsonObjectBuilder.field(
			"@type"
		).arrayValue(
		).addAllStrings(
			getOperationTypes(action)
		);

		jsonObjectBuilder.field(
			"method"
		).stringValue(
			actionKey.getHttpMethodName()
		);

		jsonObjectBuilder.field(
			"returns"
		).stringValue(
			_getReturnValue(type, action)
		);

		_addDescription(jsonObjectBuilder, description);
	}

	@Override
	public void mapDescription(
		JSONObjectBuilder jsonObjectBuilder, String description) {

		jsonObjectBuilder.field(
			"description"
		).stringValue(
			description
		);
	}

	@Override
	public void mapEntryPoint(
		JSONObjectBuilder jsonObjectBuilder, String entryPoint) {

		jsonObjectBuilder.field(
			"entrypoint"
		).stringValue(
			entryPoint
		);
	}

	@Override
	public void mapProperty(
		JSONObjectBuilder jsonObjectBuilder, String fieldName,
		String description) {

		jsonObjectBuilder.field(
			"@type"
		).stringValue(
			"SupportedProperty"
		);

		jsonObjectBuilder.field(
			"property"
		).stringValue(
			fieldName
		);

		_addDescription(jsonObjectBuilder, description);
	}

	@Override
	public void mapResource(
		JSONObjectBuilder jsonObjectBuilder, String resourceType,
		String description) {

		jsonObjectBuilder.field(
			"@id"
		).stringValue(
			resourceType
		);

		jsonObjectBuilder.field(
			"@type"
		).stringValue(
			"Class"
		);

		jsonObjectBuilder.field(
			"title"
		).stringValue(
			resourceType
		);

		_addDescription(jsonObjectBuilder, description);
	}

	@Override
	public void mapResourceCollection(
		JSONObjectBuilder jsonObjectBuilder, String resourceType,
		String description) {

		jsonObjectBuilder.field(
			"@id"
		).stringValue(
			"vocab:" + resourceType + "Collection"
		);

		jsonObjectBuilder.field(
			"@type"
		).stringValue(
			"Class"
		);

		jsonObjectBuilder.field(
			"subClassOf"
		).stringValue(
			"Collection"
		);

		jsonObjectBuilder.field(
			"description"
		).stringValue(
			"A collection of " + resourceType
		);

		jsonObjectBuilder.field(
			"title"
		).stringValue(
			resourceType + "Collection"
		);

		Stream.of(
			"totalItems", "member", "numberOfItems"
		).forEach(
			fieldName -> {
				JSONObjectBuilder propertyJsonObjectBuilder =
					new JSONObjectBuilder();

				mapProperty(propertyJsonObjectBuilder, fieldName, description);

				onFinishProperty(
					jsonObjectBuilder, propertyJsonObjectBuilder, fieldName);
			}
		);

		_addDescription(jsonObjectBuilder, description);
	}

	@Override
	public void mapTitle(JSONObjectBuilder jsonObjectBuilder, String title) {
		jsonObjectBuilder.field(
			"title"
		).stringValue(
			title
		);
	}

	@Override
	public void onFinish(
		JSONObjectBuilder jsonObjectBuilder, Documentation documentation) {

		jsonObjectBuilder.field(
			"@context"
		).arrayValue(
			arrayBuilder -> arrayBuilder.add(
				builder -> builder.field(
					"@vocab"
				).stringValue(
					"http://schema.org/"
				)
			),
			arrayBuilder -> arrayBuilder.addString(
				"https://www.w3.org/ns/hydra/core#"),
			arrayBuilder -> arrayBuilder.add(
				builder -> builder.field(
					"expects"
				).fields(
					nestedBuilder -> nestedBuilder.field(
						"@type"
					).stringValue(
						"@id"
					),
					nestedBuilder -> nestedBuilder.field(
						"@id"
					).stringValue(
						"hydra:expects"
					)
				),
				builder -> builder.field(
					"returns"
				).fields(
					nestedBuilder -> nestedBuilder.field(
						"@id"
					).stringValue(
						"hydra:returns"
					),
					nestedBuilder -> nestedBuilder.field(
						"@type"
					).stringValue(
						"@id"
					)
				)
			)
		);

		jsonObjectBuilder.field(
			"@id"
		).stringValue(
			"/doc"
		);

		jsonObjectBuilder.field(
			"@type"
		).stringValue(
			"ApiDocumentation"
		);
	}

	@Override
	public void onFinishAction(
		JSONObjectBuilder documentationJsonObjectBuilder,
		JSONObjectBuilder operationJsonObjectBuilder, Action action) {

		documentationJsonObjectBuilder.field(
			"supportedOperation"
		).arrayValue(
		).add(
			operationJsonObjectBuilder
		);
	}

	@Override
	public void onFinishProperty(
		JSONObjectBuilder documentationJsonObjectBuilder,
		JSONObjectBuilder propertyJsonObjectBuilder, String formField) {

		documentationJsonObjectBuilder.field(
			"supportedProperty"
		).arrayValue(
		).add(
			propertyJsonObjectBuilder
		);
	}

	@Override
	public void onFinishResource(
		JSONObjectBuilder documentationJsonObjectBuilder,
		JSONObjectBuilder resourceJsonObjectBuilder, String type) {

		documentationJsonObjectBuilder.field(
			"supportedClass"
		).arrayValue(
		).add(
			resourceJsonObjectBuilder
		);
	}

	private void _addDescription(
		JSONObjectBuilder documentationJsonObjectBuilder, String description) {

		if (description != null) {
			documentationJsonObjectBuilder.field(
				"comment"
			).stringValue(
				description
			);
		}
	}

	private String _getReturnValue(String type, Action action) {
		String value = null;

		ActionKey actionKey = action.getActionKey();

		String httpMethodName = actionKey.getHttpMethodName();

		if (httpMethodName.equals(DELETE.name())) {
			value = "http://www.w3.org/2002/07/owl#Nothing";
		}
		else if (actionKey.isCollection() &&
				 httpMethodName.equals(GET.name())) {

			value = "Collection";
		}
		else {
			value = type;
		}

		return value;
	}

}