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

package com.liferay.portal.service.impl;

import com.liferay.portal.kernel.model.PortalPreferenceValue;
import com.liferay.portal.kernel.model.PortalPreferences;
import com.liferay.portal.kernel.service.SQLStateAcceptor;
import com.liferay.portal.kernel.service.persistence.PortalPreferenceValuePersistence;
import com.liferay.portal.kernel.spring.aop.Property;
import com.liferay.portal.kernel.spring.aop.Retry;
import com.liferay.portal.service.base.PortalPreferenceValueLocalServiceBaseImpl;
import com.liferay.portlet.PortalPreferenceKey;
import com.liferay.portlet.PortalPreferencesImpl;
import com.liferay.portlet.internal.PreferenceUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author Preston Crary
 */
public class PortalPreferenceValueLocalServiceImpl
	extends PortalPreferenceValueLocalServiceBaseImpl {

	public static Map<PortalPreferenceKey, String[]> getPreferenceMap(
		PortalPreferenceValuePersistence portalPreferenceValuePersistence,
		long portalPreferencesId) {

		Map<PortalPreferenceKey, List<PortalPreferenceValue>>
			portalPreferenceValuesMap = getPortalPreferenceValuesMap(
				portalPreferenceValuePersistence, portalPreferencesId);

		Map<PortalPreferenceKey, String[]> preferenceMap = new HashMap<>();

		for (Map.Entry<PortalPreferenceKey, List<PortalPreferenceValue>> entry :
				portalPreferenceValuesMap.entrySet()) {

			List<PortalPreferenceValue> portalPreferenceValues =
				entry.getValue();

			String[] values = new String[portalPreferenceValues.size()];

			for (int i = 0; i < portalPreferenceValues.size(); i++) {
				PortalPreferenceValue portalPreferenceValue =
					portalPreferenceValues.get(i);

				values[i] = portalPreferenceValue.getValue();
			}

			preferenceMap.put(entry.getKey(), values);
		}

		return preferenceMap;
	}

	@Override
	public com.liferay.portal.kernel.portlet.PortalPreferences
		getPortalPreferences(
			PortalPreferences portalPreferences, boolean signedIn) {

		Map<PortalPreferenceKey, String[]> preferenceMap = getPreferenceMap(
			portalPreferenceValuePersistence,
			portalPreferences.getPortalPreferencesId());

		return new PortalPreferencesImpl(
			portalPreferences.getOwnerId(), portalPreferences.getOwnerType(),
			preferenceMap, signedIn);
	}

	@Override
	public String[] getPreferenceValues(
		long ownerId, int ownerType, String namespace, String key,
		String[] defaultValues) {

		PortalPreferences portalPreferences =
			portalPreferencesPersistence.fetchByO_O(ownerId, ownerType);

		if (portalPreferences == null) {
			return defaultValues;
		}

		List<PortalPreferenceValue> portalPreferenceValues =
			portalPreferenceValuePersistence.findByP_K_N(
				portalPreferences.getPortalPreferencesId(), key, namespace);

		String[] values = _getActualValues(portalPreferenceValues);

		if (values == null) {
			return defaultValues;
		}

		return values;
	}

	@Override
	@Retry(
		acceptor = SQLStateAcceptor.class,
		properties = {
			@Property(
				name = SQLStateAcceptor.SQLSTATE,
				value = SQLStateAcceptor.SQLSTATE_INTEGRITY_CONSTRAINT_VIOLATION + "," + SQLStateAcceptor.SQLSTATE_TRANSACTION_ROLLBACK
			)
		}
	)
	public void updatePreferenceValues(
		long ownerId, int ownerType, String namespace, String key,
		Function<String[], String[]> valuesFunction) {

		PortalPreferences portalPreferences =
			portalPreferencesPersistence.fetchByO_O(ownerId, ownerType);

		if (portalPreferences == null) {
			long portalPreferencesId = counterLocalService.increment();

			portalPreferences = portalPreferencesPersistence.create(
				portalPreferencesId);

			portalPreferences.setOwnerId(ownerId);
			portalPreferences.setOwnerType(ownerType);

			portalPreferences = portalPreferencesPersistence.update(
				portalPreferences);
		}

		List<PortalPreferenceValue> portalPreferenceValues =
			portalPreferenceValuePersistence.findByP_K_N(
				portalPreferences.getPortalPreferencesId(), key, namespace);

		String[] originalValues = _getActualValues(portalPreferenceValues);

		String[] newValues = valuesFunction.apply(originalValues);

		if (newValues == null) {
			for (PortalPreferenceValue portalPreferenceValue :
					portalPreferenceValues) {

				portalPreferenceValuePersistence.remove(portalPreferenceValue);
			}

			return;
		}

		if (Arrays.equals(originalValues, newValues)) {
			return;
		}

		newValues = PreferenceUtil.getXMLSafeValues(newValues);

		long batchCounter = 0;

		if (newValues.length > portalPreferenceValues.size()) {
			int newCount = newValues.length - portalPreferenceValues.size();

			batchCounter = counterLocalService.increment(
				PortalPreferenceValue.class.getName(), newCount);

			batchCounter -= newCount;
		}

		for (int i = 0; i < newValues.length; i++) {
			String value = newValues[i];

			if (portalPreferenceValues.size() > i) {
				PortalPreferenceValue portalPreferenceValue =
					portalPreferenceValues.get(i);

				if (!Objects.equals(
						newValues[i], portalPreferenceValue.getValue())) {

					portalPreferenceValue.setValue(value);

					portalPreferenceValuePersistence.update(
						portalPreferenceValue);
				}
			}
			else {
				PortalPreferenceValue portalPreferenceValue =
					portalPreferenceValuePersistence.create(++batchCounter);

				portalPreferenceValue.setPortalPreferencesId(
					portalPreferences.getPortalPreferencesId());
				portalPreferenceValue.setIndex(i);
				portalPreferenceValue.setKey(key);
				portalPreferenceValue.setNamespace(namespace);
				portalPreferenceValue.setValue(value);

				portalPreferenceValuePersistence.update(portalPreferenceValue);
			}
		}

		for (int i = newValues.length; i < portalPreferenceValues.size(); i++) {
			portalPreferenceValuePersistence.remove(
				portalPreferenceValues.get(i));
		}
	}

	protected static Map<PortalPreferenceKey, List<PortalPreferenceValue>>
		getPortalPreferenceValuesMap(
			PortalPreferenceValuePersistence portalPreferenceValuePersistence,
			long portalPreferencesId) {

		Map<PortalPreferenceKey, List<PortalPreferenceValue>>
			portalPreferenceValuesMap = new HashMap<>();

		for (PortalPreferenceValue portalPreferenceValue :
				portalPreferenceValuePersistence.findByPortalPreferencesId(
					portalPreferencesId)) {

			List<PortalPreferenceValue> portalPreferenceValues =
				portalPreferenceValuesMap.computeIfAbsent(
					new PortalPreferenceKey(
						portalPreferenceValue.getNamespace(),
						portalPreferenceValue.getKey()),
					key -> new ArrayList<>(1));

			portalPreferenceValues.add(portalPreferenceValue);
		}

		return portalPreferenceValuesMap;
	}

	private String[] _getActualValues(
		List<PortalPreferenceValue> portalPreferenceValues) {

		if (portalPreferenceValues.isEmpty()) {
			return null;
		}

		String[] values = new String[portalPreferenceValues.size()];

		for (int i = 0; i < portalPreferenceValues.size(); i++) {
			PortalPreferenceValue portalPreferenceValue =
				portalPreferenceValues.get(i);

			values[i] = portalPreferenceValue.getValue();
		}

		return PreferenceUtil.getActualValues(values);
	}

}