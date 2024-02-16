package io.jenkins.plugins.kafkapublisher;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import static io.jenkins.plugins.kafkapublisher.KafkaFactory.sendMessage;

@Extension
public class KafkaQueueListener extends QueueListener {

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        if (!wi.task.getName().startsWith("JJB_")) {
            return;
        }
        KafkaPublisherBuilder.KafkaDescriptor descriptor = (KafkaPublisherBuilder.KafkaDescriptor) Jenkins.get().getDescriptor(KafkaPublisherBuilder.class);
        for (KafkaPublisherBuilder.KafkaConfig config : descriptor.getConfigs().getKafkaConfigs()) {
            if (config.isEnableInQueue()) {
                sendMessage(config.getBrokers(), config.getTopicName(), "Jenkins", getMessage(wi));
            }
        }
    }


    public String getMessage(Queue.WaitingItem wi) {
        Map<String, Object> msg = new HashMap<>();
        for (ParametersAction pa : wi.getActions(ParametersAction.class)) {
            for (ParameterValue p : pa.getParameters()) {
                if (p.getName().endsWith("Id")) {
                    msg.put(p.getName(), p.getValue());
                }
            }
        }
        String jenkinsRootUrl = Jenkins.get().getRootUrl();
        long queueId = wi.getId();
        msg.put("jenkinsJobName", wi.task.getName());
        msg.put("jenkinsBuildStatus", "IN_QUEUE");
        msg.put("jenkinsBuildQueueId", queueId);
        msg.put("jenkinsBuildQueueUrl", jenkinsRootUrl + "queue/item/" + queueId + "/api/json?pretty=true");
        return JSONObject.fromObject(msg).toString();
    }
}
