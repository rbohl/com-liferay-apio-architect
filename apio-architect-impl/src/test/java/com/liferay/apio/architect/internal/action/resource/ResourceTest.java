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

import static com.spotify.hamcrest.optional.OptionalMatchers.optionalWithValue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Alejandro Hernández
 */
public class ResourceTest {

	@Test
	public void testItemOfCreatesValidResourceItem() {
		Resource.Item itemResource = Resource.Item.of("name");

		assertThat(itemResource.name(), is("name"));
		assertEquals(itemResource, Resource.Item.of("name"));
	}

	@Test
	public void testItemOfWithIdCreatesValidResourceItem() {
		Resource.Item itemResource = Resource.Item.of("name", 42L);

		assertThat(itemResource.name(), is("name"));
		assertThat(itemResource.id(), is(optionalWithValue(equalTo(42L))));
		assertEquals(itemResource, Resource.Item.of("name"));
	}

	@Test
	public void testNestedOfCreatesValidResourceNested() {
		Resource.Nested nestedResource = Resource.Nested.of("parent", "name");

		assertThat(nestedResource.name(), is("name"));
		assertThat(nestedResource.parentName(), is("parent"));
		assertEquals(nestedResource, Resource.Nested.of("parent", "name"));
	}

	@Test
	public void testPagedOfCreatesValidResourcePaged() {
		Resource.Paged pagedResource = Resource.Paged.of("name");

		assertThat(pagedResource.name(), is("name"));
		assertEquals(pagedResource, Resource.Paged.of("name"));
	}

}