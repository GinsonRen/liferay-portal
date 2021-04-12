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

package com.liferay.portlet;

import com.liferay.petra.lang.HashUtil;
import com.liferay.portal.kernel.model.PortletConstants;
import com.liferay.portal.kernel.portlet.PortalPreferences;
import com.liferay.portal.kernel.service.PortalPreferenceValueLocalServiceUtil;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.simple.Element;
import com.liferay.portlet.internal.PreferenceUtil;

import java.io.Serializable;

import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.StaleObjectStateException;

/**
 * @author Brian Wing Shun Chan
 * @author Alexander Chow
 */
public class PortalPreferencesImpl
	implements Cloneable, PortalPreferences, Serializable {

	public PortalPreferencesImpl() {
		this(0, 0, Collections.emptyMap(), false);
	}

	public PortalPreferencesImpl(
		long ownerId, int ownerType,
		Map<PortalPreferenceKey, String[]> preferences, boolean signedIn) {

		_ownerId = ownerId;
		_ownerType = ownerType;
		_signedIn = signedIn;

		_originalPreferences = preferences;
	}

	@Override
	public PortalPreferencesImpl clone() {
		return new PortalPreferencesImpl(
			getOwnerId(), getOwnerType(), new HashMap<>(getPreferences()),
			isSignedIn());
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}

		if (!(object instanceof PortalPreferencesImpl)) {
			return false;
		}

		PortalPreferencesImpl portalPreferencesImpl =
			(PortalPreferencesImpl)object;

		if ((getOwnerId() == portalPreferencesImpl.getOwnerId()) &&
			(getOwnerType() == portalPreferencesImpl.getOwnerType()) &&
			Objects.equals(
				getPreferences(), portalPreferencesImpl.getPreferences())) {

			return true;
		}

		return false;
	}

	public Map<String, String[]> getMap(String namespace) {
		Map<PortalPreferenceKey, String[]> preferences = getPreferences();

		if (preferences.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<String, String[]> preferenceMap = new HashMap<>();

		for (Map.Entry<PortalPreferenceKey, String[]> entry :
				preferences.entrySet()) {

			PortalPreferenceKey portalPreferenceKey = entry.getKey();

			if (portalPreferenceKey.matchNamespace(namespace)) {
				preferenceMap.put(
					portalPreferenceKey.getKey(), entry.getValue());
			}
		}

		return preferenceMap;
	}

	public Enumeration<String> getNames(String namespace) {
		Map<String, String[]> preferences = getMap(namespace);

		return Collections.enumeration(preferences.keySet());
	}

	public long getOwnerId() {
		return _ownerId;
	}

	public int getOwnerType() {
		return _ownerType;
	}

	public Map<PortalPreferenceKey, String[]> getPreferences() {
		if (_modifiedPreferences != null) {
			return _modifiedPreferences;
		}

		return _originalPreferences;
	}

	@Override
	public long getUserId() {
		return _userId;
	}

	@Override
	public String getValue(String namespace, String key) {
		return getValue(namespace, key, null);
	}

	@Override
	public String getValue(String namespace, String key, String defaultValue) {
		Map<PortalPreferenceKey, String[]> preferences = getPreferences();

		String[] values = preferences.get(
			new PortalPreferenceKey(namespace, key));

		if (_isNull(values)) {
			return defaultValue;
		}

		return PreferenceUtil.getActualValue(values[0]);
	}

	@Override
	public String[] getValues(String namespace, String key) {
		return getValues(namespace, key, null);
	}

	@Override
	public String[] getValues(
		String namespace, String key, String[] defaultValue) {

		return _getValues(
			new PortalPreferenceKey(namespace, key), defaultValue);
	}

	@Override
	public int hashCode() {
		int hashCode = HashUtil.hash(0, getOwnerId());

		hashCode = HashUtil.hash(hashCode, getOwnerType());
		hashCode = HashUtil.hash(hashCode, getPreferences());

		return hashCode;
	}

	@Override
	public boolean isSignedIn() {
		return _signedIn;
	}

	public void reset(String namespace, String key) {
		PortalPreferenceKey portalPreferenceKey = new PortalPreferenceKey(
			namespace, key);

		String[] oldValues = _getValues(portalPreferenceKey, null);

		if (oldValues == null) {
			return;
		}

		Map<PortalPreferenceKey, String[]> modifiedPreferences =
			_getModifiedPreferences();

		modifiedPreferences.remove(portalPreferenceKey);

		_updatePreferenceValues(namespace, key, oldValues, null);
	}

	@Override
	public void resetValues(String namespace) {
		Map<PortalPreferenceKey, String[]> preferences = getPreferences();

		for (Map.Entry<PortalPreferenceKey, String[]> entry :
				preferences.entrySet()) {

			PortalPreferenceKey portalPreferenceKey = entry.getKey();

			if (portalPreferenceKey.matchNamespace(namespace)) {
				reset(namespace, portalPreferenceKey.getKey());
			}
		}
	}

	@Override
	public void setSignedIn(boolean signedIn) {
		_signedIn = signedIn;
	}

	@Override
	public void setUserId(long userId) {
		_userId = userId;
	}

	@Override
	public void setValue(String namespace, String key, String value) {
		if (Validator.isNull(key) || key.equals(_RANDOM_KEY)) {
			return;
		}

		if (value == null) {
			reset(namespace, key);

			return;
		}

		PortalPreferenceKey portalPreferenceKey = new PortalPreferenceKey(
			namespace, key);

		String[] oldValues = _getValues(portalPreferenceKey, null);

		if ((oldValues != null) && (oldValues.length == 1) &&
			value.equals(oldValues[0])) {

			return;
		}

		Map<PortalPreferenceKey, String[]> modifiedPreferences =
			_getModifiedPreferences();

		modifiedPreferences.put(
			portalPreferenceKey,
			new String[] {PreferenceUtil.getXMLSafeValue(value)});

		if (_signedIn) {
			_updatePreferenceValues(
				namespace, key, oldValues, new String[] {value});
		}
	}

	@Override
	public void setValues(String namespace, String key, String[] values) {
		if (Validator.isNull(key) || key.equals(_RANDOM_KEY)) {
			return;
		}

		if (values == null) {
			reset(namespace, key);

			return;
		}

		if (values.length == 1) {
			setValue(namespace, key, values[0]);

			return;
		}

		PortalPreferenceKey keyEntry = new PortalPreferenceKey(namespace, key);

		String[] oldValues = _getValues(keyEntry, null);

		if (Arrays.equals(oldValues, values)) {
			return;
		}

		Map<PortalPreferenceKey, String[]> modifiedPreferences =
			_getModifiedPreferences();

		modifiedPreferences.put(
			keyEntry, PreferenceUtil.getXMLSafeValues(values));

		if (_signedIn) {
			_updatePreferenceValues(namespace, key, oldValues, values);
		}
	}

	@Override
	public int size() {
		Map<PortalPreferenceKey, String[]> preferences = getPreferences();

		return preferences.size();
	}

	protected String toXML() {
		Map<PortalPreferenceKey, String[]> preferences = getPreferences();

		if ((preferences == null) || preferences.isEmpty()) {
			return PortletConstants.DEFAULT_PREFERENCES;
		}

		Element portletPreferencesElement = new Element(
			"portlet-preferences", false);

		for (Map.Entry<PortalPreferenceKey, String[]> entry :
				preferences.entrySet()) {

			PortalPreferenceKey portalPreferenceKey = entry.getKey();

			Element preferenceElement = portletPreferencesElement.addElement(
				"preference");

			preferenceElement.addElement(
				"name", portalPreferenceKey.getNamespacedKey());

			for (String value : entry.getValue()) {
				preferenceElement.addElement("value", value);
			}
		}

		return portletPreferencesElement.toXMLString();
	}

	private Map<PortalPreferenceKey, String[]> _getModifiedPreferences() {
		if (_modifiedPreferences == null) {
			_modifiedPreferences = new ConcurrentHashMap<>(
				_originalPreferences);
		}

		return _modifiedPreferences;
	}

	private String[] _getValues(
		PortalPreferenceKey portalPreferenceKey, String[] def) {

		Map<PortalPreferenceKey, String[]> preferences = getPreferences();

		String[] values = preferences.get(portalPreferenceKey);

		if (_isNull(values)) {
			return def;
		}

		return PreferenceUtil.getActualValues(values);
	}

	private boolean _isCausedByStaleObjectStateException(Throwable throwable) {
		Throwable causeThrowable = throwable.getCause();

		while (throwable != causeThrowable) {
			if (throwable instanceof StaleObjectStateException) {
				return true;
			}

			if (causeThrowable == null) {
				return false;
			}

			throwable = causeThrowable;

			causeThrowable = throwable.getCause();
		}

		return false;
	}

	private boolean _isNull(String[] values) {
		if (ArrayUtil.isEmpty(values) ||
			((values.length == 1) &&
			 (PreferenceUtil.getActualValue(values[0]) == null))) {

			return true;
		}

		return false;
	}

	private void _updatePreferenceValues(
		String namespace, String key, String[] oldValues, String[] newValues) {

		try {
			PortalPreferenceValueLocalServiceUtil.updatePreferenceValues(
				getOwnerId(), getOwnerType(), namespace, key,
				currentValues -> {
					if (!Arrays.equals(oldValues, currentValues)) {
						throw new ConcurrentModificationException();
					}

					return newValues;
				});
		}
		catch (Exception exception) {
			if (_isCausedByStaleObjectStateException(exception)) {
				throw new ConcurrentModificationException(exception);
			}

			throw exception;
		}
	}

	private static final String _RANDOM_KEY = "r";

	private Map<PortalPreferenceKey, String[]> _modifiedPreferences;
	private final Map<PortalPreferenceKey, String[]> _originalPreferences;
	private final long _ownerId;
	private final int _ownerType;
	private boolean _signedIn;
	private long _userId;

}