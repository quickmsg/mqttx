package io.github.quickmsg.core.protocol;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import org.apache.commons.lang3.time.DateFormatUtils;

import io.github.quickmsg.common.auth.AuthManager;
import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.context.ReceiveContext;
import io.github.quickmsg.common.integrate.Integrate;
import io.github.quickmsg.common.integrate.SubscribeTopic;
import io.github.quickmsg.common.integrate.channel.IntegrateChannels;
import io.github.quickmsg.common.integrate.topic.IntegrateTopics;
import io.github.quickmsg.common.log.LogEvent;
import io.github.quickmsg.common.log.LogManager;
import io.github.quickmsg.common.log.LogStatus;
import io.github.quickmsg.common.message.mqtt.ConnectMessage;
import io.github.quickmsg.common.message.mqtt.DisConnectMessage;
import io.github.quickmsg.common.metric.CounterType;
import io.github.quickmsg.common.protocol.Protocol;
import io.github.quickmsg.common.utils.JacksonUtil;
import io.github.quickmsg.common.utils.MqttMessageUtils;
import io.github.quickmsg.core.mqtt.MqttReceiveContext;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.extern.slf4j.Slf4j;
import reactor.util.context.ContextView;

/**
 * @author luxurong
 */
@Slf4j
public class ConnectProtocol implements Protocol<ConnectMessage> {

    private static final int MILLI_SECOND_PERIOD = 1_000;


    @Override
    public void parseProtocol(ConnectMessage connectMessage, MqttChannel mqttChannel, ContextView contextView) {
        ReceiveContext<?> receiveContext = contextView.get(ReceiveContext.class);
        LogManager logManager = receiveContext.getLogManager();
        MqttReceiveContext mqttReceiveContext = (MqttReceiveContext) contextView.get(ReceiveContext.class);
        String clientIdentifier = mqttChannel.getClientId();
        Integrate integrate = mqttReceiveContext.getIntegrate();
        IntegrateChannels channels = integrate.getChannels();
        IntegrateTopics<SubscribeTopic> topics = integrate.getTopics();
        AuthManager authManager = mqttReceiveContext.getAuthManager();
        /*protocol version support*/
        if (MqttVersion.MQTT_3_1_1 != connectMessage.getVersion() && MqttVersion.MQTT_3_1 != connectMessage.getVersion() && MqttVersion.MQTT_5 != connectMessage.getVersion()) {
            mqttChannel.write(MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION));
            return;
        }
        authManager.auth(Optional.ofNullable(connectMessage.getAuth()).map(MqttChannel.Auth::getUsername).orElse(null), Optional.ofNullable(connectMessage.getAuth()).map(MqttChannel.Auth::getPassword).orElseGet(() -> new byte[]{}), clientIdentifier).subscribe(aBoolean -> {
            /*password check*/
            if (aBoolean) {
            /*check clientId, clientIdentifier)) {
            /*check clientIdentifier exist*/
                mqttChannel.setConnectCache(connectMessage.getCache(receiveContext.getIntegrate().getCluster().getLocalNode()));
                mqttChannel.getConnectCache().setWill(connectMessage.getWill());
                logManager.printInfo(mqttChannel, LogEvent.CONNECT, LogStatus.SUCCESS, JacksonUtil.bean2Json(connectMessage.getCache(receiveContext.getIntegrate().getCluster().getLocalNode())));

                mqttChannel.setAuthTime(DateFormatUtils.format(new Date(), "yyyy-mm-dd hh:mm:ss"));

                /*registry unread event close channel */
                mqttChannel.getConnection().onReadIdle((long) connectMessage.getKeepalive() * MILLI_SECOND_PERIOD << 1, () -> this.logHeartClose(logManager, mqttChannel));

                /* registry new channel*/
                channels.add(mqttChannel.getClientId(), mqttChannel);

                /* registry close mqtt channel event*/
                mqttChannel.registryClose(channel -> this.close(mqttChannel, mqttReceiveContext));

                mqttChannel.write(MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_ACCEPTED));

                receiveContext.getMetricManager().getMetricRegistry().getMetricCounter(CounterType.CONNECT).increment();
                receiveContext.getMetricManager().getMetricRegistry().getMetricCounter(CounterType.CONNECT_EVENT).increment();

            } else {
                logManager.printInfo(mqttChannel, LogEvent.CONNECT, LogStatus.FAILED, JacksonUtil.bean2Json(connectMessage.getCache(receiveContext.getIntegrate().getCluster().getLocalNode())));
                mqttChannel.write(MqttMessageUtils.buildConnectAck(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD));
            }
        });


    }

    private void logHeartClose(LogManager logManager, MqttChannel mqttChannel) {
        mqttChannel.close();
        logManager.printInfo(mqttChannel, LogEvent.HEART_TIMEOUT, LogStatus.SUCCESS, JacksonUtil.bean2Json(mqttChannel.getConnectCache()));
    }


    @Override
    public Class<ConnectMessage> getClassType() {
        return ConnectMessage.class;
    }


    private void close(MqttChannel mqttChannel, MqttReceiveContext mqttReceiveContext) {
        final MqttChannel.Will willMessage = mqttChannel.getConnectCache().getWill();
        mqttReceiveContext.getIntegrate().getChannels().remove(mqttChannel);
        IntegrateTopics<SubscribeTopic> topics = mqttReceiveContext.getIntegrate().getTopics();
        topics.removeTopic(mqttChannel, new ArrayList<>(mqttChannel.getTopics()));
        mqttReceiveContext.getRetryManager().clearRetry(mqttChannel);
        DisConnectMessage disConnectMessage = new DisConnectMessage(mqttChannel);
        mqttReceiveContext.getIntegrate().getProtocolAdaptor().chooseProtocol(disConnectMessage);
        Optional.ofNullable(willMessage).ifPresent(will ->
                Optional.ofNullable(topics.getMqttChannelsByTopic(will.getWillTopic()))
                        .ifPresent(subscribeTopics -> subscribeTopics.forEach(subscribeTopic -> {
                            MqttChannel channel = subscribeTopic.getMqttChannel();
                            MqttQoS mqttQoS = subscribeTopic.minQos(will.getMqttQoS());
                            channel.sendPublish(mqttQoS, will.toPublishMessage());
                        })));

    }


}
