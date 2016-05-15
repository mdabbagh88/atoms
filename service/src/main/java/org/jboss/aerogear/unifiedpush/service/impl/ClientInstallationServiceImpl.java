/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ejb.Asynchronous;
import javax.ejb.DependsOn;
import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.jboss.aerogear.unifiedpush.api.Alias;
import org.jboss.aerogear.unifiedpush.api.Category;
import org.jboss.aerogear.unifiedpush.api.Installation;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.api.VariantType;
import org.jboss.aerogear.unifiedpush.dao.AliasDao;
import org.jboss.aerogear.unifiedpush.dao.CategoryDao;
import org.jboss.aerogear.unifiedpush.dao.InstallationDao;
import org.jboss.aerogear.unifiedpush.dao.PushApplicationDao;
import org.jboss.aerogear.unifiedpush.dao.ResultsStream;
import org.jboss.aerogear.unifiedpush.service.ClientInstallationService;
import org.jboss.aerogear.unifiedpush.service.Configuration;
import org.jboss.aerogear.unifiedpush.service.VerificationService;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedIn;
import org.jboss.aerogear.unifiedpush.utils.AeroGearLogger;

/**
 * (Default) implementation of the {@code ClientInstallationService} interface.
 * Delegates work to an injected DAO object.
 */
@Stateless
@DependsOn(value={"Configuration", "VerificationServiceImpl"})
public class ClientInstallationServiceImpl implements ClientInstallationService {
    private final AeroGearLogger logger = AeroGearLogger.getInstance(ClientInstallationServiceImpl.class);

    @Inject
    private InstallationDao installationDao;

    @Inject
    private CategoryDao categoryDao;

    @Inject
    private AliasDao aliasDao;

    @Inject
    private PushApplicationDao pushApplicationDao;

    @Inject
    @LoggedIn
    private Instance<String> developer;

	@Inject
	private VerificationService verificationService;

    @Inject
	private Configuration configuration;

	@Override
	public Variant associateInstallation(Installation installation, Variant currentVariant) {
		if (installation.getAlias() == null) {
			logger.warning("Unable to associate, installation alias is missing!");
			return null;
		}

		Alias alias = aliasDao.findByName(installation.getAlias());

		if (alias == null) {
			return null;
		}

		PushApplication application = pushApplicationDao.findByPushApplicationID(alias.getPushApplicationID());
		List<Variant> variants = application.getVariants();

		for (Variant variant : variants) {
			// Match variant type according to previous variant.
			if(variant.getType().equals(currentVariant.getType())){
				installation.setVariant(variant);
				updateInstallation(installation);
				return variant;
			}
		}

		return null;
	}

	@Override
	public void addInstallationSynchronously(Variant variant, Installation entity) {
		this.addInstallation(variant, entity);
	}

    @Override
    @Asynchronous
    public void addInstallation(Variant variant, Installation entity) {
    	boolean shouldVerifiy = configuration.getProperty(Configuration.PROP_ENABLE_VERIFICATION, false);

    	// does it already exist ?
        Installation installation = this.findInstallationForVariantByDeviceToken(variant.getVariantID(), entity.getDeviceToken());

        // Needed for the Admin UI Only. Help for setting up Routes
        entity.setPlatform(variant.getType().getTypeName());

        // new device/client ?
        if (installation == null) {
            logger.finest("Performing new device/client registration");

        	// Prevent a device (with alias) to registered multiple times
        	// using different tokens.
            if (entity.getAlias() != null && entity.getAlias().length() != 0)
            	removePreviousInstallations(entity.getAlias());

            // Verification process required, disable device.
            if (shouldVerifiy)
            	entity.setEnabled(false);

            // store the installation:
            storeInstallationAndSetReferences(variant, entity);
        } else {
            // We only update the metadata, if the device is enabled:
            if (installation.isEnabled()) {
                logger.finest("Updating received metadata for an 'enabled' installation");
                // update the entity:
                this.updateInstallation(installation, entity);
            }
        }

        // A better implementation would initiate a new REST call when registration is done.
        if (shouldVerifiy)
        	verificationService.initiateDeviceVerification(entity, variant);
    }

