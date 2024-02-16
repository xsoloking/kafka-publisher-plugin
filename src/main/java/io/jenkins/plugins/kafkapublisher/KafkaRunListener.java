package io.jenkins.plugins.kafkapublisher;

import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static io.jenkins.plugins.kafkapublisher.KafkaFactory.sendMessage;


@Extension
public class KafkaRunListener extends RunListener<Run<?, ?>> {


    @Override
    public void onStarted(Run<?, ?> r, TaskListener listener) {
        String jobName = r.getParent().getName();
        if (!jobName.startsWith("JJB_")) {
            return;
        }
        KafkaPublisherBuilder.KafkaDescriptor descriptor = (KafkaPublisherBuilder.KafkaDescriptor) Jenkins.get().getDescriptor(KafkaPublisherBuilder.class);
        for (KafkaPublisherBuilder.KafkaConfig config : descriptor.getConfigs().getKafkaConfigs()) {
            if (config.isEnableFailure()) {
                sendToKafka(r, "STARTED", config);
            }
        }
    }


    @Override
    public void onFinalized(Run<?, ?> r) {
        String jobName = r.getParent().getName();
        if (!jobName.startsWith("JJB_")) {
            return;
        }
        if (!r.getResult().equals(Result.FAILURE)) {
            return;
        }
        KafkaPublisherBuilder.KafkaDescriptor descriptor = (KafkaPublisherBuilder.KafkaDescriptor) Jenkins.get().getDescriptor(KafkaPublisherBuilder.class);
        for (KafkaPublisherBuilder.KafkaConfig config : descriptor.getConfigs().getKafkaConfigs()) {
            if (config.isEnableFailure()) {
                sendToKafka(r, r.getResult().toString(), config);
            }
        }
    }

    private static void sendToKafka(Run<?, ?> r, String status, KafkaPublisherBuilder.KafkaConfig config) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("jenkinsJobName", r.getParent().getName());
        msg.put("jenkinsBuildUrl", Jenkins.get().getRootUrl() + r.getUrl());
        msg.put("jenkinsBuildStatus", status);
        msg.put("jenkinsBuildId", r.getId());
        for (ParametersAction pa : r.getActions(ParametersAction.class)) {
            for (ParameterValue p : pa.getParameters()) {
                if (p.getName().endsWith("Id")) {
                    msg.put(p.getName(), p.getValue());
                }
            }
        }
        sendMessage(config.getBrokers(), config.getTopicName(), "Jenkins", JSONObject.fromObject(msg).toString());

    }
}
