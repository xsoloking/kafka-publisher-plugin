package io.jenkins.plugins.kafkapublisher;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;

import static io.jenkins.plugins.kafkapublisher.KafkaFactory.sendMessage;


// cf example https://github.com/jenkinsci/hello-world-plugin
@SuppressFBWarnings({"WeakerAccess", "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"})
public class KafkaPublisherBuilder extends Builder implements SimpleBuildStep {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaPublisherBuilder.class);
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private final String kafkaName;

    private final String topicName;

    private final String key;
    private final String data;
    
    @DataBoundConstructor
    public KafkaPublisherBuilder(String kafkaName, String topicName, String key, String data) {
        this.kafkaName = kafkaName;
        this.topicName = topicName;
        this.key = key;
        this.data = data;
    }

    public String getKafkaName() {
        return kafkaName;
    }


    public String getTopicName() {
        return topicName;
    }

    public String getKey() {
        return key;
    }

    public String getData() {
        return data;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        listener.getLogger().println("Retrieving parameters");
        LOGGER.info("Retrieving parameters :");

        //noinspection unchecked
        Map<String, String> buildVariables = build.getBuildVariables();
        LOGGER.debug("BuildVariables : {}", buildVariables);

        Map<String, String> buildParameters = new HashMap<>(buildVariables);

        Cause.UserIdCause userIdCause = (Cause.UserIdCause) build.getCause(Cause.UserIdCause.class);
        if (userIdCause != null) {
            buildParameters.put("BUILD_USER_ID", userIdCause.getUserId());
            buildParameters.put("BUILD_USER_NAME", userIdCause.getUserName());
        }

        LOGGER.debug("Parameters retrieved : {}", buildParameters);

        EnvVars env = new EnvVars();
        try {
            env = build.getEnvironment(listener);
        } catch (Exception e) {
            // nothing to do ?
        }

        LOGGER.debug("Environmental variables : {}", env);

        return perform(buildParameters, env, listener);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) {
        listener.getLogger().println("Retrieving data");
        LOGGER.info("Retrieving data :");

        Map<String, String> buildParameters = new HashMap<>();
        // FIXME https://jenkins.io/doc/developer/plugin-development/pipeline-integration/

        EnvVars env = new EnvVars();

        LOGGER.debug("Data retrieved : {}", buildParameters);

        perform(buildParameters, env, listener);
    }

    private boolean perform(@Nonnull Map<String, String> buildParameters, @Nonnull EnvVars env,
                            @Nonnull TaskListener listener) {
        PrintStream console = listener.getLogger();

        try {
            console.println("Initialisation Kafka");
            // INIT Kafka
            KafkaConfig kafkaConfig = getDescriptor().getKafkaConfig(kafkaName);
            if (kafkaConfig == null) {
                throw new IllegalArgumentException("Unknown kafka config : " + kafkaName);
            }

            String topicName = kafkaConfig.getTopicName();

            if (getTopicName() != null && !getKafkaName().isEmpty()) {
                topicName = getTopicName();
            }


            String expandedData = env.expand(data);
            String message;

            message = Utils.getRawMessage(buildParameters, expandedData);
            LOGGER.info("Sending raw message:\n{}", message);
            console.println("Sending raw message:\n" + message);


            console.println("Sending message");

            String messageKey = key;
            if (messageKey == null || messageKey.isEmpty()) {
                messageKey = "Jenkins";
            }
            sendMessage(kafkaConfig.getBrokers(), topicName, messageKey, message);
        } catch (Exception e) {
            LOGGER.error("Error while sending to Kafka", e);
            console.println("Error while sending to Kafka : " + ExceptionUtils.getMessage(e));

            return false;
        }

        return true;
    }

    @Override
    public KafkaDescriptor getDescriptor() {
        return (KafkaDescriptor) super.getDescriptor();
    }

    @Extension
    @Symbol("kafkaPublisher")
    public static class KafkaDescriptor extends BuildStepDescriptor<Builder> {

        private Configs configs;

        public KafkaDescriptor() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Publish to Kafka";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) {
            this.configs = Configs.fromJSON(json);

            save();

            return true;
        }

        public Configs getConfigs() {
            return configs;
        }

        public void setConfigs(Configs configs) {
            this.configs = configs;
        }

        public KafkaConfig getKafkaConfig(String configName) {
            return configs.getKafkaConfigs()
                    .stream()
                    .filter(rc -> rc.getName().equals(configName))
                    .findFirst()
                    .orElse(null);
        }

        public ListBoxModel doFillKafkaNameItems() {
            ListBoxModel options = new ListBoxModel();
            configs.kafkaConfigs.forEach(rc -> options.add(rc.name));
            return options;
        }
    }

    public static final class Configs extends AbstractDescribableImpl<Configs> {

        private final List<KafkaConfig> kafkaConfigs;

        @DataBoundConstructor
        public Configs(List<KafkaConfig> kafkaConfigs) {
            this.kafkaConfigs = kafkaConfigs != null ? new ArrayList<>(kafkaConfigs) : Collections.emptyList();
        }

        @Override
        public ConfigsDescriptor getDescriptor() {
            return (ConfigsDescriptor) super.getDescriptor();
        }

        public List<KafkaConfig> getKafkaConfigs() {
            return Collections.unmodifiableList(kafkaConfigs);
        }

        static Configs fromJSON(JSONObject jsonObject) {
            if (!jsonObject.containsKey("configs")) {
                return null;
            }

            List<KafkaConfig> kafkaConfigs = new ArrayList<>();

            JSONObject configsJSON = jsonObject.getJSONObject("configs");

            JSONObject kafkaConfigsJSON = configsJSON.optJSONObject("kafkaConfigs");
            if (kafkaConfigsJSON != null) {
                kafkaConfigs.add(KafkaConfig.fromJSON(kafkaConfigsJSON));
            }

            JSONArray kafkaConfigsJSONArray = configsJSON.optJSONArray("kafkaConfigs");
            if (kafkaConfigsJSONArray != null) {
                for (int i = 0; i < kafkaConfigsJSONArray.size(); i++) {
                    kafkaConfigs.add(KafkaConfig.fromJSON(kafkaConfigsJSONArray.getJSONObject(i)));
                }
            }

            return new Configs(kafkaConfigs);
        }

        @Extension
        public static class ConfigsDescriptor extends Descriptor<Configs> {

        }
    }

    public static class KafkaConfig extends AbstractDescribableImpl<KafkaConfig> {

        private String name;
        private String brokers;
        private String topicName;
        private boolean enableInQueue;
        private boolean enableStarted;
        private boolean enableFailure;


        @DataBoundConstructor
        public KafkaConfig(String name, String brokers, String topicName, boolean enableInQueue,
                           boolean enableStarted, boolean enableFailure) {
            this.name = name;
            this.brokers = brokers;
            this.topicName = topicName;
            this.enableInQueue = enableInQueue;
            this.enableStarted = enableStarted;
            this.enableFailure = enableFailure;
        }

        public String getName() {
            return name;
        }


        public String getBrokers() {
            return brokers;
        }

        public String getTopicName() {
            return topicName;
        }

        public boolean isEnableInQueue() {
            return enableInQueue;
        }

        public boolean isEnableStarted() {
            return enableStarted;
        }

        public boolean isEnableFailure() {
            return enableFailure;
        }
        

        static KafkaConfig fromJSON(JSONObject jsonObject) {
            String name = jsonObject.getString("name");
            String brokers = jsonObject.getString("brokers");
            String topicName = jsonObject.getString("topicName");
            boolean enableInQueue = jsonObject.getBoolean("enableInQueue");
            boolean enableStarted = jsonObject.getBoolean("enableStarted");
            boolean enableFailure = jsonObject.getBoolean("enableFailure");

            return new KafkaConfig(name, brokers, topicName, enableInQueue, enableStarted, enableFailure);
        }

        @Override
        public KafkaConfigDescriptor getDescriptor() {
            return (KafkaConfigDescriptor) super.getDescriptor();
        }

        @Extension
        public static class KafkaConfigDescriptor extends Descriptor<KafkaConfig> {

//            public FormValidation doCheckPort(@QueryParameter String value) {
//                if (NumberUtils.isNumber(value)) {
//                    return FormValidation.ok();
//                } else {
//                    return FormValidation.error("Not a number");
//                }
//            }

//            @RequirePOST
//            public FormValidation doTestConnection(@QueryParameter("host") final String host,
//                                                   @QueryParameter("port") final String port,
//                                                   @QueryParameter("username") final String username,
//                                                   @QueryParameter("password") final String password,
//                                                   @QueryParameter("isSecure") final String isSecure,
//                                                   @QueryParameter("virtualHost") final String virtualHost) {
//                // https://jenkins.io/doc/developer/security/form-validation/
//                Jenkins.getActiveInstance().checkPermission(Jenkins.ADMINISTER); // Keep this deprecated method for compatibility with old jenkins
//
//                try {
//                    //TODO
//
//                } catch (IOException | TimeoutException | GeneralSecurityException e) {
//                    LOGGER.error("Connection error", e);
//                    return FormValidation.error("Client error : " + e.getMessage());
//                }
//            }
        }
    }

}