	@Override
    public void addInstallationsSynchronously(Variant variant, List<Installation> installations){
    	this.addInstallations(variant, installations);
    }

    @Override
    @Asynchronous
    public void addInstallations(Variant variant, List<Installation> installations) {

        // don't bother
        if (installations == null || installations.isEmpty()) {
            return;
        }

        // TODO - On a large scale database, we can't load entire device list.
        Set<String> existingTokens = installationDao.findAllDeviceTokenForVariantID(variant.getVariantID());

        // clear out:
        installationDao.flushAndClear();

        for (int i = 0; i < installations.size(); i++) {

            Installation current = installations.get(i);

            // let's avoid duplicated tokens/devices per variant
            // For devices without a token, let's also not bother the DAO layer to throw BeanValidation exception
            if (!existingTokens.contains(current.getDeviceToken()) && hasTokenValue(current)) {

                logger.finest("Importing device with token: " + current.getDeviceToken());

                storeInstallationAndSetReferences(variant, current);

                // and add a reference to the existing tokens set, to ensure the JSON file contains no duplicates:
                existingTokens.add(current.getDeviceToken());

                // some tunings, ever 10k devices releasing resources
                if (i % 10000 == 0) {
                    logger.finest("releasing some resources during import");
                    installationDao.flushAndClear();
                }
            } else {
                // for now, we ignore them.... no update applied!
                logger.finest("Device with token '" + current.getDeviceToken() + "' already exists. Ignoring it ");
            }
        }
        // clear out:
        installationDao.flushAndClear();
    }

    @Override
    public void removeInstallations(List<Installation> installations) {
        // uh... :)
        for (Installation installation : installations) {
            removeInstallation(installation);
        }
    }

    public void updateInstallation(Installation installation) {
        installationDao.update(installation);
    }

    @Override
    public void updateInstallation(Installation installationToUpdate, Installation postedInstallation) {
        // copy the "updateable" values:
        mergeCategories(installationToUpdate, postedInstallation.getCategories());

        installationToUpdate.setDeviceToken(postedInstallation.getDeviceToken());
        installationToUpdate.setAlias(postedInstallation.getAlias());
        installationToUpdate.setDeviceType(postedInstallation.getDeviceType());
        installationToUpdate.setOperatingSystem(postedInstallation
                .getOperatingSystem());
        installationToUpdate.setOsVersion(postedInstallation.getOsVersion());
        installationToUpdate.setEnabled(postedInstallation.isEnabled());
        installationToUpdate.setPlatform(postedInstallation.getPlatform());

        // update it:
        updateInstallation(installationToUpdate);
    }

    @Override
    public Installation findById(String primaryKey) {
        return installationDao.find(primaryKey);
    }

    @Override
    public void removeInstallation(Installation installation) {
        installationDao.delete(installation);
    }

    @Override
    @Asynchronous
    public void removeInstallationsForVariantByDeviceTokens(String variantID, Set<String> deviceTokens) {
        // collect inactive installations for the given variant:
        List<Installation> inactiveInstallations = installationDao.findInstallationsForVariantByDeviceTokens(variantID, deviceTokens);
        // get rid of them
        this.removeInstallations(inactiveInstallations);
    }

    @Override
    public void removeInstallationForVariantByDeviceTokenSynchronously(String variantID, String deviceToken) {
    	this.removeInstallationForVariantByDeviceToken(variantID, deviceToken);
    }

    @Override
    @Asynchronous
    public void removeInstallationForVariantByDeviceToken(String variantID, String deviceToken) {
        removeInstallation(findInstallationForVariantByDeviceToken(variantID, deviceToken));
    }

    @Override
    public Installation findInstallationForVariantByDeviceToken(String variantID, String deviceToken) {
        return installationDao.findInstallationForVariantByDeviceToken(variantID, deviceToken);
    }

    @Override
    public Installation findEnabledInstallationForVariantByDeviceToken(String variantID, String deviceToken) {
        return installationDao.findEnabledInstallationForVariantByDeviceToken(variantID, deviceToken);
    }

