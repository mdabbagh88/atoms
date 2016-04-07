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
package org.jboss.aerogear.unifiedpush.message;

import org.jboss.aerogear.unifiedpush.api.PushMessageInformation;
import org.jboss.aerogear.unifiedpush.api.VariantMetricInformation;
import org.jboss.aerogear.unifiedpush.message.event.PushMessageCompletedEvent;
import org.jboss.aerogear.unifiedpush.message.event.VariantCompletedEvent;
import org.jboss.aerogear.unifiedpush.message.jms.AbstractJMSMessageConsumer;
import org.jboss.aerogear.unifiedpush.message.jms.Dequeue;
import org.jboss.aerogear.unifiedpush.service.metrics.PushMessageMetricsService;
import org.jboss.aerogear.unifiedpush.utils.AeroGearLogger;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.Queue;

/**
 * Receives metrics from {@link NotificationDispatcher} and updates the database.
 */
@Stateless
public class MetricsCollector extends AbstractJMSMessageConsumer {

    private final AeroGearLogger logger = AeroGearLogger.getInstance(MetricsCollector.class);

    @Inject
    private PushMessageMetricsService metricsService;

    @Resource(mappedName = "java:/queue/BatchLoadedQueue")
    private Queue batchLoadedQueue;

    @Resource(mappedName = "java:/queue/AllBatchesLoadedQueue")
    private Queue allBatchesLoaded;

    @Inject
    private Event<VariantCompletedEvent> variantCompleted;

    @Inject
    private Event<PushMessageCompletedEvent> pushMessageCompleted;

    /**
     * Receives variant metrics and update the push message information in a database.
     *
     * Counts number of loaded device token batches and detects when all batches were loaded and fully served - i.e. the variant was completed.
     * When a variant was completed, fires {@link VariantCompletedEvent} CDI event.
     *
     * Additionally when a variant was completed and there are no more variants to be completed for this variant,
     * the {@link PushMessageCompletedEvent} CDI event is fired.
     *
     * @param variantMetricInformation the variant metrics info object
     */
    public void collectMetrics(@Observes @Dequeue VariantMetricInformation variantMetricInformation) {
        PushMessageInformation pushMessageInformation = metricsService.getPushMessageInformation(variantMetricInformation.getPushMessageInformation().getId());
        metricsService.lock(pushMessageInformation);

        final String variantID = variantMetricInformation.getVariantID();

        pushMessageInformation.setTotalReceivers(pushMessageInformation.getTotalReceivers() + variantMetricInformation.getReceivers());

        // AGPUSH-1585:
        // using a combined key of variant ID and PushMessageInformation ID, to not limit different push requests on the queue just to the variant
        // TODO: improve name and/or implementation if this.
        final String variantPushMessageID = variantID + ":" + pushMessageInformation.getId();

        int loadedBatches = countLoadedBatches(variantPushMessageID);
        variantMetricInformation.setTotalBatches(variantMetricInformation.getTotalBatches() + loadedBatches);

        boolean updatedExisting = false;
        for (VariantMetricInformation existingMetric : pushMessageInformation.getVariantInformations()) {
            if (variantMetricInformation.getVariantID().equals(existingMetric.getVariantID())) {
                updatedExisting = true;
                updateExistingMetric(existingMetric, variantMetricInformation);
                variantMetricInformation = existingMetric;
                break;
            }
        }

        if (!updatedExisting) {
            pushMessageInformation.addVariantInformations(variantMetricInformation);
        }

        metricsService.updatePushMessageInformation(pushMessageInformation);

        if (areIntegersEqual(variantMetricInformation.getTotalBatches(), variantMetricInformation.getServedBatches())) {

            if (areAllBatchesLoaded(variantPushMessageID)) {
                pushMessageInformation.setServedVariants(pushMessageInformation.getServedVariants() + 1);
                logger.fine(String.format("All batches for variant %s were processed", variantMetricInformation.getVariantID()));
                variantCompleted.fire(new VariantCompletedEvent(pushMessageInformation.getId(), variantMetricInformation.getVariantID()));

                if (areIntegersEqual(pushMessageInformation.getServedVariants(), pushMessageInformation.getTotalVariants())) {
                    logger.fine(String.format("All batches for application %s were processed", pushMessageInformation.getId()));
                    pushMessageCompleted.fire(new PushMessageCompletedEvent(pushMessageInformation.getId()));
                }
            }
        }
    }

    private int countLoadedBatches(String variantID) {
        int loadedBatches = 0;
        while (receiveInTransactionNoWait(batchLoadedQueue, "variantID", variantID) != null) {
            loadedBatches += 1;
        }
        return loadedBatches;
    }

    private boolean areAllBatchesLoaded(String variantID) {
        return receiveInTransactionNoWait(allBatchesLoaded, "variantID", variantID) != null;
    }

    private void updateExistingMetric(VariantMetricInformation existing, VariantMetricInformation update) {
        existing.setReceivers(existing.getReceivers() + update.getReceivers());
        existing.setServedBatches(existing.getServedBatches() + update.getServedBatches());
        existing.setTotalBatches(existing.getTotalBatches() + update.getTotalBatches());
        if (existing.getDeliveryStatus() == null) {
            existing.setDeliveryStatus(update.getDeliveryStatus());
        }
        if (existing.getDeliveryStatus() == Boolean.TRUE && update.getDeliveryStatus() == Boolean.FALSE) {
            existing.setDeliveryStatus(Boolean.FALSE);
        }
        if (existing.getReason() == null && update.getReason() != null) {
            existing.setReason(update.getReason());
        }
    }

    private boolean areIntegersEqual(int i1, int i2) {
        return i1 == i2;
    }
}