package io.github.quickmsg;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.quickmsg.common.config.BootstrapConfig;
import io.github.quickmsg.common.utils.FileExtension;
import io.github.quickmsg.common.utils.IPUtils;
import io.github.quickmsg.common.utils.ServerUtils;
import io.github.quickmsg.core.Bootstrap;
import io.github.quickmsg.exception.NotSupportConfigException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

/**
 * @author luxurong
 */
@Slf4j
public abstract class AbstractStarter {




    public static void start(String path) {
        BootstrapConfig config = null;
        if (path != null) {
            if (path.endsWith(FileExtension.PROPERTIES_SYMBOL)) {
                ObjectMapper mapper = new ObjectMapper(new JavaPropsFactory());
                try {
                    config = mapper.readValue(new File(path), BootstrapConfig.class);
                } catch (Exception e) {
                    log.error("properties read error", e);
                }
            } else if (path.endsWith(FileExtension.YAML_SYMBOL_1) || path.endsWith(FileExtension.YAML_SYMBOL_2)) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                try {
                    config = mapper.readValue(new File(path), BootstrapConfig.class);
                } catch (Exception e) {
                    log.error("yaml read error", e);
                    return;
                }
            } else {
                throw new NotSupportConfigException();
            }
        }
        if (config == null) {
            config = BootstrapConfig.defaultConfig();
        }
        Bootstrap.builder()
                .rootLevel(Level.toLevel(config.getSmqttConfig().getLogLevel()))
                .tcpConfig(config.getSmqttConfig().getTcpConfig())
                .httpConfig(config.getSmqttConfig().getHttpConfig())
                .websocketConfig(config.getSmqttConfig().getWebsocketConfig())
                .clusterConfig(config.getSmqttConfig().getClusterConfig())
                .meterConfig(config.getSmqttConfig().getMeterConfig())
                .ruleChainDefinitions(config.getSmqttConfig().getRuleChainDefinitions())
                .sourceDefinitions(config.getSmqttConfig().getRuleSources())
                .authConfig(config.getSmqttConfig().getAuthConfig())
                .build()
                .doOnStarted(AbstractStarter::printUiUrl).startAwait();

    }

    /**
     * 打印前端访问地址
     *
     * @param bootstrap 启动类
     */
    public static void printUiUrl(Bootstrap bootstrap) {
        String start = "\n-------------------------------------------------------------\n\t";
        start += String.format("SMQTTX mqtt connect url %s:%s \n\t", ServerUtils.serverIp, bootstrap.getTcpConfig().getPort());
        if (bootstrap.getHttpConfig() != null) {
            Integer port = 60000;
            start += String.format("SMQTTX-Admin UI is running AccessURLs:\n\t" +
                    "Http Local url:    http://localhost:%s" + "\n\t" +
                    "Http External url: http://%s:%s" + "\n" +
                    "-------------------------------------------------------------", port,  ServerUtils.serverIp, port);
        }
        log.info(start);
    }
}
