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

package com.liferay.adaptive.media.image.service.impl;

import aQute.bnd.annotation.ProviderType;

import com.liferay.adaptive.media.AdaptiveMediaRuntimeException;
import com.liferay.adaptive.media.image.configuration.AdaptiveMediaImageConfigurationEntry;
import com.liferay.adaptive.media.image.counter.AdaptiveMediaImageCounter;
import com.liferay.adaptive.media.image.exception.DuplicateAdaptiveMediaImageEntryException;
import com.liferay.adaptive.media.image.internal.storage.ImageStorage;
import com.liferay.adaptive.media.image.model.AdaptiveMediaImageEntry;
import com.liferay.adaptive.media.image.service.base.AdaptiveMediaImageEntryLocalServiceBaseImpl;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMap;
import com.liferay.osgi.service.tracker.collections.map.ServiceTrackerMapFactory;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.spring.extender.service.ServiceReference;

import java.io.InputStream;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

/**
 * Provides the local service for accessing, adding, deleting adaptive media
 * image entries.
 *
 * <p>
 * This service is responsible of adding adaptive media images both in the
 * database as adaptive media image entries as well as in the file store. Same
 * applies when deleting adaptive media images, it will remove the adaptive
 * media image entry and the bytes from the file store.
 * </p>
 *
 * <p>
 * This service is a low level API and, in general, it should not be used by
 * third party developers as the entry point for adaptive media images.
 * </p>
 *
 * @author Sergio González
 *
 * @review
 */
