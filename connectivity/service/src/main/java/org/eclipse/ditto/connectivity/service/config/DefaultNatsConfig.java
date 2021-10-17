package org.eclipse.ditto.connectivity.service.config;

import com.typesafe.config.Config;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import javax.annotation.concurrent.Immutable;
import java.util.List;

/**
 * This class is the default implementation of {@link NatsConfig}.
 */
@Immutable
public class DefaultNatsConfig implements NatsConfig {

    private static final String CONFIG_PATH = "nats";

    private final List<String> servers;

    private DefaultNatsConfig(final ScopedConfig config) {
        this.servers = config.getStringList(NatsConfigValue.SERVERS.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultNats} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultNatsConfig of(final Config config) {
        return new DefaultNatsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, MqttConfig.MqttConfigValue.values()));
    }

    @Override
    public List<String> servers() {
        return this.servers;
    }
}
