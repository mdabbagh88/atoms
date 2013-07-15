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
package org.jboss.aerogear.connectivity.jpa.dao.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Query;

import org.jboss.aerogear.connectivity.jpa.AbstractGenericDao;
import org.jboss.aerogear.connectivity.jpa.dao.InstallationDao;
import org.jboss.aerogear.connectivity.model.AbstractVariant;
import org.jboss.aerogear.connectivity.model.InstallationImpl;

public class InstallationDaoImpl extends AbstractGenericDao<InstallationImpl, String> implements InstallationDao {

    @SuppressWarnings("unchecked")
    @Override
    public List<InstallationImpl> findInstallationsForVariantByDeviceToken(String variantID, String deviceToken) {

        return createQuery("select installation from " + AbstractVariant.class.getSimpleName() + 
                " abstractVariant join abstractVariant.installations installation" +
                " where abstractVariant.variantID = :variantID" + 
                " and installation.deviceToken = :deviceToken")
                .setParameter("variantID", variantID)
                .setParameter("deviceToken", deviceToken)
                .getResultList();
    }
    
    /**
     * Usage: SenderSerivce: 
     * 
     * A dynamic finder for all sorts of queries around selecting Device-Token, based on different criterias.
     * The method appends different criterias to the JPQL string, IF PRESENT.
     * 
     * Done in one method, instead of having similar, but error-thrown Strings, in different methods.
     * 
     * TODO: perhaps moving to Criteria API for this later
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<String> findAllDeviceTokenForVariantIDByCategoryAndAliasAndDeviceType(String variantID, String category, List<String> aliases, List<String> deviceTypes) {

        // the required part: Join + all tokens for variantID;
        StringBuilder jpqlString = new StringBuilder("select installation.deviceToken from ");
        jpqlString.append(AbstractVariant.class.getSimpleName())
        .append(" abstractVariant join abstractVariant.installations installation where abstractVariant.variantID = :variantID");

        // parameter names and values, stored in a map:
        final Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        
        // OPTIONAL query arguments, as provided.....
        
        // are aliases present ??
        if (aliases != null && ! aliases.isEmpty()) {
            // append the string:
            jpqlString.append(" and installation.alias IN :aliases");
            // add the params:
            parameters.put("aliases", aliases);
        }

        // are devices present ??
        if (deviceTypes != null && ! deviceTypes.isEmpty()) {
            // append the string:
            jpqlString.append(" and installation.deviceType IN :deviceTypes");
            // add the params:
            parameters.put("deviceTypes", deviceTypes);
        }
        
        // is a category present ?
        if (category != null) {
            // append the string:
            jpqlString.append(" and installation.category = :category");
            // add the params:
            parameters.put("category", category);
        }

        // the JPQL
        Query jpql = createQuery(jpqlString.toString());
        // add REQUIRED param:
        jpql.setParameter("variantID", variantID);

        // add the optionals, as needed:
        Set<String> paramKeys = parameters.keySet();
        for (String parameterName : paramKeys) {
            jpql.setParameter(parameterName, parameters.get(parameterName));
        }

        return jpql.getResultList();
    }
}