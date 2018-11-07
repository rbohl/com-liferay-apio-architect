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

package com.liferay.apio.architect.internal.annotation;

import static com.liferay.apio.architect.internal.action.Predicates.isAction;
import static com.liferay.apio.architect.internal.action.Predicates.isActionFor;
import static com.liferay.apio.architect.internal.action.Predicates.isCreateAction;
import static com.liferay.apio.architect.internal.action.Predicates.isRemoveAction;
import static com.liferay.apio.architect.internal.action.Predicates.isReplaceAction;
import static com.liferay.apio.architect.internal.action.Predicates.isRetrieveAction;
import static com.liferay.apio.architect.internal.action.Predicates.isRootCollectionAction;
import static com.liferay.apio.architect.internal.action.converter.EntryPointConverter.getEntryPointFrom;
import static com.liferay.apio.architect.internal.body.JSONToBodyConverter.jsonToBody;
import static com.liferay.apio.architect.internal.body.MultipartToBodyConverter.multipartToBody;

import static io.vavr.API.$;
import static io.vavr.API.Case;
import static io.vavr.API.Match;
import static io.vavr.Predicates.instanceOf;
import static io.vavr.control.Either.left;

import static java.util.function.Function.identity;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA_TYPE;

import com.liferay.apio.architect.annotation.Id;
import com.liferay.apio.architect.annotation.ParentId;
import com.liferay.apio.architect.credentials.Credentials;
import com.liferay.apio.architect.documentation.APIDescription;
import com.liferay.apio.architect.documentation.APITitle;
import com.liferay.apio.architect.form.Body;
import com.liferay.apio.architect.internal.action.ActionSemantics;
import com.liferay.apio.architect.internal.action.resource.Resource;
import com.liferay.apio.architect.internal.action.resource.Resource.Item;
import com.liferay.apio.architect.internal.action.resource.Resource.Nested;
import com.liferay.apio.architect.internal.action.resource.Resource.Paged;
import com.liferay.apio.architect.internal.annotation.Action.Error.NotFound;
import com.liferay.apio.architect.internal.documentation.Documentation;
import com.liferay.apio.architect.internal.entrypoint.EntryPoint;
import com.liferay.apio.architect.internal.url.ApplicationURL;
import com.liferay.apio.architect.internal.wiring.osgi.manager.documentation.contributor.CustomDocumentationManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.provider.ProviderManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.representable.RepresentableManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.router.CollectionRouterManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.router.ItemRouterManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.router.NestedCollectionRouterManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.router.ReusableNestedCollectionRouterManager;
import com.liferay.apio.architect.internal.wiring.osgi.manager.uri.mapper.PathIdentifierMapperManager;
import com.liferay.apio.architect.single.model.SingleModel;
import com.liferay.apio.architect.uri.Path;

import io.vavr.CheckedFunction3;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.MediaType;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Provides methods to get the different actions provided by the different
 * routers.
 *
 * @author Javier Gamarra
 * @review
 */
@Component(service = ActionManager.class)
public class ActionManagerImpl implements ActionManager {

	/**
	 * Returns all of the action semantics collected by the different routers.
	 *
	 * @review
	 */
	public Stream<ActionSemantics> actionSemantics() {
		return Stream.of(
			_itemRouterManager.getActionSemantics(),
			_collectionRouterManager.getActionSemantics(),
			_reusableNestedCollectionRouterManager.getActionSemantics(),
			_nestedCollectionRouterManager.getActionSemantics()
		).flatMap(
			identity()
		);
	}

	@Override
	public void add(
		ActionKey actionKey,
		CheckedFunction3<Object, ?, List<Object>, ?> actionFunction,
		Class... providers) {

		_actionsMap.put(actionKey, actionFunction);
	}

	@Override
	public Either<Action.Error, Action> getAction(
		String method, List<String> params) {

		return Match(
			params.size()
		).of(
			Case($(1), __ -> _handleOneParam(method, params.get(0))),
			Case($(2), __ -> _handleTwoParams(method, params)),
			Case($(3), __ -> _handleThreeParams(method, params)),
			Case($(4), __ -> _handleFourParams(method, params)),
			Case($(), __ -> left(_notFound))
		);
	}

