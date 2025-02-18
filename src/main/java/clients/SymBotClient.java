package clients;

import authentication.ISymAuth;
import authentication.SymBotAuth;
import authentication.SymBotRSAAuth;
import clients.symphony.api.*;
import configuration.LoadBalancingMethod;
import configuration.SymConfig;
import configuration.SymConfigLoader;
import configuration.SymLoadBalancedConfig;
import exceptions.NoConfigException;
import exceptions.SymClientException;
import javax.ws.rs.client.Client;
import model.UserInfo;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.DatafeedEventsService;
import services.FirehoseService;
import utils.HttpClientBuilderHelper;
import utils.SymMessageParser;

public final class SymBotClient implements ISymClient {
    private static final Logger logger = LoggerFactory.getLogger(SymBotClient.class);
    private static SymBotClient botClient;
    private SymConfig config;
    private ISymAuth symBotAuth;
    private DatafeedClient datafeedClient;
    private MessagesClient messagesClient;
    private PresenceClient presenceClient;
    private StreamsClient streamsClient;
    private UsersClient usersClient;
    private ConnectionsClient connectionsClient;
    private DatafeedEventsService datafeedEventsService;
    private SignalsClient signalsClient;
    private UserInfo botUserInfo;
    private Client podClient;
    private Client agentClient;
    private AdminClient adminClient;
    private FirehoseClient firehoseClient;
    private FirehoseService firehoseService;
    private HealthcheckClient healthcheckClient;
    private InformationBarriersClient informationBarriersClient;

    public static SymBotClient initBotRsa(String configPath) throws NoConfigException {
        return initBotRsa(configPath, SymConfig.class);
    }

    public static <T extends SymConfig> SymBotClient initBotRsa(String configPath, Class<T> clazz) throws NoConfigException {
        return initBot(configPath, clazz, true);
    }

    public static SymBotClient initBot(String configPath) throws NoConfigException {
        return initBot(configPath, SymConfig.class);
    }

    public static <T extends SymConfig> SymBotClient initBot(String configPath, Class<T> clazz) throws NoConfigException {
        return initBot(configPath, clazz, false);
    }

    private static <T extends SymConfig> SymBotClient initBot(String configPath, Class<T> clazz, boolean isRsa)
        throws NoConfigException {
        if (botClient == null) {
            T config = SymConfigLoader.loadConfig(configPath, clazz);
            ISymAuth botAuth = isRsa ? new SymBotRSAAuth(config) : new SymBotAuth(config);
            botAuth.authenticate();
            botClient = new SymBotClient(config, botAuth);
        }

        return botClient;
    }

    public static SymBotClient initBot(SymConfig config, ISymAuth botAuth) {
        if (botClient == null) {
            botClient = new SymBotClient(config, botAuth);
            return botClient;
        }
        return botClient;
    }

    public static SymBotClient initBot(SymConfig config, ISymAuth botAuth, SymLoadBalancedConfig lbConfig) {
        if (botClient == null) {
            lbConfig.cloneAttributes(config);
            botClient = new SymBotClient(lbConfig, botAuth);
            return botClient;
        }
        return botClient;
    }

    public static SymBotClient initBot(SymConfig config,
                                       ISymAuth botAuth,
                                       ClientConfig podClientConfig,
                                       ClientConfig agentClientConfig) {
        if (botClient == null) {
            botClient = new SymBotClient(
                config, botAuth, podClientConfig, agentClientConfig
            );
            return botClient;
        }
        return botClient;
    }

    public static SymBotClient initBot(SymConfig config,
                                       ISymAuth botAuth,
                                       ClientConfig podClientConfig,
                                       ClientConfig agentClientConfig,
                                       SymLoadBalancedConfig lbConfig) {
        if (botClient == null) {
            lbConfig.cloneAttributes(config);
            botClient = new SymBotClient(config, botAuth, podClientConfig, agentClientConfig);
            return botClient;
        }
        return botClient;
    }

    public static SymBotClient initBotLoadBalancedRsa(String configPath, String lbConfigPath) throws NoConfigException {
        return initBotLoadBalancedRsa(configPath, lbConfigPath, SymConfig.class);
    }

    public static <T extends SymConfig> SymBotClient initBotLoadBalancedRsa(
        String configPath, String lbConfigPath, Class<T> clazz
    ) throws NoConfigException {
        return initBotLoadBalanced(configPath, lbConfigPath, clazz, true);
    }

    public static SymBotClient initBotLoadBalanced(String configPath, String lbConfigPath) throws NoConfigException {
        return initBotLoadBalanced(configPath, lbConfigPath, SymConfig.class);
    }

    public static <T extends SymConfig> SymBotClient initBotLoadBalanced(
        String configPath, String lbConfigPath, Class<T> clazz
    ) throws NoConfigException {
        return initBotLoadBalanced(configPath, lbConfigPath, clazz, false);
    }

    private static <T extends SymConfig> SymBotClient initBotLoadBalanced(
        String configPath, String lbConfigPath, Class<T> clazz, boolean isRsa
    ) throws NoConfigException {
        if (botClient == null) {
            T config = SymConfigLoader.loadConfig(configPath, clazz);
            SymLoadBalancedConfig lbConfig = SymConfigLoader.loadConfig(lbConfigPath, SymLoadBalancedConfig.class);
            ISymAuth botAuth = isRsa ? new SymBotRSAAuth(config) : new SymBotAuth(config);
            botAuth.authenticate();

            lbConfig.cloneAttributes(config);
            botClient = new SymBotClient(lbConfig, botAuth);
        }
        return botClient;
    }