@ProviderType
public class AdaptiveMediaImageEntryLocalServiceImpl
	extends AdaptiveMediaImageEntryLocalServiceBaseImpl {

	/**
	 * Adds an adaptive media image entry in the database and store the image
	 * bytes in the file store.
	 *
	 * @param  configurationEntry the configuration used to create the adaptive
	 *         media image
	 * @param  fileVersion the file version used to create the adaptive media
	 *         image
	 * @param  width the width of the adaptive media image
	 * @param  height the height of the adaptive media image
	 * @param  inputStream the input stream of the adaptive media image that
	 *         will be stored in the file store
	 * @param  size the size of the adaptive media image
	 * @return the adaptive media image
	 * @throws PortalException if an adaptive media image already exists for the
	 *         file version and configuration
	 *
	 * @review
	 */
	@Override
	public AdaptiveMediaImageEntry addAdaptiveMediaImageEntry(
			AdaptiveMediaImageConfigurationEntry configurationEntry,
			FileVersion fileVersion, int width, int height,
			InputStream inputStream, int size)
		throws PortalException {

		_checkDuplicates(
			configurationEntry.getUUID(), fileVersion.getFileVersionId());

		long imageEntryId = counterLocalService.increment();

		AdaptiveMediaImageEntry imageEntry =
			adaptiveMediaImageEntryPersistence.create(imageEntryId);

		imageEntry.setCompanyId(fileVersion.getCompanyId());
		imageEntry.setGroupId(fileVersion.getGroupId());
		imageEntry.setCreateDate(new Date());
		imageEntry.setFileVersionId(fileVersion.getFileVersionId());
		imageEntry.setMimeType(fileVersion.getMimeType());
		imageEntry.setHeight(height);
		imageEntry.setWidth(width);
		imageEntry.setSize(size);
		imageEntry.setConfigurationUuid(configurationEntry.getUUID());

		imageStorage.save(
			fileVersion, configurationEntry.getUUID(), inputStream);

		return adaptiveMediaImageEntryPersistence.update(imageEntry);
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();

		Bundle bundle = FrameworkUtil.getBundle(
			AdaptiveMediaImageEntryLocalServiceImpl.class);

		BundleContext bundleContext = bundle.getBundleContext();

		_serviceTrackerMap = ServiceTrackerMapFactory.openSingleValueMap(
			bundleContext, AdaptiveMediaImageCounter.class,
			"adaptive.media.key");
	}

	/**
	 * Deletes all the adaptive media images generated for the configuration in
	 * the company.
	 *
	 * <p>
	 * This method deletes the adaptive media image entry from the database and
	 * it also deletes the bytes from the file store.
	 * </p>
	 *
	 * @param  companyId the primary key of the company
	 * @param  configurationEntry the configuration used to create the adaptive
	 *         media image
	 *
	 * @review
	 */
	@Override
	public void deleteAdaptiveMediaImageEntries(
		long companyId,
		AdaptiveMediaImageConfigurationEntry configurationEntry) {

		adaptiveMediaImageEntryPersistence.removeByC_C(
			companyId, configurationEntry.getUUID());

		imageStorage.delete(companyId, configurationEntry.getUUID());
	}

	/**
	 * Deletes all the adaptive media images generated for a file version.
	 *
	 * <p>
	 * This method deletes the adaptive media image entry from the database and
	 * it also deletes the bytes from the file store.
	 * </p>
	 *
	 * @param  fileVersion the FileVersion
	 * @throws PortalException if the file version cannot be found
	 *
	 * @review
	 */
	@Override
	public void deleteAdaptiveMediaImageEntryFileVersion(
			FileVersion fileVersion)
		throws PortalException {

		List<AdaptiveMediaImageEntry> imageEntries =
			adaptiveMediaImageEntryPersistence.findByFileVersionId(
				fileVersion.getFileVersionId());

		for (AdaptiveMediaImageEntry imageEntry : imageEntries) {
			try {
				adaptiveMediaImageEntryPersistence.remove(imageEntry);

				imageStorage.delete(
					fileVersion, imageEntry.getConfigurationUuid());
			}
			catch (AdaptiveMediaRuntimeException.IOException amreioe) {
				_log.error(amreioe);
			}
		}
	}

	@Override
	public void destroy() {
		super.destroy();

		_serviceTrackerMap.close();
	}

	/**
	 * Returns the adaptive media image entry generated for the configuration
	 * and file version.
	 *
	 * @param  configurationUuid the uuid of the configuration used to create
	 *         the adaptive media image
	 * @param  fileVersionId the primary key of the file version
	 * @return the matching adaptive media image entry, or <code>null</code> if
	 *         a matching adaptive media image entry could not be found
	 *
	 * @review
	 */
	@Override
	public AdaptiveMediaImageEntry fetchAdaptiveMediaImageEntry(
		String configurationUuid, long fileVersionId) {

		return adaptiveMediaImageEntryPersistence.fetchByC_F(
			configurationUuid, fileVersionId);
	}

	/**
	 * Returns the number of adaptive media image entries generated for the
	 * configuration in the company.
	 *
	 * @param  companyId the primary key of the company
	 * @param  configurationUuid the uuid of the configuration used to create
	 *         the adaptive media image
	 * @return the number of adaptive media image entries in the company for the
	 *         configuration
	 *
	 * @review
	 */
	@Override
	public int getAdaptiveMediaImageEntriesCount(
		long companyId, String configurationUuid) {

		return adaptiveMediaImageEntryPersistence.countByC_C(
			companyId, configurationUuid);
	}

	/**
	 * Returns the input stream of the adaptive media image generated for a file
	 * version and configuration.
	 *
	 * @param  configurationEntry the configuration used to create the adaptive
	 *         media image
	 * @param  fileVersion the file version used to create the adaptive media
	 *         image
	 * @return the input stream of the adaptive media image generated for a file
	 *         version and configuration
	 *
	 * @review
	 */
	@Override
	public InputStream getAdaptiveMediaImageEntryContentStream(
		AdaptiveMediaImageConfigurationEntry configurationEntry,
		FileVersion fileVersion) {

		return imageStorage.getContentStream(
			fileVersion, configurationEntry.getUUID());
	}

	/**
	 * Returns the number of adaptive media images that are expected to be in a
	 * company if all the images that support adaptive media already have an
	 * adaptive media image generated.
	 *
	 * <p>
	 * The number of the actual adaptive media images could be less if there are
	 * some images that haven't generated the adaptive media image yet.
	 * </p>
	 *
	 * @param  companyId the primary key of the company
	 * @return the number of expected adaptive media images for a company
	 *
	 * @review
	 */
	@Override
	public int getExpectedAdaptiveMediaImageEntriesCount(long companyId) {
		Collection<AdaptiveMediaImageCounter> imageCounters =
			_serviceTrackerMap.values();

		return imageCounters.stream().mapToInt(
			adaptiveMediaImageCounter ->
				adaptiveMediaImageCounter.
					countExpectedAdaptiveMediaImageEntries(companyId)).sum();
	}

	/**
	 * Returns the percentage of images that have an adaptive media image
	 * generated based on the expected number of adaptive media images for a
	 * configuration in a company.
	 *
	 * @param  companyId the primary key of the company
	 * @param  configurationUuid the uuid of the configuration used to create
	 *         the adaptive media image
	 * @return the percentage of images that have an adaptive media image out of
	 *         the expected adaptive media images
	 *
	 * @review
	 */
	@Override
	public int getPercentage(long companyId, String configurationUuid) {
		int expectedImageEntries = getExpectedAdaptiveMediaImageEntriesCount(
			companyId);

		if (expectedImageEntries == 0) {
			return 0;
		}

		int actualImageEntries = getAdaptiveMediaImageEntriesCount(
			companyId, configurationUuid);

		return Math.min(actualImageEntries * 100 / expectedImageEntries, 100);
	}

	@ServiceReference(type = DLAppLocalService.class)
	protected DLAppLocalService dlAppLocalService;

	@ServiceReference(type = ImageStorage.class)
	protected ImageStorage imageStorage;

	private void _checkDuplicates(String configurationUuid, long fileVersionId)
		throws DuplicateAdaptiveMediaImageEntryException {

		AdaptiveMediaImageEntry imageEntry =
			adaptiveMediaImageEntryPersistence.fetchByC_F(
				configurationUuid, fileVersionId);

		if (imageEntry != null) {
			throw new DuplicateAdaptiveMediaImageEntryException();
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		AdaptiveMediaImageEntryLocalServiceImpl.class);

	private ServiceTrackerMap<String, AdaptiveMediaImageCounter>
		_serviceTrackerMap;

}