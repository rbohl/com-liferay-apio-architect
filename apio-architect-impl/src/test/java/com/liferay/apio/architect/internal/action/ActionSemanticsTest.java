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

package com.liferay.apio.architect.internal.action;

import static com.liferay.apio.architect.operation.HTTPMethod.GET;

import static java.lang.String.join;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;

import com.liferay.apio.architect.internal.action.resource.Resource;

import io.vavr.CheckedFunction1;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * @author Alejandro Hernández
 */
public class ActionSemanticsTest {

	@Test
	public void testBuilderCreatesActionSemantics() throws Throwable {
		ActionSemantics actionSemantics = ActionSemantics.ofResource(
			Resource.Paged.of("name")
		).name(
			"action"
		).method(
			GET
		).receivesParams(
			String.class, Long.class
		).returns(
			Long.class
		).executeFunction(
			list -> join("-", (List<String>)list)
		).build();

		assertThat(actionSemantics.resource(), is(Resource.Paged.of("name")));
		assertThat(actionSemantics.name(), is("action"));
		assertThat(actionSemantics.method(), is("GET"));
		assertThat(
			actionSemantics.paramClasses(), contains(String.class, Long.class));
		assertThat(actionSemantics.returnClass(), is(equalTo(Long.class)));

		CheckedFunction1<List<?>, ?> executeFunction =
			actionSemantics.executeFunction();

		String result = (String)executeFunction.apply(Arrays.asList("1", "2"));

		assertThat(result, is("1-2"));
	}

	@Test
	public void testBuilderWithoutParamsCreatesActionSemantics()
		throws Throwable {

		ActionSemantics actionSemantics = ActionSemantics.ofResource(
			Resource.Paged.of("name")
		).name(
			"action"
		).method(
			GET
		).receivesNoParams(
		).returns(
			Long.class
		).executeFunction(
			list -> join("-", (List<String>)list)
		).build();

		assertThat(actionSemantics.resource(), is(Resource.Paged.of("name")));
		assertThat(actionSemantics.name(), is("action"));
		assertThat(actionSemantics.method(), is("GET"));
		assertThat(actionSemantics.paramClasses(), is(empty()));
		assertThat(actionSemantics.returnClass(), is(equalTo(Long.class)));

		CheckedFunction1<List<?>, ?> executeFunction =
			actionSemantics.executeFunction();

		String result = (String)executeFunction.apply(Arrays.asList("1", "2"));

		assertThat(result, is("1-2"));
	}

	@Test
	public void testBuilderWithoutReturnCreatesActionSemantics()
		throws Throwable {

		ActionSemantics actionSemantics = ActionSemantics.ofResource(
			Resource.Paged.of("name")
		).name(
			"action"
		).method(
			GET
		).receivesNoParams(
		).returnsNothing(
		).executeFunction(
			list -> join("-", (List<String>)list)
		).build();

		assertThat(actionSemantics.resource(), is(Resource.Paged.of("name")));
		assertThat(actionSemantics.name(), is("action"));
		assertThat(actionSemantics.method(), is("GET"));
		assertThat(actionSemantics.paramClasses(), is(empty()));
		assertThat(actionSemantics.returnClass(), is(equalTo(Void.class)));

		CheckedFunction1<List<?>, ?> executeFunction =
			actionSemantics.executeFunction();

		String result = (String)executeFunction.apply(Arrays.asList("1", "2"));

		assertThat(result, is("1-2"));
	}

	@Test
	public void testBuilderWithStringMethodCreatesActionSemantics()
		throws Throwable {

		ActionSemantics actionSemantics = ActionSemantics.ofResource(
			Resource.Paged.of("name")
		).name(
			"action"
		).method(
			"POST"
		).receivesNoParams(
		).returnsNothing(
		).executeFunction(
			_join
		).build();

		assertThat(actionSemantics.resource(), is(Resource.Paged.of("name")));
		assertThat(actionSemantics.name(), is("action"));
		assertThat(actionSemantics.method(), is("POST"));
		assertThat(actionSemantics.paramClasses(), is(empty()));
		assertThat(actionSemantics.returnClass(), is(equalTo(Void.class)));

		CheckedFunction1<List<?>, ?> executeFunction =
			actionSemantics.executeFunction();

		String result = (String)executeFunction.apply(Arrays.asList("1", "2"));

		assertThat(result, is("1-2"));
	}

	private static final CheckedFunction1<List<?>, Object> _join = list -> join(
		"-", (List<String>)list);

}