	@Override
	public Stream<ActionSemantics> getActionSemantics(
		Resource resource, Credentials credentials) {

		Stream<ActionSemantics> stream = actionSemantics();

		return stream.filter(
			isActionFor(resource)
		).map(
			actionSemantics -> actionSemantics.withResource(resource)
		);
	}

	@Override
	public Documentation getDocumentation(
		HttpServletRequest httpServletRequest) {

		Supplier<Optional<APITitle>> apiTitleSupplier =
			() -> providerManager.provideOptional(
				httpServletRequest, APITitle.class);

		Supplier<Optional<APIDescription>> apiDescriptionSupplier =
			() -> providerManager.provideOptional(
				httpServletRequest, APIDescription.class);

		Supplier<Optional<ApplicationURL>> applicationUrlSupplier =
			() -> providerManager.provideOptional(
				httpServletRequest, ApplicationURL.class);

		Stream<ActionSemantics> stream = actionSemantics();

		Stream<Resource> resourceStream = stream.map(
			ActionSemantics::resource
		).distinct();

		return new Documentation(
			apiTitleSupplier, apiDescriptionSupplier, applicationUrlSupplier,
			() -> _representableManager.getRepresentors(), resourceStream,
			resource -> getActionSemantics(resource, null),
			() -> _customDocumentationManager.getCustomDocumentation());
	}

	@Override
	public EntryPoint getEntryPoint() {
		return getEntryPointFrom(actionSemantics());
	}

	@Override
	public Option<SingleModel> getItemSingleModel(
		Item item, HttpServletRequest request) {

		return Either.narrow(
			_getAction(item, isRetrieveAction)
		).map(
			action -> action.apply(request)
		).map(
			object -> object instanceof Try ? ((Try)object).get() : object
		).toOption(
		).filter(
			instanceOf(SingleModel.class)
		).map(
			SingleModel.class::cast
		);
	}

	@Reference
	protected PathIdentifierMapperManager pathIdentifierMapperManager;

	@Reference
	protected ProviderManager providerManager;

	private Either<Action.Error, Action> _getAction(
		Resource resource, Predicate<ActionSemantics> predicate) {

		return ActionSemantics.filter(
			actionSemantics()
		).forResource(
			resource
		).withPredicate(
			predicate
		).map(
			actionSemantics -> actionSemantics.withResource(resource)
		).map(
			actionSemantics -> actionSemantics.toAction(this::_provide)
		).<Either<Action.Error, Action>>map(
			Either::right
		).orElseGet(
			() -> Either.left(_notFound)
		);
	}

	private Either<Action.Error, Action> _getBinaryFileAction(
		Item item, String binaryId) {

		return Option.ofOptional(
			_representableManager.getRepresentorOptional(item.name())
		).flatMap(
			representor -> Option.ofOptional(
				representor.getBinaryFunction(binaryId))
		).<Action.Error>toEither(
			() -> _notFound
		).map(
			function -> request -> Option.narrow(
				getItemSingleModel(item, request)
			).map(
				SingleModel::getModel
			).map(
				function
			).getOrElseThrow(
				NotFoundException::new
			)
		);
	}

	private Object _getBody(HttpServletRequest request) {
		MediaType mediaType = Try.of(
			() -> MediaType.valueOf(request.getContentType())
		).getOrElseThrow(
			t -> new BadRequestException("Invalid Content-Type header", t)
		);

		if (mediaType.isCompatible(APPLICATION_JSON_TYPE)) {
			return jsonToBody(request);
		}

		if (mediaType.isCompatible(MULTIPART_FORM_DATA_TYPE)) {
			return multipartToBody(request);
		}

		throw new NotSupportedException();
	}