    /**
     * Synchronize installations according to alias list.
     * Disable any installation not in alias list
     * Enable any existing installation is alias list.
     * @param application
     * @param aliases
     */
    @Override
	public void syncInstallationByAliasList(PushApplication application, List<String> aliases) {
		List<String> variantIDs = new ArrayList<>(application.getVariants().size());
		for (Variant variant : application.getVariants()) {
			variantIDs.add(variant.getVariantID());
		}

		if (!aliases.isEmpty()) {
			final List<Installation> installations = installationDao.findByVariantIDsNotInAliasList(variantIDs, aliases);
			if (!installations.isEmpty()) {
				disableInstallations(installations);
			}

			final List<Installation> existingInstallations = installationDao.findByVariantIDsInAliasList(variantIDs, aliases);
			if (!existingInstallations.isEmpty()) {
				enableInstallations(existingInstallations);
			}
		}
	}

    // =====================================================================
    // ======== Various finder services for the Sender REST API ============
    // =====================================================================

    /**
     * Finder for 'send', used for Android, iOS and SimplePush clients
     */
    @Override
    public ResultsStream.QueryBuilder<String> findAllDeviceTokenForVariantIDByCriteria(String variantID, List<String> categories, List<String> aliases, List<String> deviceTypes, int maxResults, String lastTokenFromPreviousBatch) {
        return installationDao.findAllDeviceTokenForVariantIDByCriteria(variantID, categories, aliases, deviceTypes, maxResults, lastTokenFromPreviousBatch, false);
    }

    @Override
    public ResultsStream.QueryBuilder<String> findAllOldGoogleCloudMessagingDeviceTokenForVariantIDByCriteria(String variantID, List<String> categories, List<String> aliases, List<String> deviceTypes, int maxResults, String lastTokenFromPreviousBatch) {
        return installationDao.findAllDeviceTokenForVariantIDByCriteria(variantID, categories, aliases, deviceTypes, maxResults, lastTokenFromPreviousBatch, true);
    }

    /**
     * A simple validation util that checks if a token is present
     */
    private boolean hasTokenValue(Installation installation) {
        return (installation.getDeviceToken() != null && (!installation.getDeviceToken().isEmpty()));
    }

    /**
     * When an installation is created or updated, the categories are passed without IDs.
     * This method solve this issue by checking for existing categories and updating them (otherwise it would
     * persist a new object).
     * @param entity to merge the categories for
     * @param categoriesToMerge are the categories to merge with the existing one
     */
    private void mergeCategories(Installation entity, Set<Category> categoriesToMerge) {
        if (entity.getCategories() != null) {
            final List<String> categoryNames = convertToNames(categoriesToMerge);
            final List<Category> existingCategoriesFromDB = categoryDao.findByNames(categoryNames);

            // Replace json dematerialised categories with their persistent counter parts (see Category.equals),
            // by remove existing/persistent categories from the new collection, and adding them back in (with their PK).
            categoriesToMerge.removeAll(existingCategoriesFromDB);
            categoriesToMerge.addAll(existingCategoriesFromDB);

            // and apply the passed in ones.
            entity.setCategories(categoriesToMerge);
        }
    }

    private List<String> convertToNames(Set<Category> categories) {
        List<String> result = new ArrayList<String>();
        for (Category category : categories) {
            result.add(category.getName());
        }
        return result;
    }

    /*
     * Helper to set references and perform the actual storage
     */
    protected void storeInstallationAndSetReferences(Variant variant, Installation entity) {

        // ensure lower case for iOS
        if (variant.getType().equals(VariantType.IOS)) {
            entity.setDeviceToken(entity.getDeviceToken().toLowerCase());
        }
        // set reference
        entity.setVariant(variant);
        // update attached categories
        mergeCategories(entity, entity.getCategories());
        // store Installation entity
        installationDao.create(entity);
    }

    private void removePreviousInstallations(String alias) {
		installationDao.removeInstallationsByAlias(alias);
	}

    private void disableInstallations(List<Installation> installations) {
        for (Installation installation : installations) {
        	installation.setEnabled(false);
            updateInstallation(installation);
        }
    }

    private void enableInstallations(List<Installation> installations) {
        for (Installation installation : installations) {
        	installation.setEnabled(true);
            updateInstallation(installation);
        }
    }
}
