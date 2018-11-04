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

package com.liferay.apio.architect.internal.action.resource;

import static org.immutables.value.Value.Style.ImplementationVisibility.PACKAGE;

import java.util.Optional;

import org.immutables.value.Value.Auxiliary;
import org.immutables.value.Value.Immutable;
import org.immutables.value.Value.Parameter;
import org.immutables.value.Value.Style;

/**
 * Instances of this class represent an API resource name.
 *
 * <p>
 * Only three implementations are allowed: {@link Item}, {@link Paged} and
 * {@link Nested}.
 * </p>
 *
 * <p>
 * This class should never be instantiated.
 * </p>
 *
 * @author Alejandro Hernández
 * @see    Item
 * @see    Paged
 * @see    Nested
 * @review
 */
@Style(allParameters = true, visibility = PACKAGE)
public abstract class Resource {

	/**
	 * Instances of this class represent an item resource.
	 *
	 * <p>
	 * This class should never be instantiated. Always use {@link #of(String)}
	 * method to create a new instance.
	 * </p>
	 *
	 * @review
	 */
	@Immutable
	public abstract static class Item extends Resource {

		/**
		 * Creates a new {@link Item} with the provided {@code name}.
		 *
		 * @review
		 */
		public static Item of(String name) {
			return ImmutableItem.of(name, Optional.empty());
		}

		/**
		 * Creates a new {@link Item} with the provided {@code name} and {@code
		 * ID}.
		 *
		 * @review
		 */
		public static Item of(String name, Object id) {
			return ImmutableItem.of(name, Optional.of(id));
		}

		/**
		 * The resource's ID. This component is not taken into account when
		 * performing an {@link #equals(Object)} check.
		 *
		 * @review
		 */
		@Auxiliary
		@Parameter(order = 1)
		public abstract Optional<Object> id();

		/**
		 * The resource's name
		 *
		 * @review
		 */
		@Parameter(order = 0)
		public abstract String name();

	}

	/**
	 * Instances of this class represent a nested resource.
	 *
	 * <p>
	 * This class should never be instantiated. Always use {@link #of(String,
	 * String)} method to create a new instance.
	 * </p>
	 *
	 * @review
	 */
	@Immutable
	public abstract static class Nested extends Resource {

		/**
		 * Creates a new {@link Nested} with the provided {@code parent}-{@code
		 * nested} name pair.
		 *
		 * @review
		 */
		public static Nested of(String parentName, String name) {
			return ImmutableNested.of(parentName, name);
		}

		/**
		 * The resource's name
		 *
		 * @review
		 */
		@Parameter(order = 1)
		public abstract String name();

		/**
		 * The resource's parent name
		 *
		 * @review
		 */
		@Parameter(order = 0)
		public abstract String parentName();

	}

	/**
	 * Instances of this class represent a paged resource.
	 *
	 * <p>
	 * This class should never be instantiated. Always use {@link #of(String)}
	 * method to create a new instance.
	 * </p>
	 *
	 * @review
	 */
	@Immutable
	public abstract static class Paged extends Resource {

		/**
		 * Creates a new {@link Paged} with the provided {@code name}.
		 *
		 * @review
		 */
		public static Paged of(String name) {
			return ImmutablePaged.of(name);
		}

		/**
		 * The resource's name
		 *
		 * @review
		 */
		public abstract String name();

	}

}