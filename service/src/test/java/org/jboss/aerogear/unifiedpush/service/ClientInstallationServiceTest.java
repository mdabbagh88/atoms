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
package org.jboss.aerogear.unifiedpush.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.jboss.aerogear.unifiedpush.api.AndroidVariant;
import org.jboss.aerogear.unifiedpush.api.Category;
import org.jboss.aerogear.unifiedpush.api.Installation;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.api.iOSVariant;
import org.jboss.aerogear.unifiedpush.dao.ResultStreamException;
import org.jboss.aerogear.unifiedpush.dao.ResultsStream;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;

public class ClientInstallationServiceTest extends AbstractBaseServiceTest {

    @Inject
    private ClientInstallationService clientInstallationService;

    @Inject
    private GenericVariantService variantService;

    @Inject
    private PushApplicationService applicationService;

    private AndroidVariant androidVariant;

    @Override
    protected void specificSetup() {
        // setup a variant:
        androidVariant = new AndroidVariant();
        androidVariant.setGoogleKey("Key");
        androidVariant.setName("Android");
        androidVariant.setDeveloper("me");
        variantService.addVariant(androidVariant);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void testLowerCaseForIOS() throws IOException {
        iOSVariant iOSVariant = new iOSVariant();
        byte[] certificate= toByteArray(getClass().getResourceAsStream("/cert/certificate.p12"));
        iOSVariant.setCertificate(certificate);
        iOSVariant.setPassphrase("12345678");
        variantService.addVariant(iOSVariant);

        Installation device = new Installation();
        device.setAlias("SomeAlias");
        String deviceToken = TestUtils.generateFakedDeviceTokenString().toUpperCase();
        device.setDeviceToken(deviceToken);

        clientInstallationService.addInstallationSynchronously(iOSVariant, device);

        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(iOSVariant.getVariantID(), deviceToken)).isNull();
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(iOSVariant.getVariantID(), deviceToken.toLowerCase())).isNotNull();
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void registerDevices() {
        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString().toUpperCase();
        device.setDeviceToken(deviceToken);
        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(1);

        // apply some update:
        Installation otherDevice = new Installation();
        otherDevice.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        otherDevice.setAlias("username");

        clientInstallationService.addInstallationSynchronously(androidVariant, otherDevice);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(2);

        // Replace token and re-registered
        otherDevice.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        clientInstallationService.addInstallationSynchronously(androidVariant, otherDevice);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(2);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void registerDevicesWithCategories() {

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString().toUpperCase();
        device.setDeviceToken(deviceToken);
        final Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("football")));
        device.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).hasSize(1);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void registerTwoDevicesWithDifferentCategories() {
        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);

        Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device.setCategories(categories);

        device.setVariant(androidVariant);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).hasSize(2);

        // second device, with slightly different metadata
        device = new Installation();
        deviceToken = TestUtils.generateFakedDeviceTokenString().toUpperCase();
        device.setDeviceToken(deviceToken);
        categories = new HashSet<Category>(Arrays.asList(new Category("lame"), new Category("football")));
        device.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).hasSize(2);

        assertThat(
                clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories())
                .extracting("name")
                .contains("football","lame")
                .doesNotContain("soccer");
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void removeOneCategoryFromPreviouslyRegisteredDevice() {
        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);

        Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device.setCategories(categories);

        device.setVariant(androidVariant);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).hasSize(2);

        // same device, with slightly different metadata
        device = new Installation();
        device.setDeviceToken(deviceToken);
        categories = new HashSet<Category>(Arrays.asList(new Category("football")));
        device.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).hasSize(1);

        assertThat(
                clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories())
                .extracting("name")
                .contains("football")
                .doesNotContain("soccer");
    }


    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void registerDevicesAndUpdateWithCategories() {
        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString().toUpperCase();
        device.setDeviceToken(deviceToken);
        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).isEmpty();

        device = new Installation();
        device.setDeviceToken(deviceToken);
        final Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("football")));
        device.setCategories(categories);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(),deviceToken).getCategories()).hasSize(1);
    }


    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void updateDevice() {
        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);
        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(1);

        // apply some update:
        Installation sameDeviceDifferentRegistration = new Installation();
        sameDeviceDifferentRegistration.setDeviceToken(deviceToken);
        sameDeviceDifferentRegistration.setAlias("username");

        clientInstallationService.addInstallationSynchronously(androidVariant, sameDeviceDifferentRegistration);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(1);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void importDevicesWithAndWithoutTokenDuplicates() {
        // generate some devices with token:
        final int NUMBER_OF_INSTALLATIONS = 5;
        final List<Installation> devices = new ArrayList<Installation>();
        for (int i = 0; i < NUMBER_OF_INSTALLATIONS; i++) {
            Installation device = new Installation();
            device.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
            devices.add(device);
        }

        // add two more with invalid token:
        Installation device = new Installation();
        devices.add(device);

        device = new Installation();
        device.setDeviceToken("");
        devices.add(device);


        // a few invalid ones....
        assertThat(devices).hasSize(NUMBER_OF_INSTALLATIONS + 2);

        clientInstallationService.addInstallationsSynchronously(androidVariant, devices);

        // but they got ignored:
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(NUMBER_OF_INSTALLATIONS);

        // add just one device:
        device = new Installation();
        device.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        devices.add(device);

        // run the importer again
        clientInstallationService.addInstallationsSynchronously(androidVariant, devices);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(NUMBER_OF_INSTALLATIONS + 1);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void createAndDeleteDeviceByToken() {
        Installation device = new Installation();
        device.setDeviceToken(TestUtils.generateFakedDeviceTokenString());

        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(1);

        final String singleToken = device.getDeviceToken();
        clientInstallationService.removeInstallationForVariantByDeviceTokenSynchronously(androidVariant.getVariantID(), singleToken);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).isEmpty();
    }


    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void importDevicesWithoutDuplicates() {
        // generate some devices:
        final int NUMBER_OF_INSTALLATIONS = 5;
        final List<Installation> devices = new ArrayList<Installation>();
        for (int i = 0; i < NUMBER_OF_INSTALLATIONS; i++) {
            Installation device = new Installation();
            device.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
            devices.add(device);
        }

        clientInstallationService.addInstallationsSynchronously(androidVariant, devices);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(NUMBER_OF_INSTALLATIONS);

        // add just one device:
        Installation device = new Installation();
        device.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        devices.add(device);

        // run the importer again
        clientInstallationService.addInstallationsSynchronously(androidVariant, devices);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(NUMBER_OF_INSTALLATIONS + 1);
    }


    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void importDevices() {
        // generate some devices:
        final int NUMBER_OF_INSTALLATIONS = 100000;
        final List<Installation> devices = new ArrayList<Installation>();
        for (int i = 0; i < NUMBER_OF_INSTALLATIONS; i++) {
            Installation device = new Installation();
            device.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
            devices.add(device);
        }

        clientInstallationService.addInstallationsSynchronously(androidVariant, devices);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null)).hasSize(NUMBER_OF_INSTALLATIONS);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void findSingleDeviceTokenWithMultipleCategories() {

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);

        final Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device.setCategories(categories);

        device.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), Arrays.asList("football", "soccer"), null, null)).hasSize(1);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void findSingleDeviceTokenWithMultipleCategoriesAndByAlias() {

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);
        device.setAlias("root");

        final Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device.setCategories(categories);

        device.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), Arrays.asList("football", "soccer"), Arrays.asList("root"), null)).hasSize(1);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void updateDeviceByRemovingCategory() {

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);
        device.setAlias("root");

        final Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device.setCategories(categories);

        device.setVariant(androidVariant);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), Arrays.asList("football", "soccer"), Arrays.asList("root"), null)).hasSize(1);
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(), deviceToken).getCategories()).hasSize(2);

        // simulate a post WITHOUT the categories metadataad
        device = new Installation();
        device.setDeviceToken(deviceToken);
        device.setAlias("root");

        // and update
        clientInstallationService.addInstallationSynchronously(androidVariant, device);
        assertThat(clientInstallationService.findInstallationForVariantByDeviceToken(androidVariant.getVariantID(), deviceToken).getCategories()).isEmpty();
    }

    @Test
    public void findDeviceTokensWithSingleCategory() {

        Installation device1 = new Installation();
        device1.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device1.setCategories(categories);
        device1.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device1);
        clientInstallationService.addInstallationSynchronously(androidVariant, device1);


        Installation device2 = new Installation();
        device2.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("soccer")));
        device2.setCategories(categories);
        device2.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device2);
        clientInstallationService.addInstallationSynchronously(androidVariant, device2);


        Installation device3 = new Installation();
        device3.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("football")));
        device3.setCategories(categories);
        device3.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device3);
        clientInstallationService.addInstallationSynchronously(androidVariant, device3);


        final List<String> queriedTokens = findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), Arrays.asList("soccer"), null, null);

        assertThat(queriedTokens).hasSize(2);
        assertThat(queriedTokens).contains(
                device1.getDeviceToken(),
                device2.getDeviceToken()
        );
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void findDeviceTokensWithMultipleCategories() {

        Installation device1 = new Installation();
        device1.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device1.setCategories(categories);
        device1.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device1);
        clientInstallationService.addInstallationSynchronously(androidVariant, device1);

        Installation device2 = new Installation();
        device2.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("soccer")));
        device2.setCategories(categories);
        device2.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device2);
        clientInstallationService.addInstallationSynchronously(androidVariant, device2);

        Installation device3 = new Installation();
        device3.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("football")));
        device3.setCategories(categories);

        device3.setVariant(androidVariant);
        clientInstallationService.updateInstallation(device3);
        clientInstallationService.addInstallationSynchronously(androidVariant, device3);

        final List<String> queriedTokens = findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), Arrays.asList("soccer", "football"), null, null);

        assertThat(queriedTokens).hasSize(3);
        assertThat(queriedTokens).contains(
                device1.getDeviceToken(),
                device2.getDeviceToken(),
                device3.getDeviceToken()
        );
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void findDeviceTokensWithoutAnyCriteria() {

        Installation device1 = new Installation();
        device1.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        Set<Category> categories = new HashSet<Category>(Arrays.asList(new Category("football"), new Category("soccer")));
        device1.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device1);

        Installation device2 = new Installation();
        device2.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("soccer")));
        device2.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device2);

        Installation device3 = new Installation();
        device3.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("football")));
        device3.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device3);

        Installation device4 = new Installation();
        device4.setDeviceToken("01234567891:"+TestUtils.generateFakedDeviceTokenString());
        categories = new HashSet<Category>(Arrays.asList(new Category("football")));
        device4.setCategories(categories);
        clientInstallationService.addInstallationSynchronously(androidVariant, device4);

        final List<String> queriedTokens = findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null);

        assertThat(queriedTokens).hasSize(4);
        assertThat(queriedTokens).contains(
                device1.getDeviceToken(),
                device2.getDeviceToken(),
                device3.getDeviceToken(),
                device4.getDeviceToken()
        );

        final List<String> legacyTokenz = findAllOldGoogleCloudMessagingDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, null, null);

        assertThat(legacyTokenz).hasSize(3);
        assertThat(legacyTokenz).contains(
                device1.getDeviceToken(),
                device2.getDeviceToken(),
                device3.getDeviceToken()
        );
        assertThat(legacyTokenz).doesNotContain(
                device4.getDeviceToken()
        );
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void findDeviceTokensByAlias() {

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);
        device.setAlias("root");
        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        // apply some update:
        Installation otherDevice = new Installation();
        otherDevice.setDeviceToken(TestUtils.generateFakedDeviceTokenString());
        otherDevice.setAlias("root");
        clientInstallationService.addInstallationSynchronously(androidVariant, otherDevice);

        assertThat(findAllDeviceTokenForVariantIDByCriteria(androidVariant.getVariantID(), null, Arrays.asList("root"), null)).hasSize(1);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void findDeviceVariantByAlias() {

     	AndroidVariant variant = new AndroidVariant();
        variant.setGoogleKey("Key");
        variant.setName("NewVaraint");
        variant.setDeveloper("me");
        variantService.addVariant(variant);

        PushApplication application = new PushApplication();
        application.setName("NewApp");
        applicationService.addPushApplication(application);
        applicationService.addVariant(application, variant);

        String installationAlias = "p1";

        List<String> aliases = Arrays.asList("a", "b", "p1");

        pushApplicationService.updateAliasesAndInstallations(application, aliases);

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);
        device.setAlias(installationAlias);

        clientInstallationService.addInstallationSynchronously(androidVariant, device);

        Variant var = clientInstallationService.associateInstallation(device, variant);
        assertThat(var.getVariantID().equals(variant.getId()));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void testUpdateAliasesAndInstallation() {
    	AndroidVariant variant = new AndroidVariant();
        variant.setGoogleKey("Key");
        variant.setName("NewVaraint");
        variant.setDeveloper("me");
        variantService.addVariant(variant);

        PushApplication application = new PushApplication();
        application.setName("NewApp");
        applicationService.addPushApplication(application);
        applicationService.addVariant(application, variant);

        String installationAlias = "p1";

        List<String> aliases = Arrays.asList("a", "b", installationAlias);

        Installation device = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        device.setDeviceToken(deviceToken);
        device.setAlias(installationAlias);

        clientInstallationService.addInstallationSynchronously(variant, device);

        pushApplicationService.updateAliasesAndInstallations(application, aliases);

        Installation installation = clientInstallationService.findInstallationForVariantByDeviceToken(variant.getVariantID(), deviceToken);
        assertNotNull(installation);

        pushApplicationService.updateAliasesAndInstallations(application, Arrays.asList("a", "b"));

        installation = clientInstallationService.findInstallationForVariantByDeviceToken(variant.getVariantID(), deviceToken);
        assertThat(installation.isEnabled()==false);

        pushApplicationService.updateAliasesAndInstallations(application, aliases);
        installation = clientInstallationService.findInstallationForVariantByDeviceToken(variant.getVariantID(), deviceToken);

        assertThat(installation.isEnabled()==true);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void testFindDisabledInstallationForVariantByDeviceToken() {
    	AndroidVariant variant = new AndroidVariant();
        variant.setGoogleKey("Key");
        variant.setName("NewVaraint");
        variant.setDeveloper("me");
        variantService.addVariant(variant);

        PushApplication application = new PushApplication();
        application.setName("NewApp");
        applicationService.addPushApplication(application);
        applicationService.addVariant(application, variant);

        Installation disabled = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        disabled.setDeviceToken(deviceToken);
        disabled.setEnabled(false);
        clientInstallationService.addInstallationSynchronously(variant, disabled);

        Installation installation = clientInstallationService.findEnabledInstallationForVariantByDeviceToken(variant.getVariantID(),
        		deviceToken);
        assertNull(installation);
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void testFindEnabledInstallationForVariantByDeviceToken() {
    	AndroidVariant variant = new AndroidVariant();
        variant.setGoogleKey("Key");
        variant.setName("NewVaraint");
        variant.setDeveloper("me");
        variantService.addVariant(variant);

        PushApplication application = new PushApplication();
        application.setName("NewApp");
        applicationService.addPushApplication(application);
        applicationService.addVariant(application, variant);

        Installation disabled = new Installation();
        String deviceToken = TestUtils.generateFakedDeviceTokenString();
        disabled.setDeviceToken(deviceToken);
        disabled.setEnabled(true);
        clientInstallationService.addInstallationSynchronously(variant, disabled);

        Installation installation = clientInstallationService.findEnabledInstallationForVariantByDeviceToken(variant.getVariantID(),
        		deviceToken);
        assertNotNull(installation);
    }

    private List<String> findAllDeviceTokenForVariantIDByCriteria(String variantID, List<String> categories, List<String> aliases, List<String> deviceTypes) {
        try {
            ResultsStream<String> tokenStream = clientInstallationService.findAllDeviceTokenForVariantIDByCriteria(variantID, categories, aliases, deviceTypes, Integer.MAX_VALUE, null).executeQuery();
            List<String> list = new ArrayList<String>();
            while (tokenStream.next()) {
                list.add(tokenStream.get());
            }
            return list;
        } catch (ResultStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> findAllOldGoogleCloudMessagingDeviceTokenForVariantIDByCriteria(String variantID, List<String> categories, List<String> aliases, List<String> deviceTypes) {
        try {
            ResultsStream<String> tokenStream = clientInstallationService.findAllOldGoogleCloudMessagingDeviceTokenForVariantIDByCriteria(variantID, categories, aliases, deviceTypes, Integer.MAX_VALUE, null).executeQuery();
            List<String> list = new ArrayList<String>();
            while (tokenStream.next()) {
                list.add(tokenStream.get());
            }
            return list;
        } catch (ResultStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    // simple util, borrowed from AG Crypto
    private byte[] toByteArray(InputStream file) throws IOException {
        int n;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];

        while (-1 != (n = file.read(buffer))) {
            bos.write(buffer, 0, n);
        }
        return bos.toByteArray();
    }

}