    private SymBotClient(SymConfig config, ISymAuth symBotAuth) {
        this.config = config;
        this.symBotAuth = symBotAuth;

        ClientConfig podConfig = HttpClientBuilderHelper.getPodClientConfig(config);
        ClientConfig agentConfig = HttpClientBuilderHelper.getAgentClientConfig(config);

        this.podClient = HttpClientBuilderHelper.getHttpClientBuilderWithTruststore(config).withConfig(podConfig).build();
        this.agentClient = HttpClientBuilderHelper.getHttpClientBuilderWithTruststore(config).withConfig(agentConfig).build();

        try {
            botUserInfo = this.getUsersClient().getSessionUser();
        }  catch (SymClientException e) {
            logger.error("Error getting sessionUser ", e);
        }

        SymMessageParser.createInstance(this);

        reportIfLoadBalanced(config);
    }

    private SymBotClient(SymConfig config, ISymAuth symBotAuth, ClientConfig podClientConfig, ClientConfig agentClientConfig) {
        this.config = config;
        this.symBotAuth = symBotAuth;
        this.podClient = HttpClientBuilderHelper.getHttpClientBuilderWithTruststore(config)
            .withConfig(podClientConfig).build();
        this.agentClient = HttpClientBuilderHelper.getHttpClientBuilderWithTruststore(config)
            .withConfig(agentClientConfig).build();

        try {
            botUserInfo = this.getUsersClient().getSessionUser();
        } catch (SymClientException e) {
            logger.error("Error getting sessionUser ", e);
        }

        SymMessageParser.createInstance(this);

        reportIfLoadBalanced(config);
    }

    private void reportIfLoadBalanced(SymConfig config) {
        if (config instanceof SymLoadBalancedConfig) {
            SymLoadBalancedConfig lbConfig = (SymLoadBalancedConfig) config;
            LoadBalancingMethod method = lbConfig.getLoadBalancing().getMethod();
            logger.info("Using load-balanced configuration with method: {}", method);
            if (lbConfig.getLoadBalancing().getMethod() != LoadBalancingMethod.external) {
                logger.info("Agent server list: {}", String.join(", ", lbConfig.getAgentServers()));
            }
        }
    }

    public UserInfo getBotUserInfo() {
        return botUserInfo;
    }

    public DatafeedClient getDatafeedClient() {
        if (datafeedClient == null) {
            datafeedClient = new DatafeedClient(this);
        }
        return datafeedClient;
    }

    public FirehoseClient getFirehoseClient() {
        if (firehoseClient == null) {
            firehoseClient = new FirehoseClient(this);
        }
        return firehoseClient;
    }

    public FirehoseService getFirehoseService() {
        if (this.firehoseService == null) {
            this.firehoseService = new FirehoseService(this);
        }

        return this.firehoseService;
    }

    public SymConfig getConfig() {
        return config;
    }

    public <T extends SymConfig> T getConfig(Class<T> clazz) {
        return clazz.cast(config);
    }

    public ISymAuth getSymAuth() {
        return symBotAuth;
    }

    public MessagesClient getMessagesClient() {
        if (messagesClient == null) {
            SymMessageParser.createInstance(this);
            messagesClient = new MessagesClient(this);
        }
        return messagesClient;
    }

    public PresenceClient getPresenceClient() {
        if (presenceClient == null) {
            presenceClient = new PresenceClient(this);
        }
        return presenceClient;
    }

    public StreamsClient getStreamsClient() {
        if (streamsClient == null) {
            streamsClient = new StreamsClient(this);
        }
        return streamsClient;
    }

    public UsersClient getUsersClient() {
        if (usersClient == null) {
            usersClient = new UsersClient(this);
        }
        return usersClient;
    }

    public ConnectionsClient getConnectionsClient() {
        if (connectionsClient == null) {
            connectionsClient = new ConnectionsClient(this);
        }
        return connectionsClient;
    }

    public DatafeedEventsService getDatafeedEventsService() {
        if (datafeedEventsService == null) {
            datafeedEventsService = new DatafeedEventsService(this);
        }
        return datafeedEventsService;
    }

    public SignalsClient getSignalsClient() {
        if (signalsClient == null) {
            signalsClient = new SignalsClient(this);
        }
        return signalsClient;
    }

    public AdminClient getAdminClient() {
        if (adminClient == null) {
            adminClient = new AdminClient(this);
        }
        return adminClient;
    }

    public HealthcheckClient getHealthcheckClient() {
        if (healthcheckClient == null) {
            healthcheckClient = new HealthcheckClient(this);
        }
        return healthcheckClient;
    }

    public InformationBarriersClient getInformationBarriersClient() {
        if (informationBarriersClient == null) {
            informationBarriersClient = new InformationBarriersClient(this);
        }
        return informationBarriersClient;
    }

    public static void clearBotClient() {
        botClient = null;
    }

    public static SymBotClient getBotClient() {
        return botClient;
    }


    public Client getPodClient() {
        return podClient;
    }

    public Client getAgentClient() {
        return agentClient;
    }

    public void setPodClient(Client podClient) {
        this.podClient = podClient;
    }

    public void setAgentClient(Client agentClient) {
        this.agentClient = agentClient;
    }
}