	@SuppressWarnings("Convert2MethodRef")
	private Object _getId(String name, String id) {
		return Try.success(
			new Path(name, id)
		).mapTry(
			pathIdentifierMapperManager::mapToIdentifierOrFail
		).getOrElseThrow(
			t -> new NotFoundException(t)
		);
	}

	private Either<Action.Error, Action> _handleFourParams(
		String method, List<String> params) {

		Object id = _getId(params.get(0), params.get(1));

		Nested nested = Nested.of(params.get(0), id, params.get(2));

		return _getAction(nested, isAction(params.get(3), method));
	}

	private Either<Action.Error, Action> _handleOneParam(
		String method, String param1) {

		Paged paged = Paged.of(param1);

		return Match(
			method
		).of(
			Case($("GET"), __ -> _getAction(paged, isRootCollectionAction)),
			Case($("POST"), __ -> _getAction(paged, isCreateAction)),
			Case($(), __ -> left(_notFound))
		);
	}

	private Either<Action.Error, Action> _handleThreeParams(
		String method, List<String> params) {

		Object id = _getId(params.get(0), params.get(1));

		Item item = Item.of(params.get(0), id);

		return Either.narrow(
			_getBinaryFileAction(item, params.get(2))
		).orElse(
			() -> _getAction(item, isAction(params.get(2), method))
		).orElse(
			() -> {
				Object id1 = _getId(params.get(0), params.get(1));

				Nested nested = Nested.of(params.get(0), id1, params.get(2));

				return Match(
					method
				).of(
					Case($("GET"), __ -> _getAction(nested, isRetrieveAction)),
					Case($("POST"), __ -> _getAction(nested, isCreateAction)),
					Case($(), __ -> left(_notFound))
				);
			}
		);
	}

	private Either<Action.Error, Action> _handleTwoParams(
		String method, List<String> params) {

		Paged paged = Paged.of(params.get(0));
		String actionName = params.get(1);

		return Either.narrow(
			_getAction(paged, isAction(actionName, method))
		).orElse(
			() -> {
				Item item = Item.of(
					params.get(0), _getId(params.get(0), params.get(1)));

				return Match(
					method
				).of(
					Case($("DELETE"), __ -> _getAction(item, isRemoveAction)),
					Case($("PUT"), __ -> _getAction(item, isReplaceAction)),
					Case($("GET"), __ -> _getAction(item, isRetrieveAction)),
					Case($(), __ -> left(_notFound))
				);
			}
		);
	}

	private Object _provide(
		ActionSemantics actionSemantics, HttpServletRequest request,
		Class<?> clazz) {

		if (clazz.equals(Void.class)) {
			return null;
		}

		if (clazz.equals(Body.class)) {
			return _getBody(request);
		}

		if (clazz.equals(Id.class)) {
			return Option.of(
				actionSemantics.resource()
			).filter(
				instanceOf(Item.class)
			).map(
				Item.class::cast
			).flatMap(
				item -> Option.ofOptional(item.id())
			).getOrElseThrow(
				NotFoundException::new
			);
		}

		if (clazz.equals(ParentId.class)) {
			return Option.of(
				actionSemantics.resource()
			).filter(
				instanceOf(Nested.class)
			).map(
				Nested.class::cast
			).flatMap(
				nested -> Option.ofOptional(nested.id())
			).getOrElseThrow(
				NotFoundException::new
			);
		}

		return providerManager.provideMandatory(request, clazz);
	}

	private static final NotFound _notFound = new NotFound() {
	};

	private final Map<ActionKey, CheckedFunction3<Object, ?, List<Object>, ?>>
		_actionsMap = new HashMap<>();

	@Reference
	private CollectionRouterManager _collectionRouterManager;

	@Reference
	private CustomDocumentationManager _customDocumentationManager;

	@Reference
	private ItemRouterManager _itemRouterManager;

	@Reference
	private NestedCollectionRouterManager _nestedCollectionRouterManager;

	@Reference
	private RepresentableManager _representableManager;

	@Reference
	private ReusableNestedCollectionRouterManager
		_reusableNestedCollectionRouterManager;

}