package io.github.quickmsg.core.protocol;

import io.github.quickmsg.common.acl.AclAction;
import io.github.quickmsg.common.acl.AclManager;
import io.github.quickmsg.common.channel.MqttChannel;
import io.github.quickmsg.common.context.ReceiveContext;
import io.github.quickmsg.common.enums.ChannelStatus;
import io.github.quickmsg.common.event.Event;
import io.github.quickmsg.common.event.acceptor.PublishEvent;
import io.github.quickmsg.common.integrate.SubscribeTopic;
import io.github.quickmsg.common.integrate.msg.IntegrateMessages;
import io.github.quickmsg.common.integrate.topic.IntegrateTopics;
import io.github.quickmsg.common.message.RetainMessage;
import io.github.quickmsg.common.message.SessionMessage;
import io.github.quickmsg.common.message.mqtt.ClusterMessage;
import io.github.quickmsg.common.message.mqtt.PublishMessage;
import io.github.quickmsg.common.protocol.Protocol;
import io.github.quickmsg.common.utils.MqttMessageUtils;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.Set;

/**
 * @author luxurong
 */
@Slf4j
public class PublishProtocol implements Protocol<PublishMessage> {


    @Override
    public Mono<Event> parseProtocol(PublishMessage message, MqttChannel mqttChannel, ContextView contextView) {
        ReceiveContext<?> receiveContext = contextView.get(ReceiveContext.class);
        try {
            IntegrateTopics<SubscribeTopic> topics = receiveContext.getIntegrate().getTopics();
            IntegrateMessages messages = receiveContext.getIntegrate().getMessages();
            AclManager aclManager = receiveContext.getAclManager();
            Set<SubscribeTopic> mqttChannels = topics.getObjectsByTopic(message.getTopic());
            if (mqttChannel == null) {
                // cluster message
                return send(mqttChannels, message, messages, filterRetainMessage(message, messages))
                        .then(Mono.empty());
            }
            if (!aclManager.check(mqttChannel, message.getTopic(), AclAction.PUBLISH)) {
                log.warn("mqtt【{}】publish topic 【{}】 acl not authorized ", mqttChannel.getConnectMessage(), message.getTopic());
                return Mono.empty();
            }
            switch (MqttQoS.valueOf(message.getQos())) {
                case AT_MOST_ONCE:
                    return send(mqttChannels, message, messages, filterRetainMessage(message, messages))
                            .thenReturn(buildEvent(message));
                case EXACTLY_ONCE:
                case AT_LEAST_ONCE:
                    return send(mqttChannels, message, messages,
                            mqttChannel.write(MqttMessageUtils.buildPublishAck(message.getMessageId()))
                                    .then(filterRetainMessage(message, messages)))
                            .thenReturn(buildEvent(message));
                default:
                    return Mono.empty();
            }
        } catch (Exception e) {
            log.error("error ", e);
        } finally {
            if (mqttChannel != null && receiveContext.isCluster()) {
                receiveContext.getProtocolAdaptor().chooseProtocol(new ClusterMessage(message));
            }
        }
        return Mono.empty();
    }

    @Override
    public Class<PublishMessage> getClassType() {
        return PublishMessage.class;
    }

    private Event buildEvent(PublishMessage message) {
        return new PublishEvent(
                System.currentTimeMillis(),
                message.getClientId(),
                message.getTopic(),
                message.getQos(),
                message.isRetain(),
                new String(message.getBody()));
    }


    /**
     * 通用发送消息
     *
     * @param subscribeTopics {@link SubscribeTopic}
     * @param message         {@link PublishMessage}
     * @param messages        {@link IntegrateMessages}
     * @param other           {@link Mono}
     * @return Mono
     */
    private Mono<Void> send(Set<SubscribeTopic> subscribeTopics, PublishMessage message, IntegrateMessages messages, Mono<Void> other) {
        return Mono.fromRunnable(() ->
                subscribeTopics.stream()
                        .filter(subscribeTopic -> filterOfflineSession(subscribeTopic.getMqttChannel(), messages, message))
                        .forEach(subscribeTopic -> {
                                    subscribeTopic
                                            .getMqttChannel()
                                            .sendPublish(subscribeTopic.minQos(MqttQoS.valueOf(message.getQos())), message);
                                }
                        )).then(other);

    }


    /**
     * 过滤离线会话消息
     *
     * @param mqttChannel {@link MqttChannel}
     * @param messages    {@link IntegrateMessages}
     * @param message     {@link PublishMessage}
     * @return boolean
     */
    private boolean filterOfflineSession(MqttChannel mqttChannel, IntegrateMessages messages, PublishMessage message) {
        if (mqttChannel.getStatus() == ChannelStatus.ONLINE) {
            return true;
        } else {
            messages.saveSessionMessage(SessionMessage.of(mqttChannel.getConnectMessage().getClientId(), message));
            return false;
        }
    }


    /**
     * 过滤保留消息
     *
     * @param message  {@link PublishMessage}
     * @param messages {@link IntegrateMessages}
     * @return Mono
     */
    private Mono<Void> filterRetainMessage(PublishMessage message, IntegrateMessages messages) {
        return Mono.fromRunnable(() -> {
            if (message.isRetain()) {
                messages.saveRetainMessage(RetainMessage.of(message));
            }
        });
    }


}
