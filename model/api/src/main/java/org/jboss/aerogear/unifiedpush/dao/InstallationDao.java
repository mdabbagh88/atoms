/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.dao;

import org.jboss.aerogear.unifiedpush.api.Installation;
import org.jboss.aerogear.unifiedpush.dto.Count;

import java.util.List;
import java.util.Set;

public interface InstallationDao extends GenericBaseDao<Installation, String> {

    /**
     * Loads a specific installation for the given Variant, specified by the device token.
     *
     * @param variantID the variantID for the filter
     * @param deviceToken the deviceToken for the filter
     *
     * @return intallation object or null.
     */
    Installation findInstallationForVariantByDeviceToken(String variantID, String deviceToken);

    /**
     * Loads a specific <b>enabled</b> installation for the given Variant, specified by the device token.
     *
     * @param variantID the variantID for the filter
     * @param deviceToken the deviceToken for the filter
     *
     * @return intallation object or null.
     */
    Installation findEnabledInstallationForVariantByDeviceToken(String variantID, String deviceToken);

    /**
     * Loads all installations matching the <code>Set</code> of deviceTokens, for the given Variant.
     *
     * @param variantID the variantID for the filter
     * @param deviceTokens the deviceTokens for the filter
     *
     * @return list of intallation objects.
     */
    List<Installation> findInstallationsForVariantByDeviceTokens(String variantID, Set<String> deviceTokens);

    /**
     * Loads all installations matching the alias, for the given list of variants.
     *
     * @param variantIDs variant IDs by which to filter.
     * @param alias alias by which to filter
     *
     * @return list of matching installations.
     */
    List<Installation> findInstallationsForVariantsByAlias(List<String> variantIDs, String alias);

    /**
     * Sender API for installations:
     *
     * Query all tokens for the given variant, by respecting a few criteria arguments (categories, aliases and deviceTypes)
     *
     * @param variantID the variantID for the filter
     * @param categories applied categories for the filter
     * @param aliases applied aliases for the filter
     * @param deviceTypes applied deviceTypes for the filter
     * @param maxResults number of maxResults for the filter
     * @param lastTokenFromPreviousBatch identifier of the last fetched token
     * @param oldGCM if true only old GCM tokens (not containing a :) are load
     *
     * @return list of device tokens that matches this filter
     */
    ResultsStream.QueryBuilder<String> findAllDeviceTokenForVariantIDByCriteria(String variantID, List<String> categories, List<String> aliases, List<String> deviceTypes, int maxResults, String lastTokenFromPreviousBatch, boolean oldGCM);

    Set<String> findAllDeviceTokenForVariantID(String variantID);

    /**
     * Find all installations for the variant specified and for the authneticated user.
     * @param variantID the id of the variant to find the installations for
     * @param developer the developer
     * @param page the page number
     * @param pageSize the size of the pages
     *
     * @return all installations found or empty list + the total count of results
     */
    PageResult<Installation, Count> findInstallationsByVariantForDeveloper(String variantID, String developer, Integer page, Integer pageSize, String search);

    /**
     * Find all installations for the variant specified (used for admin role)
     * @param variantID the id of the variant to find the installations for
     * @param page the page number
     * @param pageSize the size of the pages
     *
     * @return all installations found or empty list + the total count of results
     */
    PageResult<Installation, Count> findInstallationsByVariant(String variantID, Integer page, Integer pageSize, String search);


    /**
     * Counts the total number of registered devices/clients for the give List of variantIDs
     *
     * @param loginName name of the current user
     *
     * @return number of devices for user
     */
    long getNumberOfDevicesForLoginName(String loginName);

    //Admin query
    /**
     * Counts the total number of all registered devices/clients
     *
     * @return number of devices for admin
     */
    long getTotalNumberOfDevices();

    /**
     * Counts the number of registered devices/clients for the given variant ID
     *
     * @param variantId the variant ID
     *
     * @return number of devices for given variant
     */
    long getNumberOfDevicesForVariantID(String variantId);

    /**
     * Find all installations matching the given variant IDs whose alias is not in the {@code aliases} list.
     * @param variantIDs variant IDs to include.
     * @param aliases aliases the aliases the installation do not match against.
     * @return all the matching installations
     */
	List<Installation> findByVariantIDsNotInAliasList(List<String> variantIDs, List<String> aliases);

	/**
     * Find all installations matching the given variant IDs whose alias is in the {@code aliases} list.
     * @param variantIDs variant IDs to include.
     * @param aliases aliases the aliases the installation do not match against.
     * @return all the matching installations
     */
	List<Installation> findByVariantIDsInAliasList(List<String> variantIDs, List<String> aliases);

	/**
	 * Remove any installations with the given alias.
	 *
	 * @param alias alias of installations
	 * @return the number of installations that have been disabled
	 */
	int removeInstallationsByAlias(String alias);

	/**
	 * Returns the subset of the given aliases whose device is marked as enabled.
	 *
	 * @param aliases aliases to filter
	 * @return aliases that are enabled
	 */
	Set<String> filterDisabledDevices(Set<String> aliases);
}
