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

package com.liferay.apio.architect.internal.wiring.osgi.manager.provider;

import static com.liferay.apio.architect.internal.unsafe.Unsafe.unsafeCast;

import static org.slf4j.LoggerFactory.getLogger;

import com.liferay.apio.architect.credentials.Credentials;
import com.liferay.apio.architect.internal.wiring.osgi.manager.base.ClassNameBaseManager;
import com.liferay.apio.architect.provider.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import javax.ws.rs.NotFoundException;

import org.osgi.service.component.annotations.Component;

import org.slf4j.Logger;

/**
 * Manages services that have a {@link Provider}.
 *
 * @author Alejandro Hernández
 * @author Carlos Sierra Andrés
 * @author Jorge Ferrer
 */
@Component(service = ProviderManager.class)
public class ProviderManager extends ClassNameBaseManager<Provider> {

	public ProviderManager() {
		super(Provider.class, 0);
	}

	/**
	 * Returns the list of missing providers' class names.
	 *
	 * @param  neededProviders the list of needed providers
	 * @return the list of missing providers
	 */
	public List<String> getMissingProviders(
		Collection<String> neededProviders) {

		Set<String> providedClassNames = serviceTrackerMap.keySet();

		List<String> list = new ArrayList<>(neededProviders);

		list.removeAll(providedClassNames);

		return list;
	}

	/**
	 * Returns an instance of type {@code T} if a valid {@code Provider} is
	 * found; throws a {@code NotFoundException} otherwise.
	 *
	 * @param  httpServletRequest the current request
	 * @param  clazz the class type {@code T}
	 * @return the {@code T} instance, if a valid {@code Provider} is found;
	 *         throws {@code NotFoundException} otherwise
	 */
	public <T> T provideMandatory(
		HttpServletRequest httpServletRequest, Class<T> clazz) {

		Optional<Provider> providerOptional = getServiceOptional(clazz);

		if (!providerOptional.isPresent()) {
			_logger.warn("Missing provider for mandatory class: {}", clazz);

			throw new NotFoundException();
		}

		Optional<T> optional = provideOptional(httpServletRequest, clazz);

		if (clazz.equals(Credentials.class) && !optional.isPresent()) {
			return unsafeCast((Credentials)() -> "");
		}

		return optional.orElseThrow(
			() -> {
				_logger.warn(
					"Mandatory provider for class {} returned null", clazz);

				return new NotFoundException();
			});
	}

	/**
	 * Returns the instance of type {@code T} if a valid {@code Provider} can be
	 * found. Returns {@code Optional#empty()} otherwise.
	 *
	 * @param  httpServletRequest the current request
	 * @param  clazz the class type {@code T}
	 * @return the instance of {@code T}, if a valid {@code Provider} is
	 *         present; {@code Optional#empty()} otherwise
	 */
	public <T> Optional<T> provideOptional(
		HttpServletRequest httpServletRequest, Class<T> clazz) {

		Optional<Provider<T>> optional = unsafeCast(getServiceOptional(clazz));

		return optional.map(
			provider -> provider.createContext(httpServletRequest));
	}

	private Logger _logger = getLogger(getClass());

}