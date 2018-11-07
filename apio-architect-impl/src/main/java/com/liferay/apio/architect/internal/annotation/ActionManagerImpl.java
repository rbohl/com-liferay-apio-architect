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
import static com.liferay.apio.architect.internal.action.Predicates.isCreateAction;
import static com.liferay.apio.architect.internal.action.Predicates.isRemoveAction;
import static com.liferay.apio.architect.internal.action.Predicates.isReplaceAction;
import static com.liferay.apio.architect.internal.action.Predicates.isRetrieveAction;
import static com.liferay.apio.architect.internal.action.Predicates.isRootCollectionAction;
import static com.liferay.apio.architect.internal.action.converter.EntryPointConverter.getEntryPointFrom;
import static com.liferay.apio.architect.internal.body.JSONToBodyConverter.jsonToBody;
import static com.liferay.apio.architect.internal.body.MultipartToBodyConverter.multipartToBody;

import static io.vavr.Predicates.instanceOf;
import static io.vavr.control.Either.right;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA_TYPE;

import com.liferay.apio.architect.annotation.Id;
import com.liferay.apio.architect.credentials.Credentials;
import com.liferay.apio.architect.documentation.APIDescription;
import com.liferay.apio.architect.documentation.APITitle;
import com.liferay.apio.architect.form.Body;
import com.liferay.apio.architect.internal.action.ActionSemantics;
import com.liferay.apio.architect.internal.action.resource.Resource;
import com.liferay.apio.architect.internal.action.resource.Resource.Item;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

	public static ActionManager newTestInstance(
		PathIdentifierMapperManager pathIdentifierMapperManager,
		ProviderManager providerManager) {

		ActionManagerImpl actionManagerImpl = new ActionManagerImpl();

		actionManagerImpl.pathIdentifierMapperManager =
			pathIdentifierMapperManager;
		actionManagerImpl.providerManager = providerManager;

		return actionManagerImpl;
	}

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

		_providers.put(actionKey, providers);
	}

	@Override
	public Either<Action.Error, Action> getAction(
		String method, List<String> params) {

		if (params.size() == 1) {
			Paged paged = Paged.of(params.get(0));

			if (method.equals("GET")) {
				return _getAction(paged, isRootCollectionAction);
			}

			if (method.equals("POST")) {
				return _getAction(paged, isCreateAction);
			}
		}

		if (params.size() == 2) {
			Paged paged = Paged.of(params.get(0));
			String actionName = params.get(1);

			Either<Action.Error, Action> pagedCustomActionEither = _getAction(
				paged, isAction(actionName, method));

			if (pagedCustomActionEither.isRight()) {
				return pagedCustomActionEither;
			}

			Item item = Item.of(
				params.get(0), _getId(params.get(0), params.get(1)));

			if (method.equals("DELETE")) {
				return _getAction(item, isRemoveAction);
			}

			if (method.equals("PUT")) {
				return _getAction(item, isReplaceAction);
			}

			if (method.equals("GET")) {
				return _getAction(item, isRetrieveAction);
			}
		}

		if (params.size() == 3) {
			Item item = Item.of(
				params.get(0), _getId(params.get(0), params.get(1)));

			return Either.narrow(
				_getBinaryFileAction(item, params.get(2))
			).orElse(
				() -> _getAction(item, isAction(params.get(2), method))
			);
		}

		if (params.size() == 4) {
			ActionKey actionKey = new ActionKey(
				method, params.get(0), params.get(1), params.get(2),
				params.get(3));

			return _getActionsWithId(actionKey);
		}

		return Either.left(_notFound);
	}

	@Override
	public List<Action> getActions(
		ActionKey actionKey, Credentials credentials) {

		Set<ActionKey> actionKeys = _actionsMap.keySet();

		Stream<ActionKey> stream = actionKeys.stream();

		return stream.filter(
			childActionKey ->
				_sameResource(actionKey, childActionKey) &&
				(_isCustomActionOfCollection(actionKey, childActionKey) ||
				 _isCustomActionOfItem(actionKey, childActionKey) ||
				 _isCustomOfActionNested(actionKey, childActionKey))
		).filter(
			this::_isValidAction
		).map(
			childActionKey -> {
				Object id = _getId(actionKey.getPath());

				return _getAction(childActionKey, id);
			}
		).collect(
			toList()
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

		return new Documentation(
			apiTitleSupplier, apiDescriptionSupplier, applicationUrlSupplier,
			() -> _representableManager.getRepresentors(), _actionsMap::keySet,
			actionKey -> getActions(actionKey, null),
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

	private Action _getAction(ActionKey actionKey, Object id) {
		return new Action() {

			@Override
			public Object apply(HttpServletRequest httpServletRequest) {
				return Try.of(
					() -> _getActionFunction(actionKey)
				).mapTry(
					action -> action.apply(
						id, null,
						(List<Object>)_getProviders(
							httpServletRequest, actionKey))
				).getOrElseThrow(
					() -> new NotFoundException("Not Found")
				);
			}

			@Override
			public ActionKey getActionKey() {
				return actionKey;
			}

			@Override
			public Optional<String> getURIOptional() {
				Optional<Path> optionalPath =
					pathIdentifierMapperManager.mapToPath(
						actionKey.getResource(), actionKey.getIdOrAction());

				return optionalPath.map(
					path -> path.asURI() + "/" + actionKey.getNestedResource());
			}

		};
	}

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

	private CheckedFunction3<Object, ?, List<Object>, ?> _getActionFunction(
		ActionKey actionKey) {

		if (_actionsMap.containsKey(actionKey)) {
			return _actionsMap.get(actionKey);
		}

		return _actionsMap.get(actionKey.getGenericActionKey());
	}

	private Either<Action.Error, Action> _getActionsWithId(
		ActionKey actionKey) {

		Object id = _getId(actionKey.getPath());

		return right(_getAction(actionKey, id));
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

	private Object _getId(Path path) {
		try {
			return pathIdentifierMapperManager.mapToIdentifierOrFail(path);
		}
		catch (Error e) {
			return null;
		}
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

	private List<Object> _getProvidedObjects(
		Class<Object>[] value, HttpServletRequest httpServletRequest) {

		return Stream.of(
			value
		).map(
			provider -> providerManager.provideMandatory(
				httpServletRequest, provider)
		).collect(
			toList()
		);
	}

	private List<Object> _getProviders(
		HttpServletRequest httpServletRequest, ActionKey actionKey) {

		return Try.of(
			() -> _getProvidersByParam(actionKey)
		).map(
			value -> _getProvidedObjects(value, httpServletRequest)
		).getOrElse(
			Collections::emptyList
		);
	}

	private Class<Object>[] _getProvidersByParam(ActionKey actionKey) {
		if (_providers.containsKey(actionKey)) {
			return _providers.get(actionKey);
		}

		return _providers.get(actionKey.getGenericActionKey());
	}

	private boolean _isCustomActionOfCollection(
		ActionKey actionKey, ActionKey childActionKey) {

		if (actionKey.isCollection() &&
			(childActionKey.isCollection() || childActionKey.isCustom())) {

			return true;
		}

		return false;
	}

	private boolean _isCustomActionOfItem(
		ActionKey actionKey, ActionKey childActionKey) {

		if (!actionKey.isNested() && actionKey.isItem() &&
			childActionKey.isItem() &&
			(_getActionFunction(
				new ActionKey("GET", childActionKey.getNestedResource())) ==
					null)) {

			return true;
		}

		return false;
	}

	private boolean _isCustomOfActionNested(
		ActionKey actionKey, ActionKey childActionKey) {

		String nestedResource = actionKey.getNestedResource();

		if (actionKey.isNested() &&
			nestedResource.equals(childActionKey.getNestedResource())) {

			return true;
		}

		return false;
	}

	private boolean _isValidAction(ActionKey actionKey) {
		if (actionKey.isCollection()) {
			return _actionsMap.containsKey(actionKey);
		}

		if (_getActionFunction(actionKey) != null) {
			return true;
		}

		return false;
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

		return providerManager.provideMandatory(request, clazz);
	}

	private boolean _sameResource(
		ActionKey actionKey, ActionKey childActionKey) {

		String resource = childActionKey.getResource();

		return resource.equals(actionKey.getResource());
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

	private final Map<ActionKey, Class[]> _providers = new HashMap<>();

	@Reference
	private RepresentableManager _representableManager;

	@Reference
	private ReusableNestedCollectionRouterManager
		_reusableNestedCollectionRouterManager;

}