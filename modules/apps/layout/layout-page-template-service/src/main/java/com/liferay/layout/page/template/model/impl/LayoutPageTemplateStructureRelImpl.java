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

package com.liferay.layout.page.template.model.impl;

import com.liferay.layout.util.structure.LayoutStructure;
import com.liferay.portal.kernel.model.cache.CacheField;

/**
 * @author Eudaldo Alonso
 */
public class LayoutPageTemplateStructureRelImpl
	extends LayoutPageTemplateStructureRelBaseImpl {

	@Override
	public LayoutStructure getLayoutStructure() {
		if (_layoutStructure != null) {
			return _layoutStructure;
		}

		_layoutStructure = LayoutStructure.of(getData());

		return _layoutStructure;
	}

	@Override
	public void setLayoutStructure(LayoutStructure layoutStructure) {
		_layoutStructure = layoutStructure;
	}

	@CacheField(propagateToInterface = true)
	private LayoutStructure _layoutStructure;

}