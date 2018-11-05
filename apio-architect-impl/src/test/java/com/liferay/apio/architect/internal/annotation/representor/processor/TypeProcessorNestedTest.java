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

package com.liferay.apio.architect.internal.annotation.representor.processor;

import static com.liferay.apio.architect.internal.annotation.representor.processor.TypeProcessortTestUtil.testFieldData;
import static com.liferay.apio.architect.internal.annotation.representor.processor.TypeProcessortTestUtil.testLinkedModelData;
import static com.liferay.apio.architect.internal.annotation.representor.processor.TypeProcessortTestUtil.testListFieldData;
import static com.liferay.apio.architect.internal.annotation.representor.processor.TypeProcessortTestUtil.testRelatedCollectionData;

import static org.hamcrest.core.IsEqual.equalTo;

import static org.junit.Assert.assertThat;

import com.liferay.apio.architect.internal.annotation.representor.types.Dummy.IntegerIdentifier;
import com.liferay.apio.architect.internal.annotation.representor.types.DummyWithNested;
import com.liferay.apio.architect.internal.annotation.representor.types.DummyWithNested.NestedDummy;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Víctor Galán
 */
public class TypeProcessorNestedTest {

	@BeforeClass
	public static void setUpClass() {
		ParsedType parsedType = TypeProcessor.proccesType(
			DummyWithNested.class);

		_nestedParsedType = parsedType.getParsedTypes().get(0);

		_parsedType = _nestedParsedType.getParsedType();
	}

	@Test
	public void testBasicFields() {
		List<FieldData> fieldMetadata = _parsedType.getFieldDataList();

		testFieldData(fieldMetadata.get(0), "stringField", String.class);
	}

	@Test
	public void testLinkedModels() {
		List<LinkedModelFieldData> linkedModelFieldData =
			_parsedType.getLinkedModelFieldDataList();

		testLinkedModelData(
			linkedModelFieldData.get(0), "linkedModel",
			IntegerIdentifier.class);
	}

	@Test
	public void testListFields() {
		List<ListFieldData> listFieldData = _parsedType.getListFieldDataList();

		testListFieldData(
			listFieldData.get(0), "stringListField", String.class);
	}

	@Test
	public void testNested() {
		assertThat(
			_nestedParsedType.getParsedTypeClass(), equalTo(NestedDummy.class));

		testFieldData(_nestedParsedType, "nestedDummy", NestedDummy.class);
	}

	@Test
	public void testRelatedCollections() {
		List<RelatedCollectionFieldData> relatedCollectionFieldData =
			_parsedType.getRelatedCollectionFieldDataList();

		testRelatedCollectionData(
			relatedCollectionFieldData.get(0), "relatedCollection",
			IntegerIdentifier.class);
	}

	private static NestedParsedType _nestedParsedType;
	private static ParsedType _parsedType;

}